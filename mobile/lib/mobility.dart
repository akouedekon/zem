import 'dart:async';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'api.dart';

class MobilityPage extends StatefulWidget {
  final ZemApi api;
  final String role;
  const MobilityPage({super.key, required this.api, required this.role});
  @override
  State<MobilityPage> createState() => _MobilityPageState();
}

class _MobilityPageState extends State<MobilityPage>
    with WidgetsBindingObserver {
  final pickup = TextEditingController(), destination = TextEditingController();
  final lat = TextEditingController(),
      lon = TextEditingController(),
      destLat = TextEditingController(),
      destLon = TextEditingController();
  final revenue = TextEditingController(),
      count = TextEditingController(),
      expenses = TextEditingController();
  final form = GlobalKey<FormState>(), declarationForm = GlobalKey<FormState>();
  List<dynamic> rides = [], requests = [];
  Map<String, dynamic>? earnings, location;
  bool busy = false, online = false, foreground = true;
  String? message;
  Timer? timer;
  String get day => DateTime.now()
      .toUtc()
      .add(const Duration(hours: 1))
      .toIso8601String()
      .substring(0, 10);
  bool get driver => widget.role == 'DRIVER';
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    run(refresh);
    timer = Timer.periodic(const Duration(seconds: 30), (_) {
      if (foreground && !busy) {
        run(() async {
          if (driver && online) await share();
          await refresh();
        });
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    foreground = state == AppLifecycleState.resumed;
  }

  @override
  void dispose() {
    timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    for (final c in [
      pickup,
      destination,
      lat,
      lon,
      destLat,
      destLon,
      revenue,
      count,
      expenses,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> run(Future<void> Function() fn) async {
    if (busy || !mounted) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await fn();
    } catch (e) {
      message = e is ApiException
          ? e.message
          : e is Exception
          ? e.toString()
          : 'Connexion impossible';
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> refresh() async {
    final list = await widget.api.request(
      '${driver ? 'driver' : 'passenger'}/rides',
    );
    if (!mounted) return;
    rides = list;
    if (driver) {
      final service = await widget.api.request('driver/service');
      online = service['online'] == true;
      earnings = Map<String, dynamic>.from(
        await widget.api.request('driver/earnings?day=$day'),
      );
      requests = online ? await widget.api.request('driver/requests') : [];
    } else {
      final active = rides.where(
        (r) => ['ACCEPTED', 'IN_PROGRESS'].contains(r['status']),
      );
      location = null;
      if (active.isNotEmpty) {
        try {
          location = Map<String, dynamic>.from(
            await widget.api.request('rides/${active.first['id']}/location'),
          );
        } on ApiException catch (e) {
          if (e.status != 404) rethrow;
        }
      }
    }
  }

  Future<Position> position() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      throw Exception('Activez la localisation du téléphone');
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      throw Exception('Autorisez la position pendant l’utilisation de ZEM');
    }
    return Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        timeLimit: Duration(seconds: 15),
      ),
    );
  }

  Future<void> share() async {
    final p = await position();
    if (!mounted || !foreground) return;
    await widget.api.request(
      'driver/service',
      method: 'PUT',
      body: {'online': true, 'latitude': p.latitude, 'longitude': p.longitude},
    );
  }

  Future<void> mutate(
    String path, {
    String method = 'POST',
    Map<String, dynamic>? body,
  }) async {
    await widget.api.request(path, method: method, body: body);
    await refresh();
  }

  String money(dynamic n) => n == null ? 'Non renseigné' : '$n FCFA';
  Widget panel(String title, List<Widget> children) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    ),
  );
  Widget field(
    String label,
    TextEditingController c, {
    bool number = false,
    bool optional = false,
    double? bound,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextFormField(
      controller: c,
      enabled: !busy,
      decoration: InputDecoration(labelText: label),
      keyboardType: number
          ? const TextInputType.numberWithOptions(decimal: true, signed: true)
          : TextInputType.text,
      maxLength: number ? 18 : 160,
      validator: (s) {
        if (s == null || s.isEmpty) return optional ? null : 'Champ requis';
        if (number) {
          final n = double.tryParse(s);
          if (n == null || !n.isFinite || (bound != null && n.abs() > bound)) {
            return 'Nombre invalide';
          }
        }
        return null;
      },
    ),
  );
  Future<void> finish(Map<String, dynamic> r) async {
    var amount = '${r['estimated_fare']}';
    final value = await showDialog<int>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Recette déclarée en FCFA'),
        content: TextFormField(
          initialValue: amount,
          onChanged: (v) => amount = v,
          keyboardType: TextInputType.number,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () {
              final n = int.tryParse(amount);
              if (n != null && n >= 0 && n <= 1000000) {
                Navigator.pop(context, n);
              }
            },
            child: const Text('Terminer'),
          ),
        ],
      ),
    );
    if (value != null) {
      await run(
        () =>
            mutate('driver/rides/${r['id']}/complete', body: {'amount': value}),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(driver ? 'Courses et revenus' : 'Mon zem'),
      actions: [
        IconButton(
          onPressed: busy ? null : () => run(refresh),
          tooltip: 'Actualiser',
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (busy) const LinearProgressIndicator(),
        if (message != null)
          Text(message!, style: const TextStyle(color: Colors.red)),
        if (driver)
          panel(online ? 'En service' : 'Hors service', [
            const Text(
              'Position actualisée uniquement quand cet écran est visible. Le passager de votre course peut la consulter. Quitter l’écran arrête les mises à jour ; la position devient indisponible après 2 minutes.',
            ),
            FilledButton(
              onPressed: busy
                  ? null
                  : () => run(() async {
                      if (online) {
                        await mutate(
                          'driver/service',
                          method: 'PUT',
                          body: {'online': false},
                        );
                      } else {
                        await share();
                        await refresh();
                      }
                    }),
              child: Text(
                online
                    ? 'Hors service · effacer ma position'
                    : 'Passer en service avec GPS',
              ),
            ),
          ]),
        if (!driver &&
            !rides.any(
              (r) => [
                'REQUESTED',
                'ACCEPTED',
                'IN_PROGRESS',
              ].contains(r['status']),
            ))
          panel('Commander une course', [
            Form(
              key: form,
              child: Column(
                children: [
                  field('Lieu de départ', pickup),
                  OutlinedButton(
                    onPressed: busy
                        ? null
                        : () => run(() async {
                            final p = await position();
                            lat.text = '${p.latitude}';
                            lon.text = '${p.longitude}';
                          }),
                    child: const Text('Utiliser mon GPS pour le départ'),
                  ),
                  field('Latitude de départ', lat, number: true, bound: 90),
                  field('Longitude de départ', lon, number: true, bound: 180),
                  field('Destination', destination),
                  field(
                    'Latitude de destination',
                    destLat,
                    number: true,
                    bound: 90,
                  ),
                  field(
                    'Longitude de destination',
                    destLon,
                    number: true,
                    bound: 180,
                  ),
                  const Text(
                    'Pilote : coordonnées de destination. Estimation 500 FCFA + 200/km à vol d’oiseau, arrondie à 100. Aucun débit.',
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () {
                            if (form.currentState!.validate()) {
                              run(
                                () => mutate(
                                  'passenger/rides',
                                  body: {
                                    'pickup': pickup.text,
                                    'destination': destination.text,
                                    'pickupLat': double.parse(lat.text),
                                    'pickupLon': double.parse(lon.text),
                                    'destinationLat': double.parse(
                                      destLat.text,
                                    ),
                                    'destinationLon': double.parse(
                                      destLon.text,
                                    ),
                                  },
                                ),
                              );
                            }
                          },
                    child: const Text('Demander une course'),
                  ),
                ],
              ),
            ),
          ]),
        if (location != null)
          panel('Position récente du conducteur', [
            Text('${location!['latitude']}, ${location!['longitude']}'),
            Text('Mise à jour : ${location!['updated_at']}'),
          ]),
        for (final r in requests)
          panel('${r['pickup']} → ${r['destination']}', [
            Text('Estimation : ${money(r['estimated_fare'])}'),
            FilledButton(
              onPressed: busy
                  ? null
                  : () => run(() => mutate('driver/rides/${r['id']}/accept')),
              child: const Text('Accepter'),
            ),
          ]),
        for (final r in rides)
          panel('${r['pickup']} → ${r['destination']}', [
            Text(
              '${r['status']} · ${money(r['declared_fare'] ?? r['estimated_fare'])}',
            ),
            if (['REQUESTED', 'ACCEPTED'].contains(r['status']))
              OutlinedButton(
                onPressed: busy
                    ? null
                    : () => run(() => mutate('rides/${r['id']}/cancel')),
                child: const Text('Annuler la course'),
              ),
            if (driver && r['status'] == 'ACCEPTED')
              FilledButton(
                onPressed: busy
                    ? null
                    : () => run(() => mutate('driver/rides/${r['id']}/start')),
                child: const Text('Démarrer'),
              ),
            if (driver && r['status'] == 'IN_PROGRESS')
              FilledButton(
                onPressed: busy
                    ? null
                    : () => finish(Map<String, dynamic>.from(r)),
                child: const Text('Terminer et déclarer la recette'),
              ),
          ]),
        if (earnings != null)
          panel('Revenus du $day', [
            Text(
              'Courses enregistrées : ${money(earnings!['recorded_revenue'])}',
            ),
            Text(
              'Hors ZEM déclaré : ${money(earnings!['offPlatformRevenue'])}',
            ),
            Text('Charges : ${money(earnings!['expenses'])}'),
            Text(
              'Solde estimé : ${earnings!['estimatedBalance'] == null ? 'Non calculable' : money(earnings!['estimatedBalance'])}',
            ),
            const Text('Montants déclaratifs ; encaissement non vérifié.'),
            Form(
              key: declarationForm,
              child: Column(
                children: [
                  field('Recettes hors ZEM', revenue, number: true),
                  field('Nombre de courses hors ZEM', count, number: true),
                  field(
                    'Charges · vide si inconnues',
                    expenses,
                    number: true,
                    optional: true,
                  ),
                  const Text(
                    'Ne recomptez pas les courses enregistrées dans ZEM. Enregistrer remplace la déclaration du jour.',
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () {
                            if (declarationForm.currentState!.validate()) {
                              final n = int.tryParse(revenue.text),
                                  q = int.tryParse(count.text),
                                  x = expenses.text.isEmpty
                                      ? null
                                      : int.tryParse(expenses.text);
                              if (n == null ||
                                  q == null ||
                                  (expenses.text.isNotEmpty && x == null)) {
                                setState(
                                  () =>
                                      message = 'Utilisez des montants entiers',
                                );
                                return;
                              }
                              run(
                                () => mutate(
                                  'driver/declarations',
                                  method: 'PUT',
                                  body: {
                                    'day': day,
                                    'offPlatformRevenue': n,
                                    'offPlatformRides': q,
                                    'expenses': x,
                                  },
                                ),
                              );
                            }
                          },
                    child: const Text('Enregistrer la déclaration'),
                  ),
                ],
              ),
            ),
          ]),
      ],
    ),
  );
}
