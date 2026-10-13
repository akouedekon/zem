import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:zem_mobile/api.dart';
import 'package:zem_mobile/mobility.dart';

void main() {
  testWidgets('passenger requests a real API ride and sees its pending state', (
    tester,
  ) async {
    var created = false;
    final api = ZemApi(
      client: MockClient((req) async {
        if (req.method == 'POST') {
          expect(req.url.path, '/api/passenger/rides');
          final body = jsonDecode(req.body);
          expect(body['pickupLat'], 6.36);
          expect(body['destination'], 'Dantokpa');
          created = true;
          return http.Response('{}', 201);
        }
        return http.Response(
          created
              ? '[{"id":"demo","pickup":"Étoile Rouge","destination":"Dantokpa","status":"REQUESTED","estimated_fare":1200,"declared_fare":null}]'
              : '[]',
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: MobilityPage(api: api, role: 'PASSENGER'),
      ),
    );
    await tester.pumpAndSettle();
    final landmark = find.widgetWithText(ActionChip, 'Marché Dantokpa');
    await tester.ensureVisible(landmark); await tester.tap(landmark); await tester.pumpAndSettle();
    final destinationInput = find.widgetWithText(TextFormField, 'Destination');
    await tester.ensureVisible(destinationInput); await tester.enterText(destinationInput, 'Autre destination');
    expect(tester.widget<TextFormField>(find.widgetWithText(TextFormField, 'Latitude de destination')).controller!.text, isEmpty);
    for (final entry in {
      'Lieu de départ': 'Étoile Rouge',
      'Latitude de départ': '6.36',
      'Longitude de départ': '2.40',
      'Destination': 'Dantokpa',
      'Latitude de destination': '6.37',
      'Longitude de destination': '2.43',
    }.entries) {
      final input = find.widgetWithText(TextFormField, entry.key);
      await tester.ensureVisible(input);
      await tester.enterText(input, entry.value);
    }
    final button = find.text('Demander une course');
    tester.testTextInput.hide();
    await tester.pumpAndSettle();
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(created, true);
    expect(find.textContaining('REQUESTED'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    api.close();
  });
}
