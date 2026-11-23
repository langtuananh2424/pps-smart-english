import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import 'auth_controller.dart';
import 'auth_widgets.dart';
import 'validators.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  String _error = '';

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = '';
    });
    try {
      await ref
          .read(authProvider.notifier)
          .register(_email.text.trim(), _password.text, _name.text.trim());
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Tạo tài khoản',
      subtitle: 'Bắt đầu hành trình học tiếng Anh cùng Cam.',
      children: [
        Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AppTextField(
                label: 'Tên hiển thị',
                hint: 'vd: Lan Anh',
                controller: _name,
                validator: validateDisplayName,
                autofillHints: const [AutofillHints.nickname],
              ),
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
                hint: 'Tối thiểu 8 ký tự',
                controller: _password,
                validator: validateNewPassword,
                secret: true,
                autofillHints: const [AutofillHints.newPassword],
              ),
              AppTextField(
                label: 'Nhập lại mật khẩu',
                hint: 'Nhập lại mật khẩu',
                controller: _confirm,
                validator: validateConfirm(() => _password.text),
                secret: true,
                autofillHints: const [AutofillHints.newPassword],
                textInputAction: TextInputAction.done,
                onSubmitted: _submit,
              ),
              ErrorNotice(_error),
              SubmitButton(label: 'Đăng ký', busy: _busy, onPressed: _submit),
            ],
          ),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => context.go('/dang-nhap'),
          child: const Text('Đã có tài khoản? Đăng nhập'),
        ),
      ],
    );
  }
}
