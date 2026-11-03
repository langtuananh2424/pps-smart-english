class Course {
  const Course({
    required this.id,
    required this.title,
    required this.description,
    required this.levelLabel,
  });

  factory Course.fromJson(Map<String, dynamic> json) => Course(
        id: json['id'] as int,
        title: json['title'] as String,
        description: (json['description'] as String?) ?? '',
        levelLabel: (json['levelLabel'] as String?) ?? '',
      );

  final int id;
  final String title;
  final String description;
  final String levelLabel;
}
