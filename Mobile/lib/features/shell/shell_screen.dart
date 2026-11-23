import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Thanh tab dưới; nhãn và thứ tự theo Web/src/layout/navItems.ts (nhãn mobile),
/// Không có "Khóa học" theo yêu cầu đợt đầu (D-96).
const shellTabs = <({String label, IconData icon})>[
  (label: 'Học', icon: Icons.home_outlined),
  (label: 'Bài giảng', icon: Icons.play_lesson_outlined),
  (label: 'Bài tập', icon: Icons.edit_note_outlined),
  (label: 'Hồ sơ', icon: Icons.person_outline),
];

class ShellScreen extends StatelessWidget {
  const ShellScreen({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (i) => navigationShell.goBranch(
          i,
          initialLocation: i == navigationShell.currentIndex,
        ),
        destinations: [
          for (final t in shellTabs)
            NavigationDestination(icon: Icon(t.icon), label: t.label),
        ],
      ),
    );
  }
}

/// Thân tab chưa làm.
class PlaceholderTab extends StatelessWidget {
  const PlaceholderTab(this.title, {super.key});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: const Center(child: Text('Đang xây dựng')),
    );
  }
}
