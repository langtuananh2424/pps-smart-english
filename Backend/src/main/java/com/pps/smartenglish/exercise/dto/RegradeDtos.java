package com.pps.smartenglish.exercise.dto;

import com.pps.smartenglish.exercise.domain.RegradeRun;
import java.time.Instant;
import java.util.Map;

/** DTO chấm lại bài cũ (chỉ ADMIN). Điểm cũ chỉ xuất hiện ở đây, không bao giờ ở response của học viên. */
public final class RegradeDtos {

    private RegradeDtos() {}

    public record StartRequest(@jakarta.validation.constraints.NotNull Integer answerVersion) {}

    public record Preview(long questionId, int answerVersion, long affectedAnswers, long affectedAttempts, long maxAiCalls,
                          boolean regradeRequired) {}

    /** itemCounts (số item theo trạng thái) chỉ có ở GET một run; danh sách để null. */
    public record RunView(Long id, Long questionId, int questionVersion, String status, Long requestedBy, int total, int changed,
                          int failed, Instant createdAt, Instant finishedAt, Map<String, Long> itemCounts) {

        public static RunView of(RegradeRun r, Map<String, Long> itemCounts) {
            return new RunView(r.getId(), r.getQuestionId(), r.getQuestionVersion(), r.getStatus(), r.getRequestedBy(), r.getTotal(),
                    r.getChanged(), r.getFailed(), r.getCreatedAt(), r.getFinishedAt(), itemCounts);
        }
    }

    /** created = true khi vừa tạo hoặc mở lại run (HTTP 202); false khi trả lại run đã có (HTTP 200). */
    public record Started(RunView run, boolean created) {}
}
