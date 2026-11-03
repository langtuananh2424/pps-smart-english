import 'package:flutter/material.dart';

import 'course.dart';
import 'course_api.dart';

void main() => runApp(App(api: CourseApi()));

class App extends StatelessWidget {
  const App({super.key, required this.api});

  final CourseApi api;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PPS Smart English',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFE8590C)),
        useMaterial3: true,
      ),
      home: CourseListPage(api: api),
    );
  }
}

class CourseListPage extends StatelessWidget {
  const CourseListPage({super.key, required this.api});

  final CourseApi api;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('PPS Smart English')),
      body: FutureBuilder<List<Course>>(
        future: api.fetchCourses(),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Text('Không tải được danh sách khóa học (${snapshot.error})'),
            );
          }
          final courses = snapshot.data!;
          return ListView.separated(
            itemCount: courses.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) => ListTile(
              title: Text(courses[i].title),
              subtitle: Text(courses[i].description),
            ),
          );
        },
      ),
    );
  }
}
