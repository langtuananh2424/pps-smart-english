import 'package:flutter_test/flutter_test.dart';
import 'package:pps_smart_english/main.dart';

void main() {
  testWidgets('hiển thị tiêu đề', (tester) async {
    await tester.pumpWidget(const App());
    expect(find.text('PPS Smart English'), findsOneWidget);
  });
}
