package com.pps.smartenglish.exercise.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-152: danh sách câu của lượt Luyện thêm (attempt_questions). */
@Repository
public class AttemptQuestionRepository {

    private final JdbcTemplate jdbc;

    AttemptQuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long attemptId, List<Long> questionIds) {
        jdbc.batchUpdate("insert into attempt_questions (attempt_id, question_id, sort_order) values (?, ?, ?)",
                questionIds.stream().map(id -> new Object[] {attemptId, id, questionIds.indexOf(id)}).toList());
    }

    public List<Long> questionIds(long attemptId) {
        return jdbc.queryForList("select question_id from attempt_questions where attempt_id = ? order by sort_order", Long.class, attemptId);
    }

    public boolean contains(long attemptId, long questionId) {
        return !jdbc.queryForList("select 1 from attempt_questions where attempt_id = ? and question_id = ?", attemptId, questionId).isEmpty();
    }

    /** Lượt Luyện thêm đang mở của học viên có câu này (D-147 mục 11). */
    public Optional<Long> openAttemptOf(long userId, long questionId) {
        return jdbc.queryForList("select a.id from student_exercise_attempts a join attempt_questions aq on aq.attempt_id = a.id "
                + "where a.user_id = ? and a.submitted_at is null and (a.assignment_id is null or exists (select 1 from assignments s where s.id = a.assignment_id and s.status = 'ACTIVE')) and aq.question_id = ? order by a.id desc limit 1",
                Long.class, userId, questionId).stream().findFirst();
    }
}
