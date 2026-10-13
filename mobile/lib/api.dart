import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

abstract class SessionStore {
  Future<String?> read();
  Future<void> write(String token);
  Future<void> clear();
}
class SecureSessionStore implements SessionStore {
  final FlutterSecureStorage storage = const FlutterSecureStorage();
  @override
  Future<String?> read() => storage.read(key: 'zem_session');
  @override
  Future<void> write(String token) => storage.write(key: 'zem_session', value: token);
  @override
  Future<void> clear() => storage.delete(key: 'zem_session');
}
class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}
class ZemApi {
  final http.Client client;
  final SessionStore store;
  final String baseUrl;
  String? _token;
  ZemApi({http.Client? client, SessionStore? store,
    this.baseUrl = const String.fromEnvironment('API_BASE_URL', defaultValue: 'https://zem.example/api')})
    : client = client ?? http.Client(), store = store ?? SecureSessionStore() {
    final uri = Uri.parse(baseUrl);
    if (uri.scheme != 'https' && !(kDebugMode && uri.scheme == 'http')) {
      throw ArgumentError('Une URL HTTPS est requise');
    }
  }
  Future<Map<String, dynamic>?> restore() async {
    _token = await store.read();
    if (_token == null) return null;
    try { return Map<String, dynamic>.from(await request('auth/me')); }
    on ApiException catch (e) { if (e.status == 401) return null; rethrow; }
  }
  Future<Map<String, dynamic>> login(String email, String password, {bool register = false, bool passenger = false}) async {
    final body = {'email': email.trim(), 'password': password};
    if (register) await request(passenger ? 'auth/register-passenger' : 'auth/register', method: 'POST', body: body);
    final data = await request('auth/login', method: 'POST', body: body);
    _token = data['token']; await store.write(_token!);
    return Map<String, dynamic>.from(data['user']);
  }
  Future<void> logout() async {
    // Preserve the local session on transient failure so revocation can be retried.
    try { await request('auth/logout', method: 'POST'); }
    on ApiException catch (e) { if (e.status != 401) rethrow; }
    _token = null; await store.clear();
  }
  Uri url(String path) => Uri.parse('${baseUrl.replaceAll(RegExp(r'/+$'), '')}/$path');
  Map<String, String> get headers => {if (_token != null) 'Authorization': 'Bearer $_token'};
  Future<dynamic> request(String path, {String method = 'GET', Map<String, dynamic>? body}) async {
    final req = http.Request(method, url(path))..headers.addAll(headers);
    if (body != null) { req.headers['Content-Type'] = 'application/json'; req.body = jsonEncode(body); }
    final response = await http.Response.fromStream(await client.send(req).timeout(const Duration(seconds: 30)));
    return _decode(response);
  }
  Future<dynamic> upload(String kind, String filename, Uint8List bytes) async {
    if (bytes.isEmpty || bytes.length > 3 * 1024 * 1024) throw ApiException(400, 'Fichier vide ou supérieur à 3 Mo');
    final req = http.MultipartRequest('POST', url('driver/documents/$kind'))..headers.addAll(headers)
      ..files.add(http.MultipartFile.fromBytes('file', bytes, filename: filename));
    return _decode(await http.Response.fromStream(await client.send(req).timeout(const Duration(seconds: 30))));
  }
  Future<dynamic> _decode(http.Response response) async {
    dynamic data;
    try { data = response.body.isEmpty ? null : jsonDecode(utf8.decode(response.bodyBytes)); }
    catch (_) { data = null; }
    if (response.statusCode >= 400) {
      if (response.statusCode == 401) { _token = null; await store.clear(); }
      throw ApiException(response.statusCode, data is Map && data['message'] is String ? data['message'] :
        response.statusCode == 401 ? 'Session expirée, reconnectez-vous' : 'Demande refusée (${response.statusCode})');
    }
    return data;
  }
  void close() => client.close();
}
