package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.RegradeRun;
import com.pps.smartenglish.exercise.repository.RegradeItemRepository;
import com.pps.smartenglish.exercise.repository.RegradeRunRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tạo run và toàn bộ item trong một transaction (run + một câu INSERT ... SELECT). Trùng (câu, phiên bản) thì UNIQUE chặn. */
@Component
class RegradeRunCreator {

    private final RegradeRunRepository runs;
    private final RegradeItemRepository items;
    private final Clock clock;

    RegradeRunCreator(RegradeRunRepository runs, RegradeItemRepository items, Clock clock) {
        this.runs = runs;
        this.items = items;
        this.clock = clock;
    }

    @Transactional
    public RegradeRun create(Question q, long adminId) {
        RegradeRun run = runs.saveAndFlush(new RegradeRun(q.getId(), q.getAnswerVersion(), adminId, Instant.now(clock)));
        int total = items.insertFromAnswers(run.getId(), q.getId());
        if (total == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no_submissions", "Câu hỏi chưa có bài nộp nào để chấm lại");
        }
        runs.setTotal(run.getId(), total);
        return runs.findById(run.getId()).orElseThrow();
    }
}
