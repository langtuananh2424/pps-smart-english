import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pps_smart_english/core/api_client.dart';
import 'package:pps_smart_english/core/token_store.dart';

class FakeTokenStore implements TokenStore {
  String? token;

  @override
  Future<String?> readRefreshToken() async => token;

  @override
  Future<void> writeRefreshToken(String t) async => token = t;

  @override
  Future<void> clear() async => token = null;
}

typedef Handler = Future<ResponseBody> Function(RequestOptions o);

class FakeAdapter implements HttpClientAdapter {
  FakeAdapter(this.handler);

  final Handler handler;
  final calls = <RequestOptions>[];

  int count(String path) => calls.where((o) => o.path == path).length;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    calls.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonRes(int status, Object body) => ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

const refreshOk = {'accessToken': 'new', 'refreshToken': 'r2', 'user': {'id': 1}};

void main() {
  late FakeTokenStore store;
  late FakeAdapter adapter;
  late ApiClient client;
  var lost = 0;

  // `/me` chỉ nhận token "new"; refresh trễ một chút để các yêu cầu kịp xếp hàng.
  void setUp0({bool refreshFails = false}) {
    store = FakeTokenStore()..token = 'r1';
    lost = 0;
    adapter = FakeAdapter((o) async {
      if (o.path == '/auth/refresh') {
        await Future<void>.delayed(const Duration(milliseconds: 20));
        return refreshFails
            ? jsonRes(401, {'error': {'code': 'INVALID', 'message': 'Phiên hết hạn'}})
            : jsonRes(200, refreshOk);
      }
      if (o.headers['Authorization'] == 'Bearer new') return jsonRes(200, {'ok': true});
      return jsonRes(401, {'error': {'code': 'UNAUTH', 'message': 'Chưa đăng nhập'}});
    });
    final dio = Dio(BaseOptions(baseUrl: 'http://test/api'))..httpClientAdapter = adapter;
    client = ApiClient(store: store, dio: dio, onSessionLost: () => lost++);
  }

  test('401 -> refresh -> thử lại một lần, lưu refresh token mới', () async {
    setUp0();
    final res = await client.get('/me');

    expect(res, {'ok': true});
    expect(adapter.count('/me'), 2);
    expect(adapter.count('/auth/refresh'), 1);
    expect(store.token, 'r2');
    expect(adapter.calls.first.headers['X-Client'], 'mobile');
    final refresh = adapter.calls.firstWhere((o) => o.path == '/auth/refresh');
    expect(refresh.data, {'refreshToken': 'r1'});
  });

  test('nhiều 401 đồng thời chỉ refresh một lần', () async {
    setUp0();
    final results = await Future.wait([client.get('/me'), client.get('/me'), client.get('/me')]);

    expect(results.every((r) => r?['ok'] == true), isTrue);
    expect(adapter.count('/auth/refresh'), 1);
  });

  test('refresh thất bại: xóa token, báo mất phiên, ném lỗi gốc', () async {
    setUp0(refreshFails: true);

    await expectLater(
      client.get('/me'),
      throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Chưa đăng nhập')),
    );
    expect(store.token, isNull);
    expect(lost, 1);
    expect(adapter.count('/me'), 1);
  });

  test('401 của /auth/* không kích hoạt refresh', () async {
    setUp0();
    await expectLater(client.post('/auth/login', {}), throwsA(isA<ApiException>()));
    expect(adapter.count('/auth/refresh'), 0);
  });

  test('restore không có refresh token trả null, không gọi mạng', () async {
    setUp0();
    store.token = null;
    expect(await client.restore(), isNull);
    expect(adapter.calls, isEmpty);
  });
}
