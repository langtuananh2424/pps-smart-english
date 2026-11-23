// 10.0.2.2 là máy host nhìn từ Android emulator; ghi đè bằng --dart-define.
const apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080/api',
);
