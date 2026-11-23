package com.pps.smartenglish.classroom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.speaking.SpeakingSubmission;
import com.pps.smartenglish.ai.speaking.SpeakingSubmissionRepository;
import com.pps.smartenglish.classroom.domain.Assignment;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * FR-ASG-03, FR-ASG-04: bài nộp của bài giao câu Viết/Nói (D-130), đọc từ phiên viết và lượt nói. Mỗi người một bài nộp; có nhiều
 * thì lấy bài mới nhất. Phiên viết có job chấm lỗi hẳn và lượt nói UNCLEAR, FAILED không tính là đã nộp (học viên nộp lại được).
 */
@Component
class AiAssignmentSubmissions {

    static final int WRITING_MAX = 100; // scorePercent của bài Viết
    static final int SPEAKING_MAX = 9; // band tổng của bài Nói

    private final WritingSessionRepository writing;
    private final SpeakingSubmissionRepository speaking;
    private final AiJobRepository jobs;

    AiAssignmentSubmissions(WritingSessionRepository writing, SpeakingSubmissionRepository speaking, AiJobRepository jobs) {
        this.writing = writing;
        this.speaking = speaking;
        this.jobs = jobs;
    }

    /** Bài nộp mới nhất của từng người nhận, theo userId. */
    Map<Long, AssignmentSubmission> latestByUser(Assignment a) {
        return latest(a, a.getQuestionType().equals(Assignment.WRITING) ? writingOf(writing.findByAssignmentIdAndStatus(a.getId(), WritingSession.SUBMITTED))
                : speakingOf(speaking.findByAssignmentIdAndStatusIn(a.getId(), SpeakingSubmission.SUBMITTED_OK)));
    }

    Optional<AssignmentSubmission> latestOf(Assignment a, long userId) {
        var subs = a.getQuestionType().equals(Assignment.WRITING)
                ? writingOf(writing.findByAssignmentIdAndUserIdAndStatus(a.getId(), userId, WritingSession.SUBMITTED))
                : speakingOf(speaking.findByAssignmentIdAndUserIdAndStatusIn(a.getId(), userId, SpeakingSubmission.SUBMITTED_OK));
        return Optional.ofNullable(latest(a, subs).get(userId));
    }

    /** Bài nộp có id này của bài giao, nếu đã nộp. */
    Optional<AssignmentSubmission> byId(Assignment a, long id) {
        return latestByUser(a).values().stream().filter(s -> s.id() == id).findFirst();
    }

    private static Map<Long, AssignmentSubmission> latest(Assignment a, List<AssignmentSubmission> subs) {
        Map<Long, AssignmentSubmission> out = new HashMap<>();
        subs.forEach(s -> out.merge(s.userId(), s, (x, y) -> x.id() > y.id() ? x : y));
        return out;
    }

    private List<AssignmentSubmission> writingOf(List<WritingSession> sessions) {
        Map<Long, AiJob> grade = new HashMap<>();
        jobs.findAllById(sessions.stream().map(WritingSession::getGradeJobId).filter(Objects::nonNull).toList())
                .forEach(j -> grade.put(j.getId(), j));
        return sessions.stream().filter(s -> {
            var j = grade.get(s.getGradeJobId());
            return j == null || !AiJob.FAILED.equals(j.getStatus());
        }).map(s -> {
            var j = grade.get(s.getGradeJobId());
            JsonNode n = j != null && AiJob.DONE.equals(j.getStatus()) ? j.getOutput().path("scorePercent") : null;
            return new AssignmentSubmission(s.getUserId(), s.getId(), s.getSubmittedAt(),
                    n != null && n.isNumber() ? BigDecimal.valueOf(n.asInt()) : null, WRITING_MAX);
        }).toList();
    }

    private List<AssignmentSubmission> speakingOf(List<SpeakingSubmission> rows) {
        return rows.stream().map(s -> {
            JsonNode n = s.getResult() == null ? null : s.getResult().path("overall");
            return new AssignmentSubmission(s.getUserId(), s.getId(), s.getSubmittedAt(),
                    n != null && n.isNumber() ? BigDecimal.valueOf(n.asDouble()) : null, SPEAKING_MAX);
        }).toList();
    }
}
