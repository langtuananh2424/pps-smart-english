import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import 'auth_controller.dart';
import 'auth_widgets.dart';
import 'validators.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _busy = false;
  bool _sent = false;
  String _error = '';

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = '';
    });
    try {
      await ref.read(authProvider.notifier).forgotPassword(_email.text.trim());
      // Backend luôn trả thành công chung, không lộ email có tồn tại không.
      if (mounted) setState(() => _sent = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final back = TextButton(
      onPressed: () => context.go('/dang-nhap'),
      child: Text(_sent ? 'Về trang đăng nhập' : 'Quay lại đăng nhập'),
    );
    if (_sent) {
      return AuthScaffold(
        title: 'Kiểm tra email của bạn',
        subtitle: 'Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu. '
            'Link có hiệu lực trong thời gian ngắn.',
        children: [back],
      );
    }
    return AuthScaffold(
      title: 'Quên mật khẩu?',
      subtitle: 'Nhập email đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu.',
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
                textInputAction: TextInputAction.done,
                onSubmitted: _submit,
              ),
              ErrorNotice(_error),
              SubmitButton(label: 'Gửi hướng dẫn', busy: _busy, onPressed: _submit),
            ],
          ),
        ),
        const SizedBox(height: 8),
        back,
      ],
    );
  }
}
