import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'api.dart';
import 'mobility.dart';

void main() => runApp(ZemApp(api: ZemApi()));
class ZemApp extends StatelessWidget {
  final ZemApi api;
  const ZemApp({super.key, required this.api});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'ZEM 2.0', debugShowCheckedModeBanner: false,
    theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff086b4b)), useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xfff3f6f4),
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xfff3f6f4), centerTitle: false),
      cardTheme: CardThemeData(elevation: 0, color: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: const BorderSide(color: Color(0xffdde7e1)))),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)))),
      inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: const Color(0xfff9fbfa), contentPadding: const EdgeInsets.all(16), border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)))),
    home: DriverHome(api: api));
}
class DriverHome extends StatefulWidget {
  final ZemApi api;
  const DriverHome({super.key, required this.api});
  @override
  State<DriverHome> createState() => _DriverHomeState();
}
class _DriverHomeState extends State<DriverHome> {
  final email = TextEditingController(), password = TextEditingController();
  final name = TextEditingController(), phone = TextEditingController(), plate = TextEditingController();
  final authForm = GlobalKey<FormState>(), profileForm = GlobalKey<FormState>();
  Map<String, dynamic>? user, dossier, card;
  List<dynamic> docs = [];
  bool busy = true, register = false;
  bool passenger = false;
  String? message;
  static const kinds = {'IDENTITY': 'Pièce d’identité', 'DRIVING_LICENSE': 'Permis de conduire', 'VEHICLE': 'Justificatif du véhicule'};
  static const statuses = {'DRAFT': 'Brouillon', 'SUBMITTED': 'En cours de revue', 'APPROVED': 'Validé', 'REJECTED': 'À corriger'};
  bool get editable => dossier == null || ['DRAFT', 'REJECTED'].contains(dossier!['status']);
  @override
  void initState() { super.initState(); _action(() async { user = await widget.api.restore(); if (user != null && user!['role'] == 'DRIVER') await _refresh(); }); }
  @override
  void dispose() { for (final c in [email, password, name, phone, plate]) { c.dispose(); } widget.api.close(); super.dispose(); }
  Future<void> _action(Future<void> Function() fn) async {
    if (mounted) setState(() { busy = true; message = null; });
    try { await fn(); }
    on ApiException catch (e) { message = e.message; if (e.status == 401) { user = null; dossier = null; card = null; docs = []; } }
    catch (_) { message = 'Connexion impossible. Vérifiez votre connexion et réessayez.'; }
    finally { if (mounted) setState(() { busy = false; }); }
  }
  Future<void> _refresh() async {
    try {
      dossier = Map<String, dynamic>.from(await widget.api.request('driver/dossier'));
      name.text = dossier!['fullName']; phone.text = dossier!['phone']; plate.text = dossier!['plate'];
      docs = await widget.api.request('driver/documents');
      try { card = Map<String, dynamic>.from(await widget.api.request('driver/card')); }
      on ApiException catch (e) { if (e.status != 404) rethrow; card = null; }
    } on ApiException catch (e) { if (e.status != 404) rethrow; dossier = null; docs = []; card = null; }
  }
  Future<void> _pick(String kind) => _action(() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['pdf', 'jpg', 'jpeg', 'png'], withData: false);
    if (result == null) return;
    final chosen = result.files.single;
    if (chosen.size > 4 * 1024 * 1024 || chosen.path == null) throw ApiException(400, 'Document supérieur à 4 Mo ou inaccessible');
    final file = File(chosen.path!);
    if (await file.length() > 4 * 1024 * 1024) throw ApiException(400, 'Document supérieur à 4 Mo');
    await widget.api.upload(kind, chosen.name, await file.readAsBytes()); await _refresh(); message = 'Justificatif enregistré';
  });
  String? required(String? value) => value == null || value.trim().isEmpty ? 'Champ requis' : null;
  Widget field(String label, TextEditingController c, {bool secret = false, bool enabled = true, TextInputType? keyboard, String? Function(String?)? validator, int? maxLength}) => Padding(
    padding: const EdgeInsets.only(bottom: 16), child: TextFormField(controller: c, enabled: enabled && !busy, obscureText: secret, keyboardType: keyboard,
      autocorrect: !secret, enableSuggestions: !secret, maxLength: maxLength,
      decoration: InputDecoration(labelText: label, counterText: ''), validator: validator ?? required));
  Widget panel(String title, List<Widget> children) => Card(margin: const EdgeInsets.only(bottom: 20), child: Padding(padding: const EdgeInsets.all(20),
    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [Text(title, style: Theme.of(context).textTheme.titleLarge), const SizedBox(height: 20), ...children])));
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('ZEM 2.0'), actions: [if (user != null && user!['role'] == 'DRIVER') IconButton(tooltip: 'Actualiser', onPressed: busy ? null : () => _action(_refresh), icon: const Icon(Icons.refresh)),
      if (user != null) IconButton(tooltip: 'Se déconnecter', onPressed: busy ? null : () => _action(() async { await widget.api.logout(); user = null; dossier = null; card = null; docs = []; password.clear(); }), icon: const Icon(Icons.logout))]),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(20), children: [
      Text(user?['role'] == 'PASSENGER' ? 'Votre espace passager' : 'Votre espace conducteur', style: Theme.of(context).textTheme.headlineMedium), const SizedBox(height: 12),
      const Text('Version pilote. Aucune assurance ni paiement. Utilisez des données de démonstration.'), const SizedBox(height: 20),
      if (busy) const LinearProgressIndicator(),
      if (message != null) Padding(padding: const EdgeInsets.symmetric(vertical: 16), child: Text(message!, style: const TextStyle(fontWeight: FontWeight.bold))),
      if (user == null) panel(register ? passenger ? 'Créer un compte passager' : 'Créer un compte conducteur' : 'Se connecter', [
        if (register) SwitchListTile(title: const Text('Compte passager'), subtitle: const Text('Désactivé : compte conducteur'), value: passenger, onChanged: busy ? null : (v) => setState(() => passenger = v)),
        Form(key: authForm, child: Column(children: [
          field('Adresse e-mail', email, keyboard: TextInputType.emailAddress, maxLength: 254,
            validator: (v) => v == null || !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(v.trim()) ? 'Adresse e-mail invalide' : null),
          field('Mot de passe', password, secret: true, maxLength: 72, validator: (v) => v == null || v.isEmpty || (register && v.length < 12) ? 'Mot de passe requis (12 caractères à l’inscription)' : null),
          FilledButton(onPressed: busy ? null : () { if (authForm.currentState!.validate()) { _action(() async {
            user = await widget.api.login(email.text, password.text, register: register, passenger: passenger); password.clear();
            if (user!['role'] == 'DRIVER') await _refresh();
          }); } }, child: Text(register ? 'Créer le compte' : 'Se connecter')),
          TextButton(onPressed: busy ? null : () => setState(() { register = !register; message = null; }), child: Text(register ? 'J’ai déjà un compte' : 'Créer un compte')),
        ]))])
      else if (user!['role'] == 'PASSENGER') panel('Mon espace passager', [FilledButton(onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => MobilityPage(api: widget.api, role: 'PASSENGER'))), child: const Text('Commander et suivre une course'))])
      else if (user!['role'] != 'DRIVER') panel('Compte administratif', [const Text('La revue administrative est disponible dans l’interface web ZEM.')])
      else ...[
        panel('Mon activité', [FilledButton(onPressed: busy ? null : () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => MobilityPage(api: widget.api, role: 'DRIVER'))), child: const Text('Courses, service et revenus'))]),
        panel('Mon dossier · ${statuses[dossier?['status']] ?? 'Brouillon'}', [
          if (dossier?['rejectionReason'] != null) Padding(padding: const EdgeInsets.only(bottom: 16), child: Text('Motif : ${dossier!['rejectionReason']}')),
          Form(key: profileForm, child: Column(children: [
            field('Nom complet', name, enabled: editable, maxLength: 120),
            field('Téléphone', phone, enabled: editable, keyboard: TextInputType.phone, maxLength: 24,
              validator: (v) => v == null || !RegExp(r'^\+?[0-9 ]{8,24}$').hasMatch(v) ? 'Téléphone invalide' : null),
            field('Immatriculation', plate, enabled: editable, maxLength: 24),
            FilledButton(onPressed: busy || !editable ? null : () { if (profileForm.currentState!.validate()) { _action(() async {
              await widget.api.request('driver/dossier', method: 'PUT', body: {'fullName': name.text, 'phone': phone.text, 'plate': plate.text});
              await _refresh(); message = 'Dossier enregistré';
            }); } }, child: const Text('Enregistrer le dossier')),
          ])), if (!editable) const Padding(padding: EdgeInsets.only(top: 12), child: Text('Dossier figé pendant la revue et après validation.')),
        ]),
        panel('Mes justificatifs', [const Text('PDF, JPEG ou PNG. 4 Mo maximum. Stockage privé.'), const SizedBox(height: 12),
          for (final entry in kinds.entries) ListTile(contentPadding: EdgeInsets.zero, title: Text(entry.value),
            subtitle: Text(docs.any((d) => d['kind'] == entry.key) ? 'Document enregistré' : 'À ajouter'),
            trailing: IconButton(tooltip: 'Ajouter ou remplacer ${entry.value}', onPressed: busy || !editable || dossier == null ? null : () => _pick(entry.key), icon: const Icon(Icons.upload_file))),
          const SizedBox(height: 12), FilledButton(onPressed: busy || !editable || docs.length != 3 ? null : () => _action(() async {
            await widget.api.request('driver/dossier/submit', method: 'POST'); await _refresh(); message = 'Dossier transmis à l’administration';
          }), child: const Text('Soumettre à la revue')),
        ]),
        if (card != null) panel('Ma carte numérique', [Text(dossier!['fullName'], style: Theme.of(context).textTheme.titleLarge), Text(dossier!['plate']),
          Text(card!['revokedAt'] != null ? 'Carte révoquée' : 'Échéance : ${card!['expiresAt'].toString().substring(0, 10)}'),
          Center(child: QrImageView(data: card!['verificationUrl'], size: 240, backgroundColor: Colors.white)),
          const Text('Ce QR permet une vérification en ligne sans divulguer vos justificatifs.'),
          OutlinedButton(onPressed: busy ? null : () => _action(() async {
            final result = await widget.api.request('public/cards/${card!['publicToken']}');
            message = result['valid'] == true ? 'Carte valide' : 'Carte non valide : ${result['status']}';
          }), child: const Text('Vérifier la validité')),
        ]),
      ],
    ])));
}
