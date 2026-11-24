package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ForcedProgress;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-143: bài giao Nghe ép cho làm lại không giới hạn. Phần này tổng hợp cho giáo viên theo từng người nhận: lượt mới nhất (số câu đúng,
 * đạt hay chưa), số lần đã làm, đã có lượt nào đạt chưa. Không có điểm (D-142).
 */
@Service
public class ForcedAssignmentProgress {

    private final QuestionBankRepository banks;
    private final ForcedRules rules;
    private final JdbcTemplate jdbc;

    ForcedAssignmentProgress(QuestionBankRepository banks, ForcedRules rules, JdbcTemplate jdbc) {
        this.banks = banks;
        this.rules = rules;
        this.jdbc = jdbc;
    }

    /** Theo userId; người chưa nộp lượt nào không có trong kết quả. */
    @Transactional(readOnly = true)
    public Map<Long, ForcedProgress> byUser(long bankId, long assignmentId) {
        RuleView rule = rules.forAssignment(bankId, assignmentId);
        // một dòng mỗi (lượt, part); câu không thuộc nhóm có group_id null và chỉ tính vào tổng
        var rows = jdbc.query("select t.id, t.user_id, q.group_id, sum(case when a.is_correct = true then 1 else 0 end), count(*) "
                + "from student_exercise_attempts t join student_answers a on a.attempt_id = t.id join questions q on q.id = a.question_id "
                + "where t.assignment_id = ? and t.submitted_at is not null group by t.id, t.user_id, q.group_id order by t.id",
                (rs, i) -> new Row(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3), rs.getInt(4), rs.getInt(5)), assignmentId);
        Map<Long, Map<Long, Acc>> byUserAttempt = new HashMap<>(); // user -> attempt (theo id tăng dần) -> số liệu
        for (Row r : rows) {
            Acc acc = byUserAttempt.computeIfAbsent(r.user(), k -> new java.util.LinkedHashMap<>()).computeIfAbsent(r.attempt(), k -> new Acc());
            acc.correct += r.correct();
            acc.total += r.total();
            if (r.group() != null) {
                acc.parts.put(r.group(), new ForcedPass.Part(r.correct(), r.total()));
            }
        }
        Map<Long, ForcedProgress> out = new HashMap<>();
        byUserAttempt.forEach((user, attempts) -> {
            long latestId = 0;
            Acc latest = null;
            boolean ever = false;
            for (var e : attempts.entrySet()) {
                Acc acc = e.getValue();
                ever |= ForcedPass.passed(rule, acc.correct, acc.total, acc.parts);
                latestId = e.getKey();
                latest = acc;
            }
            out.put(user, new ForcedProgress(attempts.size(), latestId, latest.correct, latest.total,
                    ForcedPass.passed(rule, latest.correct, latest.total, latest.parts), ever));
        });
        return out;
    }

    private record Row(long attempt, long user, Long group, int correct, int total) {}

    private static final class Acc {
        int correct;
        int total;
        final Map<Long, ForcedPass.Part> parts = new HashMap<>();
    }

}
