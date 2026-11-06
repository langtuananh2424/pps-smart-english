package com.pps.smartenglish.shared.event;

/** Phát một lần duy nhất, ở lần đầu bài học chuyển sang hoàn thành (xem lại không phát). retention nghe để tính streak và XP. */
public record LessonCompleted(long userId, long lessonId) {}
