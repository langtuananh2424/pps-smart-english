import 'dart:convert';

import 'package:http/http.dart' as http;

import 'course.dart';

/// Android emulator reaches the host machine at 10.0.2.2.
/// Override with `--dart-define=API_BASE_URL=https://api.example.com`.
const _defaultBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);

class CourseApi {
  CourseApi({http.Client? client, String baseUrl = _defaultBaseUrl})
      : _client = client ?? http.Client(),
        _baseUrl = baseUrl;

  final http.Client _client;
  final String _baseUrl;

  Future<List<Course>> fetchCourses() async {
    final res = await _client.get(Uri.parse('$_baseUrl/api/courses'));
    if (res.statusCode != 200) {
      throw Exception('HTTP ${res.statusCode}');
    }
    final list = jsonDecode(utf8.decode(res.bodyBytes)) as List<dynamic>;
    return list.map((e) => Course.fromJson(e as Map<String, dynamic>)).toList();
  }
}
