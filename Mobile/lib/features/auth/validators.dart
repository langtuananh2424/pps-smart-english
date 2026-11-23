import 'dart:convert';

// Luật và câu chữ theo Web/src/auth/schemas.ts.
final _emailRe = RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$');

String? validateEmail(String? v) {
  final s = (v ?? '').trim();
  if (s.isEmpty) return 'Vui lòng nhập email';
  return _emailRe.hasMatch(s) ? null : 'Email không hợp lệ';
}

String? validateRequiredPassword(String? v) =>
    (v ?? '').isEmpty ? 'Vui lòng nhập mật khẩu' : null;

// Backend: tối thiểu 8 ký tự, tối đa 72 byte UTF-8 (giới hạn BCrypt).
String? validateNewPassword(String? v) {
  final s = v ?? '';
  if (s.length < 8) return 'Mật khẩu tối thiểu 8 ký tự';
  if (utf8.encode(s).length > 72) return 'Mật khẩu tối đa 72 byte';
  return null;
}

String? validateDisplayName(String? v) {
  final s = (v ?? '').trim();
  if (s.isEmpty) return 'Vui lòng nhập tên hiển thị';
  return s.length > 100 ? 'Tên hiển thị tối đa 100 ký tự' : null;
}

String? Function(String?) validateConfirm(String Function() password) =>
    (v) {
      if ((v ?? '').isEmpty) return 'Vui lòng nhập lại mật khẩu';
      return v == password() ? null : 'Mật khẩu nhập lại không khớp';
    };
