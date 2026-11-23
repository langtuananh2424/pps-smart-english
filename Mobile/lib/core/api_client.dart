import 'package:dio/dio.dart';

import 'config.dart';
import 'token_store.dart';

const networkError = 'Không kết nối được máy chủ, vui lòng thử lại.';
const _genericError = 'Có lỗi xảy ra, vui lòng thử lại.';
const _retriedKey = 'retried';

/// Lỗi gọi API; [message] lấy từ `{"error":{"message"}}` của backend.
class ApiException implements Exception {
  const ApiException(this.message, {this.status, this.code});

  final String message;
  final int? status;
  final String? code;

  @override
  String toString() => message;
}

ApiException _toApiException(DioException e) {
  final res = e.response;
  if (res == null) return const ApiException(networkError);
  final data = res.data;
  final err = data is Map ? data['error'] : null;
  if (err is Map) {
    return ApiException(
      (err['message'] as String?) ?? _genericError,
      status: res.statusCode,
      code: err['code'] as String?,
    );
  }
  return ApiException(_genericError, status: res.statusCode);
}

/// Phản chiếu Web/src/lib/api.ts: Bearer trong bộ nhớ, 401 -> refresh một lần rồi thử lại.
class ApiClient {
  ApiClient({required TokenStore store, Dio? dio, this.onSessionLost})
      : _store = store,
        _dio = dio ?? Dio(BaseOptions(baseUrl: apiBaseUrl)) {
    _dio.options
      ..connectTimeout = const Duration(seconds: 15)
      ..receiveTimeout = const Duration(seconds: 30);
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (o, h) {
        o.headers['X-Client'] = 'mobile';
        final t = _accessToken;
        if (t != null) o.headers['Authorization'] = 'Bearer $t';
        h.next(o);
      },
      onError: _onError,
    ));
  }

  final TokenStore _store;
  final Dio _dio;

  /// Gọi khi phiên hết hạn và không refresh được.
  void Function()? onSessionLost;

  String? _accessToken;
  // Refresh token xoay vòng: hai lần refresh đồng thời sẽ làm hỏng phiên.
  Future<Map<String, dynamic>?>? _refreshing;

  Future<Map<String, dynamic>?> get(String path) => _send('GET', path);

  Future<Map<String, dynamic>?> post(String path, [Object? body]) =>
      _send('POST', path, body);

  Future<Map<String, dynamic>?> _send(
    String method,
    String path, [
    Object? body,
  ]) async {
    try {
      final res = await _dio.request<Object?>(
        path,
        data: body,
        options: Options(method: method),
      );
      final d = res.data;
      return d is Map<String, dynamic> ? d : null;
    } on DioException catch (e) {
      throw _toApiException(e);
    }
  }

  Future<void> setTokens(String access, String? refresh) async {
    _accessToken = access;
    if (refresh != null) await _store.writeRefreshToken(refresh);
  }

  Future<void> clearSession() async {
    _accessToken = null;
    await _store.clear();
  }

  /// Khôi phục phiên lúc mở app; trả body AuthResponse hoặc null.
  Future<Map<String, dynamic>?> restore() => _refresh();

  /// Thu hồi refresh token trên server rồi xóa phiên; lỗi server vẫn xóa phía máy.
  Future<void> logout() async {
    final token = await _store.readRefreshToken();
    if (token != null) {
      try {
        await post('/auth/logout', {'refreshToken': token});
      } on ApiException {
        // vẫn xóa phiên cục bộ
      }
    }
    await clearSession();
  }

  Future<Map<String, dynamic>?> _refresh() =>
      _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);

  Future<Map<String, dynamic>?> _doRefresh() async {
    final token = await _store.readRefreshToken();
    if (token == null) return null;
    try {
      final body = await post('/auth/refresh', {'refreshToken': token});
      final access = body?['accessToken'] as String?;
      if (access == null) return null;
      await setTokens(access, body?['refreshToken'] as String?);
      return body;
    } on ApiException catch (e) {
      // Chỉ xóa token khi server từ chối; mất mạng thì giữ để thử lại lần sau.
      if (e.status != null) await clearSession();
      return null;
    }
  }

  Future<void> _onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final req = err.requestOptions;
    final skip = err.response?.statusCode != 401 ||
        req.path.startsWith('/auth/') ||
        req.extra[_retriedKey] == true;
    if (skip) return handler.next(err);

    final refreshed = await _refresh();
    if (refreshed == null) {
      _accessToken = null;
      onSessionLost?.call();
      return handler.next(err);
    }
    req.extra[_retriedKey] = true;
    try {
      handler.resolve(await _dio.fetch<Object?>(req));
    } on DioException catch (e) {
      handler.next(e);
    }
  }
}
