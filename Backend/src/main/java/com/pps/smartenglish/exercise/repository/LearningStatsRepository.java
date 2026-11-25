package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.dto.LearningDtos.Metric;
import com.pps.smartenglish.exercise.dto.LearningDtos.WrongQuestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn gộp thống kê (FR-EX-08/09). Không có bảng tổng hợp: mỗi lần tính từ student_answers theo chỉ mục
 * (user_id, submitted_at) của lượt và (attempt_id, question_id) của câu trả lời. JdbcTemplate vì là truy vấn đọc ghép nhiều
 * bảng, và để H2/PostgreSQL cho cùng kết quả. Biểu thức SQL trong enum là hằng, không bao giờ lấy từ request.
 */
@Repository
public class LearningStatsRepository {

    /** D-147: lượt còn job AI chấm lần đầu chưa xong chỉ có điểm tạm, không đưa vào thống kê (cùng điều kiện AttemptResultGate). */
    private static final String SETTLED = " and not exists (select 1 from ai_jobs j where j.attempt_id = t.id "
            + "and j.regrade_item_id is null and j.status in ('PENDING', 'RUNNING'))";

    public enum Dimension {
        SKILL("coalesce(q.skill, tp.skill, 'UNSPECIFIED')", "coalesce(q.skill, tp.skill, 'UNSPECIFIED')", "", ""),
        TYPE("q.type", "q.type", "", ""),
        GRAMMAR("ge.code", "ge.name", "join question_grammar_tags gt on gt.question_id = q.id "
                + "join grammar_elements ge on ge.id = gt.grammar_element_id", ""),
        TOPIC("cast(tp.id as varchar(20))", "tp.title", "", " and tp.id is not null");

        private final String key;
        private final String label;
        private final String join;
        private final String filter;

        Dimension(String key, String label, String join, String filter) {
            this.key = key;
            this.label = label;
            this.join = join;
            this.filter = filter;
        }
    }

    /** Phạm vi: mọi lượt đã nộp của một học viên, hoặc đúng một lượt (đã kiểm chủ lượt và đã nộp). */
    public record Scope(String where, long arg) {
        public static Scope user(long userId) {
            // lượt Nghe ép không có điểm và đáp án từng câu (D-142), không gộp vào hồ sơ
            return new Scope("t.user_id = ? and t.submitted_at is not null and t.result_mode = 'FULL'" + SETTLED, userId);
        }

        public static Scope attempt(long attemptId) {
            return new Scope("t.id = ?", attemptId);
        }
    }

    /** Một lượt đã nộp: thời điểm nộp, số câu đã chấm, số câu đúng. */
    public record AttemptTotals(OffsetDateTime submittedAt, long answered, long correct) {}

    private final JdbcTemplate jdbc;

    LearningStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Metric> metrics(Dimension d, Scope scope) {
        String sql = "select " + d.key + ", " + d.label + ", "
                + "sum(case when a.is_correct is not null then 1 else 0 end), "
                + "sum(case when a.is_correct = true then 1 else 0 end), "
                + "coalesce(sum(a.credit), 0), "
                + "sum(case when a.is_correct is null then 1 else 0 end), "
                + "sum(case when a.is_correct is null and g.id is not null then 1 else 0 end), "
                + "coalesce(sum(case when a.is_correct is null then g.score end), 0) "
                + "from student_answers a join student_exercise_attempts t on t.id = a.attempt_id "
                + "join questions q on q.id = a.question_id "
                + "left join question_banks b on b.id = q.bank_id left join topics tp on tp.id = b.topic_id " + d.join + " "
                + "left join student_answer_grading g on g.answer_id = a.id and g.grader = 'AI' "
                + "where " + scope.where + d.filter + " group by " + d.key + ", " + d.label + " order by 1";
        return jdbc.query(sql, (rs, i) -> {
            long answered = rs.getLong(3);
            long correct = rs.getLong(4);
            long aiGraded = rs.getLong(7);
            BigDecimal aiAvg = aiGraded == 0 ? null
                    : rs.getBigDecimal(8).divide(BigDecimal.valueOf(aiGraded), 2, RoundingMode.HALF_UP);
            return new Metric(rs.getString(1), rs.getString(2), answered, correct, rs.getBigDecimal(5),
                    answered == 0 ? null : (int) Math.round(correct * 100.0 / answered), rs.getLong(6), aiGraded, aiAvg);
        }, scope.arg);
    }

    /**
     * Số lần sai tích lũy của học viên cho các câu sai trong lượt này; chỉ tính dòng cùng phiên bản đáp án hiện tại của
     * câu (đáp án đã sửa thì lần sai cũ không tính oan, mục 7.3). Chấm lại cập nhật answer_version của dòng đã chấm lại.
     */
    public List<WrongQuestion> wrongCounts(long userId, long attemptId) {
        return jdbc.query("select a.question_id, count(*) from student_answers a "
                + "join student_exercise_attempts t on t.id = a.attempt_id join questions q on q.id = a.question_id "
                + "where t.user_id = ? and t.submitted_at is not null and a.is_correct = false "
                + "and a.answer_version = q.answer_version "
                + "and a.question_id in (select w.question_id from student_answers w where w.attempt_id = ? and w.is_correct = false) "
                + "group by a.question_id order by 2 desc, 1", (rs, i) -> new WrongQuestion(rs.getLong(1), rs.getLong(2)),
                userId, attemptId);
    }

    /** Mỗi lượt đã nộp từ mốc since một dòng: số dòng bằng số lượt, không phải số câu. Gộp theo tuần ở service (múi giờ). */
    public List<AttemptTotals> attemptTotalsSince(long userId, OffsetDateTime since) {
        return jdbc.query("select t.submitted_at, sum(case when a.is_correct is not null then 1 else 0 end), "
                + "sum(case when a.is_correct = true then 1 else 0 end) "
                + "from student_exercise_attempts t join student_answers a on a.attempt_id = t.id "
                + "where t.user_id = ? and t.result_mode = 'FULL' and t.submitted_at >= ?" + SETTLED + " group by t.id, t.submitted_at",
                (rs, i) -> new AttemptTotals(rs.getObject(1, OffsetDateTime.class), rs.getLong(2), rs.getLong(3)),
                userId, since.withOffsetSameInstant(ZoneOffset.UTC));
    }
}
