package com.pps.smartenglish.lms.dto;

/** FR-LMS-04, D-122: unit có đang khóa với học viên không và tiến độ unit đang chặn. Các khối null khi không áp dụng. */
public record UnitGate(Long topicId, boolean locked, boolean openedEarly, Long blockingTopicId, String blockingTitle,
                       Lectures lectures, Exercises exercises, ChapterTest chapterTest) {

    public record Lectures(int completed, int total, int requiredPercent, int currentPercent) {}

    public record Exercises(int completed, int total) {}

    public record ChapterTest(boolean submitted) {}
}
