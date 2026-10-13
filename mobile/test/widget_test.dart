import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:zem_mobile/api.dart';
import 'package:zem_mobile/main.dart';

class MemoryStore implements SessionStore {
  String? token;
  @override
  Future<String?> read() async => token;
  @override
  Future<void> write(String t) async { token = t; }
  @override
  Future<void> clear() async { token = null; }
}
void main() {
  testWidgets('shows real login and pilot notice without a session', (tester) async {
    await tester.pumpWidget(ZemApp(api: ZemApi(store: MemoryStore(), client: MockClient((_) async => http.Response('{}', 500)))));
    await tester.pumpAndSettle();
    expect(find.text('Adresse e-mail'), findsOneWidget);
    expect(find.text('Créer un compte'), findsOneWidget);
    expect(find.textContaining('Aucune assurance'), findsOneWidget);
  });
  test('login stores token securely and logout revokes before clearing', () async {
    final store = MemoryStore();
    final api = ZemApi(store: store, client: MockClient((req) async {
      if (req.url.path.endsWith('/login')) return http.Response(jsonEncode({'token': 'opaque', 'user': {'role': 'DRIVER'}}), 200);
      expect(req.headers['Authorization'], 'Bearer opaque');
      expect(req.url.path.endsWith('/logout'), isTrue);
      return http.Response('', 204);
    }));
    await api.login('test@zem.test', 'Long-password-17'); expect(store.token, 'opaque');
    await api.logout(); expect(store.token, isNull); api.close();
  });
  test('expired sessions are erased and require login', () async {
    final store = MemoryStore()..token = 'expired';
    final api = ZemApi(store: store, client: MockClient((_) async => http.Response('{}', 401)));
    expect(await api.restore(), isNull); expect(store.token, isNull); api.close();
  });
  test('transient logout failure preserves session for retry', () async {
    final store = MemoryStore()..token = 'live';
    final api = ZemApi(store: store, client: MockClient((req) async => req.url.path.endsWith('/me') ? http.Response('{"role":"DRIVER"}', 200) : http.Response('{}', 503)));
    await api.restore();
    await expectLater(api.logout(), throwsA(isA<ApiException>()));
    expect(store.token, 'live'); api.close();
  });
}
