import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/theme.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/forgot_password_screen.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/register_screen.dart';
import 'features/shell/profile_tab.dart';
import 'features/shell/shell_screen.dart';

const _publicPaths = {'/dang-nhap', '/dang-ky', '/quen-mat-khau'};

String? _redirect(AuthState auth, String path) {
  if (auth.loading) return path == '/splash' ? null : '/splash';
  final loggedIn = auth.user != null;
  if (path == '/splash') return loggedIn ? '/' : '/dang-nhap';
  if (!loggedIn && !_publicPaths.contains(path)) return '/dang-nhap';
  if (loggedIn && _publicPaths.contains(path)) return '/';
  return null;
}

GoRoute _tab(String path, Widget child) =>
    GoRoute(path: path, builder: (_, __) => child);

final routerProvider = Provider<GoRouter>((ref) {
  // Báo router chạy lại redirect mỗi khi trạng thái đăng nhập đổi.
  final auth = ValueNotifier<AuthState>(ref.read(authProvider));
  ref.listen(authProvider, (_, next) => auth.value = next);
  ref.onDispose(auth.dispose);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: auth,
    redirect: (_, state) => _redirect(auth.value, state.uri.path),
    routes: [
      GoRoute(
        path: '/splash',
        builder: (_, __) => const Scaffold(body: Center(child: CircularProgressIndicator())),
      ),
      GoRoute(path: '/dang-nhap', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/dang-ky', builder: (_, __) => const RegisterScreen()),
      GoRoute(path: '/quen-mat-khau', builder: (_, __) => const ForgotPasswordScreen()),
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => ShellScreen(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [_tab('/', const PlaceholderTab('Học'))]),
          StatefulShellBranch(routes: [_tab('/bai-giang', const PlaceholderTab('Bài giảng'))]),
          StatefulShellBranch(routes: [_tab('/bai-tap', const PlaceholderTab('Bài tập'))]),
          StatefulShellBranch(routes: [_tab('/ho-so', const ProfileTab())]),
        ],
      ),
    ],
  );
});

class App extends ConsumerWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'PPS Smart English',
      theme: buildTheme(),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
