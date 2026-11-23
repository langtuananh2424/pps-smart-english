import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/token_store.dart';

class SessionUser {
  const SessionUser({required this.id, required this.displayName, required this.role});

  factory SessionUser.fromJson(Map<String, dynamic> j) => SessionUser(
        id: (j['id'] as num).toInt(),
        displayName: j['displayName'] as String? ?? '',
        role: j['role'] as String? ?? '',
      );

  final int id;
  final String displayName;
  final String role;
}

class AuthState {
  const AuthState({this.loading = true, this.user});

  /// true trong lúc khôi phục phiên lúc mở app.
  final bool loading;
  final SessionUser? user;
}

final tokenStoreProvider = Provider<TokenStore>((_) => SecureTokenStore());

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(store: ref.watch(tokenStoreProvider)),
);

final authProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);

class AuthController extends Notifier<AuthState> {
  ApiClient get _api => ref.read(apiClientProvider);

  @override
  AuthState build() {
    _api.onSessionLost = () => state = const AuthState(loading: false);
    Future.microtask(_restore);
    return const AuthState();
  }

  Future<void> _restore() async {
    final body = await _api.restore();
    final user = body?['user'];
    state = AuthState(
      loading: false,
      user: user is Map<String, dynamic> ? SessionUser.fromJson(user) : null,
    );
  }

  /// Ném [ApiException] khi lỗi để màn hình hiển thị message.
  Future<void> login(String email, String password) =>
      _start('/auth/login', {'identifier': email, 'password': password});

  Future<void> register(String email, String password, String displayName) =>
      _start('/auth/register', {
        'email': email,
        'password': password,
        'displayName': displayName,
      });

  Future<void> forgotPassword(String email) =>
      _api.post('/auth/forgot-password', {'email': email});

  Future<void> logout() async {
    await _api.logout();
    state = const AuthState(loading: false);
  }

  Future<void> _start(String path, Map<String, dynamic> body) async {
    final res = await _api.post(path, body);
    if (res == null) throw const ApiException(networkError);
    await _accept(res);
  }

  Future<void> _accept(Map<String, dynamic> res) async {
    await _api.setTokens(res['accessToken'] as String, res['refreshToken'] as String?);
    state = AuthState(
      loading: false,
      user: SessionUser.fromJson(res['user'] as Map<String, dynamic>),
    );
  }
}
