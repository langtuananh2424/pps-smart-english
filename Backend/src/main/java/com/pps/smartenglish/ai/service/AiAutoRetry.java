package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-147: job chấm Viết/Nói của lượt đã nộp mà lỗi hẳn (hoặc Nói UNCLEAR) được chấm lại tự động đúng một lần. Tái dùng hàng đợi:
 * job quay về PENDING với cờ auto_retried; lần lỗi thứ hai đi đường FAILED cũ (hoàn lượt) và câu tính 0.
 */
@Component
public class AiAutoRetry {

    private static final Set<AiJobKind> KINDS = Set.of(AiJobKind.WRITING_GRADE, AiJobKind.SPEAKING_GRADE, AiJobKind.REWRITE_GRADE);

    private final AttemptLinks attempts;

    AiAutoRetry(AttemptLinks attempts) {
        this.attempts = attempts;
    }

    /** Lượt còn mở thì không: học viên tự làm lại được; chỉ lượt đã nộp mới không còn ai nộp lại. */
    public boolean applies(AiJob job) {
        return job.getAttemptId() != null && job.getRegradeItemId() == null && !job.isAutoRetried()
                && KINDS.contains(job.getKind()) && !attempts.isOpen(job.getAttemptId());
    }
}
