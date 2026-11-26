package com.pps.smartenglish.exercise.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * D-152: truy vấn đọc cho Luyện thêm. Nguồn câu hợp lệ là một chỗ duy nhất (SOURCE): bộ PRACTICE của khóa đã xuất bản, không Nghe ép,
 * câu chưa lưu trữ và không thuộc nhóm bài đọc/audio, không nằm trong lượt đang mở của học viên. Mọi truy vấn có giới hạn.
 */
@Repository
public class ExtraPoolRepository {

    private static final String SOURCE = """
            from questions q join question_banks b on b.id = q.bank_id join topics tp on tp.id = b.topic_id
            join course_levels lv on lv.id = tp.level_id
            where lv.course_id = :courseId and lv.status = 'PUBLISHED' and tp.status = 'PUBLISHED'
              and b.status = 'PUBLISHED' and b.purpose = 'COURSE' and b.mode = 'PRACTICE' and b.playback_mode <> 'LOCKED'
              and (b.lesson_id is null or exists (select 1 from lessons l where l.id = b.lesson_id and l.status = 'PUBLISHED'))
              and q.archived_at is null and q.group_id is null and q.type in (:types)
              and not exists (select 1 from student_exercise_attempts o
                              where o.user_id = :userId and o.bank_id = b.id and o.submitted_at is null)
              and not exists (select 1 from attempt_questions aq join student_exercise_attempts oa on oa.id = aq.attempt_id
                              where aq.question_id = q.id and oa.user_id = :userId and oa.submitted_at is null)
            """;

    public record TopicRow(long topicId, String title, int sortOrder, boolean freeTrial, long levelId, String levelTitle) {}

    /** Số câu hợp lệ của một topic theo dạng câu (type). */
    public record CountRow(long topicId, String type, int n) {}

    public record Candidate(long questionId, long topicId, String type) {}

    /** Kết quả các lượt đã nộp của topic: số câu khác nhau đã làm, số lần chấm, số lần đúng. */
    public record TopicStat(int questions, int graded, int correct) {}

    private final NamedParameterJdbcTemplate jdbc;

    ExtraPoolRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** Topic đã xuất bản của khóa theo thứ tự học. */
    public List<TopicRow> topics(long courseId) {
        return jdbc.query("select t.id, t.title, t.sort_order, t.is_free_trial, lv.id, lv.title from topics t "
                + "join course_levels lv on lv.id = t.level_id where lv.course_id = :c and lv.status = 'PUBLISHED' "
                + "and t.status = 'PUBLISHED' order by lv.sort_order, lv.id, t.sort_order, t.id",
                Map.of("c", courseId), (rs, i) -> new TopicRow(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4),
                        rs.getLong(5), rs.getString(6)));
    }

    public Map<Long, TopicStat> stats(long userId, Collection<Long> topicIds) {
        Map<Long, TopicStat> out = new HashMap<>();
        if (topicIds.isEmpty()) {
            return out;
        }
        // theo bank_id của lượt: lượt Luyện thêm (bộ neo, không topic) không lọt vào đây
        jdbc.query("select b.topic_id, count(distinct a.question_id), sum(case when a.is_correct is not null then 1 else 0 end), "
                + "sum(case when a.is_correct = true then 1 else 0 end) from student_answers a "
                + "join student_exercise_attempts t on t.id = a.attempt_id join question_banks b on b.id = t.bank_id "
                + "where t.user_id = :u and t.submitted_at is not null and t.result_mode = 'FULL' and b.topic_id in (:topics) "
                + "and b.purpose = 'COURSE' and b.mode = 'PRACTICE' group by b.topic_id",
                new MapSqlParameterSource("u", userId).addValue("topics", topicIds),
                rs -> { out.put(rs.getLong(1), new TopicStat(rs.getInt(2), rs.getInt(3), rs.getInt(4))); });
        return out;
    }

    public List<CountRow> counts(long userId, long courseId, List<String> types) {
        return jdbc.query("select b.topic_id, q.type, count(*) " + SOURCE + " group by b.topic_id, q.type",
                params(userId, courseId, types), (rs, i) -> new CountRow(rs.getLong(1), rs.getString(2), rs.getInt(3)));
    }

    public List<Candidate> candidates(long userId, long courseId, Collection<Long> topicIds, List<String> types, int limit) {
        if (topicIds.isEmpty()) {
            return List.of();
        }
        // Câu từng sai đi trước (sắp theo số lần sai), phần còn lại lấy mẫu ngẫu nhiên: kể cả Topic lớn hơn limit câu vẫn ưu tiên đúng câu sai
        // và không chỉ gặp các câu id nhỏ nhất. shortcut: order by random() sắp cả nguồn của Topic, ổn với vài nghìn câu; lớn hơn thì lấy mẫu theo khoảng id.
        var wrongIds = wrongIds(userId, topicIds, Math.max(1, limit / 2));
        Map<Long, Candidate> out = new java.util.LinkedHashMap<>();
        if (!wrongIds.isEmpty()) {
            query(SOURCE + " and b.topic_id in (:topics) and q.id in (:wrong)", params(userId, courseId, types).addValue("topics", topicIds)
                    .addValue("wrong", wrongIds), "q.id", limit).forEach(c -> out.put(c.questionId(), c));
        }
        query(SOURCE + " and b.topic_id in (:topics)", params(userId, courseId, types).addValue("topics", topicIds), "random()", limit)
                .forEach(c -> out.putIfAbsent(c.questionId(), c));
        return out.values().stream().limit(limit).toList();
    }

    private List<Candidate> query(String from, MapSqlParameterSource p, String order, int limit) {
        return jdbc.query("select q.id, b.topic_id, q.type " + from + " order by " + order + " limit :limit", p.addValue("limit", limit),
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), rs.getString(3)));
    }

    /** Câu của các topic mà học viên từng sai (cùng cách đếm với wrongCounts), nhiều lần sai nhất trước. */
    private List<Long> wrongIds(long userId, Collection<Long> topicIds, int limit) {
        return jdbc.queryForList("select w.question_id from (select question_id, sum(n) as n from ("
                + "select a.question_id as question_id, count(*) as n from student_answers a "
                + "join student_exercise_attempts t on t.id = a.attempt_id join questions q on q.id = a.question_id "
                + "where t.user_id = :u and t.submitted_at is not null and a.is_correct = false and a.answer_version = q.answer_version "
                + "group by a.question_id "
                + "union all select c.question_id, sum(c.wrong_count) from attempt_question_checks c "
                + "join student_exercise_attempts t on t.id = c.attempt_id where t.user_id = :u group by c.question_id) h "
                + "group by question_id) w join questions q on q.id = w.question_id join question_banks b on b.id = q.bank_id "
                + "where b.topic_id in (:topics) and w.n > 0 order by w.n desc, w.question_id limit :limit",
                new MapSqlParameterSource("u", userId).addValue("topics", topicIds).addValue("limit", limit), Long.class);
    }

    /**
     * Số lần sai tích lũy của học viên cho từng câu: câu trả lời cuối sai (cùng phiên bản đáp án, như LearningStatsRepository)
     * cộng số lần kiểm tra sai D-150.
     */
    public Map<Long, Long> wrongCounts(long userId, Collection<Long> questionIds) {
        Map<Long, Long> out = new HashMap<>();
        if (questionIds.isEmpty()) {
            return out;
        }
        var p = new MapSqlParameterSource("u", userId).addValue("ids", questionIds);
        jdbc.query("select a.question_id, count(*) from student_answers a join student_exercise_attempts t on t.id = a.attempt_id "
                + "join questions q on q.id = a.question_id where t.user_id = :u and t.submitted_at is not null "
                + "and a.is_correct = false and a.answer_version = q.answer_version and a.question_id in (:ids) group by a.question_id",
                p, rs -> { out.merge(rs.getLong(1), rs.getLong(2), Long::sum); });
        jdbc.query("select c.question_id, sum(c.wrong_count) from attempt_question_checks c "
                + "join student_exercise_attempts t on t.id = c.attempt_id where t.user_id = :u and c.question_id in (:ids) "
                + "group by c.question_id", p, rs -> { out.merge(rs.getLong(1), rs.getLong(2), Long::sum); });
        return out;
    }

    private static MapSqlParameterSource params(long userId, long courseId, List<String> types) {
        return new MapSqlParameterSource("userId", userId).addValue("courseId", courseId).addValue("types", types);
    }
}
