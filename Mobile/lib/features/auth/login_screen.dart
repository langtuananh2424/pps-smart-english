import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import 'auth_controller.dart';
import 'auth_widgets.dart';
import 'validators.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String _error = '';

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = '';
    });
    try {
      // Router tự chuyển trang khi có phiên.
      await ref.read(authProvider.notifier).login(_email.text.trim(), _password.text);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Chào mừng trở lại!',
      subtitle: 'Đăng nhập để tiếp tục lộ trình học.',
      children: [
        Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AppTextField(
                label: 'Email',
                hint: 'vd: ban@example.com',
                controller: _email,
                validator: validateEmail,
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
              ),
              AppTextField(
                label: 'Mật khẩu',
                hint: 'Nhập mật khẩu',
                controller: _password,
                validator: validateRequiredPassword,
                secret: true,
                autofillHints: const [AutofillHints.password],
                textInputAction: TextInputAction.done,
                onSubmitted: _submit,
              ),
              ErrorNotice(_error),
              SubmitButton(
                label: 'Đăng nhập',
                busy: _busy,
                onPressed: _submit,
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => context.go('/quen-mat-khau'),
          child: const Text('Quên mật khẩu?'),
        ),
        TextButton(
          onPressed: () => context.go('/dang-ky'),
          child: const Text('Chưa có tài khoản? Đăng ký'),
        ),
      ],
    );
  }
}
