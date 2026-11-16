package com.pps.smartenglish.home.dto;

import java.time.Instant;
import java.util.List;

/** DTO của các API Trang chủ. */
public final class HomeDtos {

    private HomeDtos() {}

    /** status: ACTIVE hoặc EXPIRED (theo expiresAt). lastActivityAt null khi chưa học gì. */
    public record MyCourse(Long courseId, String slug, String title, String targetType, String status, Instant expiresAt,
                           Instant lastActivityAt) {}

    public record Roadmap(List<LevelNode> levels, Resume resume) {}

    public record LevelNode(Long id, String code, String title, List<TopicNode> topics) {}

    /** status: DONE, IN_PROGRESS, NOT_STARTED hoặc LOCKED. */
    public record TopicNode(Long id, String title, boolean isFreeTrial, boolean locked, int completedLessons, int totalLessons,
                            long xpEarned, String status, List<LessonNode> lessons, List<BankNode> banks) {}

    /** order: thứ tự 1-based trong topic. */
    public record LessonNode(Long id, String title, String type, Integer durationSec, int order, boolean completed,
                             int watchedSec) {}

    /** lessonId null: bộ gắn với cả topic. */
    public record BankNode(Long id, String title, Long lessonId, long questionCount) {}

    /** Bài chưa hoàn thành đầu tiên trong phần học viên được học. */
    public record Resume(Long lessonId, String lessonTitle, int lessonOrder, Long topicId, String topicTitle, Long levelId,
                         String levelCode, String levelTitle, int completedInTopic, int totalInTopic) {}

    public enum TaskKind { VIDEO, EXERCISE, WRITING }

    public record TodayTask(TaskKind kind, String title, String subtitle, Long lessonId, Long bankId, Long questionId,
                            Integer remainingSec, Long questionCount, boolean doneToday) {}

    public record Today(Long courseId, List<TodayTask> tasks, int doneCount, int total) {}

    public enum CamKind { GREETING, IN_PROGRESS_ATTEMPT, STREAK, COURSE_EXPIRING, UNREAD }

    public record CamAction(String label, String path) {}

    /** action null: chỉ là lời nhắn. */
    public record CamMessage(CamKind kind, String text, CamAction action) {}

    public record CamMessages(List<CamMessage> messages) {}
}
