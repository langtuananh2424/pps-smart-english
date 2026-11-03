import 'package:flutter_test/flutter_test.dart';
import 'package:pps_smart_english/course.dart';
import 'package:pps_smart_english/course_api.dart';
import 'package:pps_smart_english/main.dart';

class _FakeApi extends CourseApi {
  @override
  Future<List<Course>> fetchCourses() async => const [
        Course(
          id: 1,
          title: 'Giao tiếp phản xạ',
          description: 'Nói tự nhiên',
          levelLabel: 'Giao tiếp',
        ),
      ];
}

void main() {
  testWidgets('hiển thị danh sách khóa học', (tester) async {
    await tester.pumpWidget(App(api: _FakeApi()));
    await tester.pumpAndSettle();

    expect(find.text('Giao tiếp phản xạ'), findsOneWidget);
  });
}
