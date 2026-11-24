package com.pps.smartenglish.exercise.listening;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Đếm theo học viên cho Nghe ép (mục 7.3-7.4): số lượt đã nộp và số lần sai của từng câu. JdbcTemplate như LearningStatsRepository
 * để H2 và PostgreSQL cho cùng kết quả. Chỉ tính các lượt COUNT_ONLY của học viên trên bộ, có id không vượt lượt đang xem,
 * nên kết quả của một lượt luôn như nhau dù xem lại sau này.
 */
@Repository
public class ListeningStatsRepository {

    public record WrongCount(long questionId, long wrongCount) {}

    private final JdbcTemplate jdbc;

    ListeningStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Số lượt đã nộp tới và gồm lượt upToAttemptId. assignmentId khác null (D-143): chỉ đếm lượt của bài giao đó; null: mọi lượt
     * Nghe ép của học viên trên bộ.
     */
    public int submittedCount(long userId, long bankId, Long assignmentId, long upToAttemptId) {
        return jdbc.queryForObject("select count(*) from student_exercise_attempts where user_id = ? and bank_id = ? "
                + "and result_mode = 'COUNT_ONLY' and submitted_at is not null and id <= ?" + (assignmentId == null ? "" : " and assignment_id = ?"),
                Integer.class, args(userId, bankId, upToAttemptId, assignmentId));
    }

    private static Object[] args(Object... base) {
        return java.util.Arrays.stream(base).filter(java.util.Objects::nonNull).toArray();
    }

    /**
     * Câu sai từ threshold lần trở lên, nhiều lần sai trước. Chỉ tính dòng cùng phiên bản đáp án hiện tại của câu:
     * đáp án đã sửa thì lần sai cũ không tính oan (mục 7.3).
     */
    public List<WrongCount> hardQuestions(long userId, long bankId, Long assignmentId, long upToAttemptId, int threshold) {
        return jdbc.query("select a.question_id, count(*) from student_answers a "
                + "join student_exercise_attempts t on t.id = a.attempt_id join questions q on q.id = a.question_id "
                + "where t.user_id = ? and t.bank_id = ? and t.result_mode = 'COUNT_ONLY' and t.submitted_at is not null and t.id <= ? "
                + "and a.is_correct = false and a.answer_version = q.answer_version and q.archived_at is null "
                + (assignmentId == null ? "" : "and t.assignment_id = ? ")
                + "group by a.question_id having count(*) >= ? order by 2 desc, 1",
                (rs, i) -> new WrongCount(rs.getLong(1), rs.getLong(2)), args(userId, bankId, upToAttemptId, assignmentId, threshold));
    }

    /** Ghi một lần mỗi (lượt, câu); xem lại không tạo dòng mới. Trả true nếu là lần đầu. */
    public boolean recordScriptView(long userId, long questionId, long attemptId, Instant now) {
        return jdbc.update("insert into script_views (user_id, question_id, attempt_id, viewed_at) "
                + "select ?, ?, ?, ? where not exists (select 1 from script_views where attempt_id = ? and question_id = ?)",
                userId, questionId, attemptId, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), attemptId, questionId) > 0;
    }
}
