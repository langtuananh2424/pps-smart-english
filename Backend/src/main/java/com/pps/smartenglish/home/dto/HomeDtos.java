package com.pps.smartenglish.home.dto;

import com.pps.smartenglish.catalog.domain.TopicSkill;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO của các API Trang chủ. */
public final class HomeDtos {

    private HomeDtos() {}

    /**
     * status: ACTIVE, EXPIRED (theo expiresAt) hoặc TRIAL (chưa mua, có tiến độ ở topic học thử, D-61).
     * lastActivityAt null khi chưa học gì. completedLessons/totalLessons: bài PUBLISHED của khóa (D-46).
     */
    public record MyCourse(Long courseId, String slug, String title, String targetType, String status, Instant expiresAt,
                           Instant lastActivityAt, int completedLessons, int totalLessons,
                           int answeredQuestions, int correctQuestions) {}

    public record Roadmap(List<LevelNode> levels, Resume resume) {}

    public record LevelNode(Long id, String code, String title, List<TopicNode> topics) {}

    /** status: DONE, IN_PROGRESS, NOT_STARTED hoặc LOCKED. lockReason (khi locked): PAYWALL (chưa mua) hoặc SEQUENTIAL (học tuần tự, FR-LMS-04). */
    public record TopicNode(Long id, String title, boolean isFreeTrial, TopicSkill skill, boolean locked, int completedLessons, int totalLessons,
                            long xpEarned, String status, List<LessonNode> lessons, List<BankNode> banks, String lockReason) {}

    /** order: thứ tự 1-based trong topic. lastWatchedAt null khi chưa có tiến độ (D-59). */
    public record LessonNode(Long id, String title, String type, Integer durationSec, int order, boolean completed,
                             int watchedSec, int maxPositionSec, Instant lastWatchedAt) {}

    /** lessonId null: bộ gắn với cả topic. */
    public record BankNode(Long id, String title, Long lessonId, long questionCount) {}

    /** D-59: bài chưa xong xem gần nhất; không có thì bài chưa xong đầu tiên. positionSec: vị trí đã dừng. */
    public record Resume(Long lessonId, String lessonTitle, int lessonOrder, Long topicId, String topicTitle, Long levelId,
                         String levelCode, String levelTitle, int completedInTopic, int totalInTopic, int positionSec,
                         Integer durationSec) {}

    /** D-62, D-63. accuracy là phần trăm nguyên, null khi chưa làm câu nào. */
    public record ExerciseStats(int answered, int correct, Integer accuracy, int typesPracticed, List<TypeStat> byType,
                                Weakest weakest, Band band) {}

    public record TypeStat(QuestionType type, long questionCount, long answered, long correct, Integer accuracy) {}

    public record Weakest(QuestionType type, int accuracy, long answered) {}

    /** source: PLACEMENT (EXAM chưa có: bài thi khóa chưa lưu band). */
    public record Band(BigDecimal value, String source, Instant at) {}

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
