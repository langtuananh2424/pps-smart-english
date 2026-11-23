import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/auth_controller.dart';

/// GET /api/me (MeResponse: displayName, email, ...).
final meProvider = FutureProvider.autoDispose<Map<String, dynamic>?>(
  (ref) => ref.watch(apiClientProvider).get('/me'),
);

class ProfileTab extends ConsumerWidget {
  const ProfileTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final me = ref.watch(meProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Hồ sơ')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            me.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Column(
                children: [
                  Semantics(liveRegion: true, child: Text('$e')),
                  TextButton(
                    onPressed: () => ref.invalidate(meProvider),
                    child: const Text('Thử lại'),
                  ),
                ],
              ),
              data: (m) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${m?['displayName'] ?? ''}',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 4),
                  Text('${m?['email'] ?? ''}'),
                ],
              ),
            ),
            const Spacer(),
            OutlinedButton(
              style: OutlinedButton.styleFrom(minimumSize: const Size(48, 52)),
              onPressed: () => ref.read(authProvider.notifier).logout(),
              child: const Text('Đăng xuất'),
            ),
          ],
        ),
      ),
    );
  }
}
