package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.speaking.SpeakingProperties;
import com.pps.smartenglish.ai.speaking.SpeakingSubmission;
import com.pps.smartenglish.ai.speaking.SpeakingSubmissionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-145, D-147: điểm câu Viết/Nói của một lượt làm bài. Viết: credit = scorePercent / 100. Nói: credit = band tổng / bandMax.
 * Câu nhiều phiên thì chỉ lấy phiên mới nhất (id lớn nhất); phiên cũ không bao giờ được tính.
 */
@Service
public class AttemptAiScores {

    public static final String REASON_AI_FAILED = "AI chấm lỗi";
    public static final String REASON_UNCLEAR = "bản ghi âm không rõ";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    /**
     * credit null = job chấm đang chạy (lượt chờ). Mọi trạng thái cuối khác không có điểm (chưa nộp chấm, hết lượt AI, lỗi,
     * không rõ) là credit 0; failReason chỉ có khi phiên đã nộp chấm mà không chấm được (đã hoàn lượt AI).
     */
    public record Linked(BigDecimal credit, String failReason, boolean refunded) {}

    private final WritingSessionRepository writing;
    private final SpeakingSubmissionRepository speaking;
    private final AiJobRepository jobs;
    private final AttemptCreditSink sink;
    private final SpeakingProperties speakingProps;

    AttemptAiScores(WritingSessionRepository writing, SpeakingSubmissionRepository speaking, AiJobRepository jobs,
                    AttemptCreditSink sink, SpeakingProperties speakingProps) {
        this.writing = writing;
        this.speaking = speaking;
        this.jobs = jobs;
        this.sink = sink;
        this.speakingProps = speakingProps;
    }

    /** Theo questionId; câu không có trong map là chưa mở phiên nào. */
    @Transactional(readOnly = true)
    public Map<Long, Linked> latestByQuestion(long attemptId) {
        Map<Long, Linked> out = new HashMap<>();
        for (var s : writing.findByAttemptIdOrderByIdAsc(attemptId)) { // id tăng dần: phiên sau ghi đè phiên trước
            if (sink.countsWriting(s.getQuestionId())) {
                out.put(s.getQuestionId(), writingLinked(s));
            }
        }
        for (var s : speaking.findByAttemptIdOrderByIdAsc(attemptId)) {
            out.put(s.getQuestionId(), speakingLinked(s));
        }
        return out;
    }

