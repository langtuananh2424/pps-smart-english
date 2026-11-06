package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.dto.WritingDtos.AnswerAccepted;
import com.pps.smartenglish.ai.dto.WritingDtos.DraftAccepted;
import com.pps.smartenglish.ai.dto.WritingDtos.RootQuestion;
import com.pps.smartenglish.ai.dto.WritingDtos.SessionCreated;
import com.pps.smartenglish.ai.dto.WritingDtos.SubmitAccepted;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.service.WritingQuestionSource.WritingQuestion;
import com.pps.smartenglish.commerce.service.EmailVerification;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.idempotency.IdempotencyService;
import com.pps.smartenglish.shared.idempotency.IdempotencyService.Outcome;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-AI-01: viết phát triển ý (Scaffolding) rồi nộp chấm. Chỉ bước nộp trừ lượt (Q-15). */
@Service
public class WritingService {

    /** Số câu hỏi đào sâu tối đa; đủ thì code tự đặt READY. Chưa đủ MIN thì bỏ qua `done` của model. */
    static final int MAX_FOLLOWUPS = 3;
    static final int MIN_FOLLOWUPS = 2;
    static final String ASSISTANT = "ASSISTANT";
    static final String STUDENT = "STUDENT";

    private final WritingSessionRepository sessions;
    private final AiJobRepository jobs;
    private final AiJobService aiJobs;
    private final AiQuotaService quota;
    private final AiGuard guard;
    private final WritingQuestionSource questions;
    private final EmailVerification emailVerification;
    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;
    private final Clock clock;

    WritingService(WritingSessionRepository sessions, AiJobRepository jobs, AiJobService aiJobs, AiQuotaService quota,
                   AiGuard guard, WritingQuestionSource questions, EmailVerification emailVerification,
                   IdempotencyService idempotency, ObjectMapper mapper, Clock clock) {
        this.sessions = sessions;
        this.jobs = jobs;
        this.aiJobs = aiJobs;
        this.quota = quota;
        this.guard = guard;
        this.questions = questions;
        this.emailVerification = emailVerification;
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Không gọi AI. */
    @Transactional
    public SessionCreated create(long userId, long questionId) {
        emailVerification.require(userId);
        WritingQuestion q = questions.find(questionId, userId);
        guard.rateLimit(userId);
        quota.requirePositive(userId);
        ArrayNode turns = mapper.createArrayNode().add(turn(ASSISTANT, q.prompt()));
        var s = sessions.save(new WritingSession(userId, q.id(), turns));
        return new SessionCreated(s.getId(), new RootQuestion(q.id(), q.prompt(), q.payload()));
    }

    @Transactional
    public AnswerAccepted answer(long userId, long sessionId, String text) {
        var s = load(userId, sessionId);
        if (!WritingSession.ACTIVE.equals(s.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "session_not_active", "Phiên viết không còn nhận câu trả lời");
        }
        guard.rateLimit(userId);
        quota.requirePositive(userId);
        requireIdle(s);
        s.setTurns(append(s.getTurns(), STUDENT, text));
        if (s.getStep() >= MAX_FOLLOWUPS) {
            s.setStatus(WritingSession.READY); // đủ câu hỏi đào sâu: không cần gọi AI nữa
            sessions.save(s);
            return new AnswerAccepted(null, WritingSession.READY);
        }
        guard.requireCapacity();
        sessions.save(s);
        WritingQuestion q = questions.find(s.getQuestionId(), userId);
        ObjectNode in = baseInput(s, q).put("asked", s.getStep());
        in.set("turns", s.getTurns());
        return new AnswerAccepted(aiJobs.enqueue(userId, AiJobKind.SCAFFOLD_FOLLOWUP, in, s.getId()).getId(), WritingSession.ACTIVE);
    }

    @Transactional
    public DraftAccepted draft(long userId, long sessionId) {
        var s = load(userId, sessionId);
        if (!WritingSession.READY.equals(s.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "session_not_ready", "Chưa đủ câu trả lời để tạo bản nháp");
        }
        guard.rateLimit(userId);
        quota.requirePositive(userId);
        requireIdle(s);
        guard.requireCapacity();
        WritingQuestion q = questions.find(s.getQuestionId(), userId);
        ObjectNode in = baseInput(s, q);
        in.set("turns", s.getTurns());
        return new DraftAccepted(aiJobs.enqueue(userId, AiJobKind.SCAFFOLD_DRAFT, in, s.getId()).getId());
    }

    /** Một transaction: trừ lượt + tạo job WRITING_GRADE + session SUBMITTED. Idempotency-Key chống trừ lượt hai lần. */
    public Outcome<SubmitAccepted> submit(long userId, long sessionId, String finalText, String idempotencyKey) {
        String hash = IdempotencyService.sha256(sessionId + ":" + finalText);
        return idempotency.execute(userId, idempotencyKey, hash, HttpStatus.ACCEPTED.value(), SubmitAccepted.class,
                () -> doSubmit(userId, sessionId, finalText));
    }

    private SubmitAccepted doSubmit(long userId, long sessionId, String finalText) {
        var s = load(userId, sessionId);
        if (WritingSession.SUBMITTED.equals(s.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "session_submitted", "Phiên viết này đã được nộp");
        }
        WritingQuestion q = questions.find(s.getQuestionId(), userId);
        ObjectNode in = baseInput(s, q);
        in.put("text", finalText);
        in.put("wordCount", WordStats.count(finalText));
        in.put("copiedWords", WordStats.copiedFromPrompt(finalText, q.task()));
        Long jobId = aiJobs.enqueueCharged(userId, AiJobKind.WRITING_GRADE, in, s.getId(), null)
                .orElseThrow(quota::exhausted);
        // chặn nộp đồng thời bằng hai khóa khác nhau: bên thua rollback, kéo theo cả lượt đã trừ
        if (sessions.claimSubmit(s.getId(), jobId, Instant.now(clock)) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "session_submitted", "Phiên viết này đã được nộp");
        }
        return new SubmitAccepted(jobId, "PENDING", quota.balance(userId));
    }

    private WritingSession load(long userId, long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "session_not_found", "Không tìm thấy phiên viết"));
    }

    private void requireIdle(WritingSession s) {
        if (jobs.existsScaffoldInFlight(s.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "session_busy", "AI đang xử lý bước trước, vui lòng chờ");
        }
    }

    private ObjectNode baseInput(WritingSession s, WritingQuestion q) {
        ObjectNode in = mapper.createObjectNode();
        in.put("sessionId", s.getId());
        in.put("courseTitle", q.courseTitle());
        in.put("task", q.task());
        if (q.requiredWords() != null) {
            in.put("requiredWords", q.requiredWords());
        }
        return in;
    }

    private ArrayNode append(JsonNode turns, String role, String text) {
        ArrayNode copy = ((ArrayNode) turns).deepCopy();
        copy.add(turn(role, text));
        return copy;
    }

    private ObjectNode turn(String role, String text) {
        return mapper.createObjectNode().put("role", role).put("text", text);
    }
}
