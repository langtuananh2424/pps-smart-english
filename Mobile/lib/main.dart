import 'package:flutter/material.dart';

void main() => runApp(const App());

class App extends StatelessWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PPS Smart English',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFE8590C)),
        useMaterial3: true,
      ),
      home: const Scaffold(body: Center(child: Text('PPS Smart English'))),
    );
  }
}
