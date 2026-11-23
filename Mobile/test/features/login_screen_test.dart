import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pps_smart_english/features/auth/login_screen.dart';

Widget _app() => const ProviderScope(child: MaterialApp(home: LoginScreen()));

void main() {
  testWidgets('để trống thì báo lỗi, không gọi mạng', (tester) async {
    await tester.pumpWidget(_app());
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pump();

    expect(find.text('Vui lòng nhập email'), findsOneWidget);
    expect(find.text('Vui lòng nhập mật khẩu'), findsOneWidget);
  });

  testWidgets('email sai định dạng', (tester) async {
    await tester.pumpWidget(_app());
    await tester.enterText(find.widgetWithText(TextFormField, 'Email'), 'abc');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pump();

    expect(find.text('Email không hợp lệ'), findsOneWidget);
  });
}
