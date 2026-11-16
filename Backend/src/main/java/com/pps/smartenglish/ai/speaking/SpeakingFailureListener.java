package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.shared.event.AiJobFailed;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Job chấm nói lỗi hẳn (đã hoàn lượt) thì lượt nói chuyển FAILED để học viên thấy và thu lại. */
@Component
class SpeakingFailureListener {

    private static final Logger log = LoggerFactory.getLogger(SpeakingFailureListener.class);

    private final SpeakingSubmissionRepository submissions;
    private final Clock clock;

    SpeakingFailureListener(SpeakingSubmissionRepository submissions, Clock clock) {
        this.submissions = submissions;
        this.clock = clock;
    }

    @EventListener
    void onFailed(AiJobFailed e) {
        if (!AiJobKind.SPEAKING_GRADE.name().equals(e.kind())) {
            return;
        }
        try {
            submissions.failByJob(e.jobId(), Instant.now(clock));
        } catch (RuntimeException ex) {
            log.warn("Không đánh dấu FAILED cho lượt nói của job {}: {}", e.jobId(), ex.toString());
        }
    }
}