    /** Chạy trong transaction hoàn tất job chấm viết; bỏ qua nếu phiên không gắn lượt hoặc không còn là phiên mới nhất. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onWritingGraded(long sessionId, JsonNode output) {
        writing.findById(sessionId).filter(s -> s.getAttemptId() != null).filter(s -> sink.countsWriting(s.getQuestionId())).filter(this::latestWriting)
                .ifPresent(s -> sink.applyCredit(s.getAttemptId(), s.getQuestionId(), percentToCredit(output.path("scorePercent").asInt(0))));
    }

    /** Như onWritingGraded; kết quả UNCLEAR (không có band) không qua đây mà qua onSpeakingUnclear. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSpeakingGraded(long submissionId) {
        speaking.findById(submissionId).filter(s -> s.getAttemptId() != null).filter(this::latestSpeaking).ifPresent(s -> {
            BigDecimal credit = speakingCredit(s.getResult(), s.getStatus());
            if (credit != null) {
                sink.applyCredit(s.getAttemptId(), s.getQuestionId(), credit);
            }
        });
    }

    /** D-147: bản ghi không rõ lần cuối (đã chấm lại một lần, đã hoàn lượt): câu tính 0 kèm lý do. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSpeakingUnclear(long submissionId, boolean refunded) {
        speaking.findById(submissionId).filter(s -> s.getAttemptId() != null).filter(this::latestSpeaking)
                .ifPresent(s -> sink.applyFailure(s.getAttemptId(), s.getQuestionId(), REASON_UNCLEAR, refunded));
    }

    /** D-147: job chấm Viết/Nói lỗi hẳn (đã chấm lại một lần, đã hoàn lượt): câu tính 0 kèm lý do. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onGradeFailed(AiJob job) {
        switch (job.getKind()) {
            case WRITING_GRADE -> java.util.Optional.ofNullable(job.getSessionId()).flatMap(writing::findById).filter(s -> s.getAttemptId() != null).filter(s -> sink.countsWriting(s.getQuestionId())).filter(this::latestWriting)
                    .ifPresent(s -> sink.applyFailure(s.getAttemptId(), s.getQuestionId(), REASON_AI_FAILED, job.isCharged()));
            case SPEAKING_GRADE -> speaking.findByJobId(job.getId()).filter(s -> s.getAttemptId() != null).filter(this::latestSpeaking)
                    .ifPresent(s -> sink.applyFailure(s.getAttemptId(), s.getQuestionId(), REASON_AI_FAILED, job.isCharged()));
            default -> { }
        }
    }

    private boolean latestWriting(WritingSession s) {
        return writing.findFirstByAttemptIdAndQuestionIdOrderByIdDesc(s.getAttemptId(), s.getQuestionId())
                .map(l -> l.getId().equals(s.getId())).orElse(false);
    }

    private boolean latestSpeaking(SpeakingSubmission s) {
        return speaking.findFirstByAttemptIdAndQuestionIdOrderByIdDesc(s.getAttemptId(), s.getQuestionId())
                .map(l -> l.getId().equals(s.getId())).orElse(false);
    }

    private Linked writingLinked(WritingSession s) {
        if (!WritingSession.SUBMITTED.equals(s.getStatus())) {
            return new Linked(ZERO, null, false); // D-147: mở mà chưa nộp chấm thì tính 0, không chờ
        }
        var job = s.getGradeJobId() == null ? null : jobs.findById(s.getGradeJobId()).orElse(null);
        if (job == null) {
            return new Linked(ZERO, null, false);
        }
        return switch (job.getStatus()) {
            case AiJob.DONE -> new Linked(job.getOutput() == null ? ZERO : percentToCredit(job.getOutput().path("scorePercent").asInt(0)), null, false);
            case AiJob.FAILED -> new Linked(ZERO, REASON_AI_FAILED, job.isCharged());
            default -> new Linked(null, null, false);
        };
    }

    private Linked speakingLinked(SpeakingSubmission s) {
        return switch (s.getStatus()) {
            case SpeakingSubmission.GRADING -> new Linked(null, null, false);
            case SpeakingSubmission.GRADED -> {
                BigDecimal credit = speakingCredit(s.getResult(), s.getStatus());
                yield new Linked(credit == null ? ZERO : credit, null, false);
            }
            case SpeakingSubmission.UNCLEAR -> new Linked(ZERO, REASON_UNCLEAR, speakingRefunded(s));
            case SpeakingSubmission.FAILED -> new Linked(ZERO, REASON_AI_FAILED, speakingRefunded(s));
            default -> new Linked(ZERO, null, false); // AWAITING_UPLOAD, EXPIRED: chưa nộp chấm
        };
    }

    /** Bài giao không trừ lượt nên không có gì để hoàn. */
    private boolean speakingRefunded(SpeakingSubmission s) {
        return s.getJobId() != null && jobs.findById(s.getJobId()).map(AiJob::isCharged).orElse(false);
    }

    private BigDecimal speakingCredit(JsonNode result, String status) {
        if (!SpeakingSubmission.GRADED.equals(status) || result == null || !result.path("overall").isNumber()) {
            return null;
        }
        return clamp(BigDecimal.valueOf(result.path("overall").asDouble() / speakingProps.bandMax()));
    }

    private static BigDecimal percentToCredit(int percent) {
        return clamp(BigDecimal.valueOf(percent).divide(BigDecimal.valueOf(100)));
    }

    /** credit nằm trong 0..1, 4 chữ số thập phân như cột student_answers.credit. */
    private static BigDecimal clamp(BigDecimal v) {
        return v.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }
}
