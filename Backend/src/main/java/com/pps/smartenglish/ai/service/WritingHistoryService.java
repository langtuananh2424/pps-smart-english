package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.dto.WritingDtos.RootQuestion;
import com.pps.smartenglish.ai.dto.WritingDtos.SessionSummary;
import com.pps.smartenglish.ai.dto.WritingDtos.SessionView;
import com.pps.smartenglish.ai.dto.WritingDtos.TurnView;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-84: xem lại phiên viết và danh sách phiên của tôi. Luôn lọc theo chủ phiên. */
@Service
public class WritingHistoryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final WritingSessionRepository sessions;
    private final AiJobRepository jobs;
    private final WritingQuestionSource questions;
    private final AttemptResultGate gate;

    WritingHistoryService(WritingSessionRepository sessions, AiJobRepository jobs, WritingQuestionSource questions,
                          AttemptResultGate gate) {
        this.gate = gate;
        this.sessions = sessions;
        this.jobs = jobs;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public SessionView view(long userId, long sessionId) {
        return toView(sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "session_not_found", "Không tìm thấy phiên viết")), true);
    }

    /**
     * D-141: giáo viên xem bài nộp của học viên, cùng nội dung chủ phiên tự xem. Không lọc theo chủ phiên nên người gọi phải kiểm
     * quyền lớp và bài giao trước (AssignmentSubmissionReader).
     */
    @Transactional(readOnly = true)
    public SessionView viewForStaff(long sessionId) {
        var s = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "session_not_found", "Không tìm thấy phiên viết"));
        return toView(s, questions.findForStaff(s.getQuestionId()), false); // giáo viên chỉ xem bài đã nộp, không bị khóa điểm
    }

    private SessionView toView(WritingSession s, boolean owner) {
        // D-147: chủ phiên xem lại (chỉ đọc) được kể cả bộ EXAM sau khi nộp; ghi thì WritingService khóa 409. Kết quả theo AttemptResultGate
        return toView(s, questions.find(s.getQuestionId(), s.getUserId(), s.getAssignmentId() != null, s.getAttemptId() != null), owner);
    }

    private SessionView toView(WritingSession s, WritingQuestionSource.WritingQuestion q, boolean owner) {
        long userId = s.getUserId();
        List<TurnView> turns = new ArrayList<>();
        s.getTurns().forEach(t -> turns.add(new TurnView(
                WritingService.ASSISTANT.equals(t.path("role").asText()) ? "CAM" : "STUDENT", t.path("text").asText())));
        var inFlight = jobs.findScaffoldInFlightIds(s.getId());
        AiJob grade = s.getGradeJobId() == null ? null : jobs.findByIdAndUserId(s.getGradeJobId(), userId).orElse(null);
        return new SessionView(s.getId(), s.getStatus(), new RootQuestion(q.id(), q.prompt(), q.payload()), turns,
                inFlight.isEmpty() ? null : inFlight.get(0), s.getDraft(), finalText(grade), s.getGradeJobId(),
                grade != null && AiJob.DONE.equals(grade.getStatus()) && !(owner && gate.sealedSession(s.getUserId(), s.getQuestionId(), s.getAttemptId())) ? grade.getOutput() : null,
                s.getCreatedAt());
    }

    /** Session không lưu bài nộp riêng: lấy từ input của job chấm. */
    private static String finalText(AiJob grade) {
        return grade == null ? null : grade.getInput().path("text").asText(null);
    }

    @Transactional(readOnly = true)
    public CursorPage<SessionSummary> list(long userId, Long questionId, Long assignmentId, String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        long before = Cursors.decode(cursor);
        var page = PageRequest.of(0, size + 1);
        // assignmentId có: chỉ phiên của bài giao đó (bỏ qua questionId); không có: như cũ
        List<WritingSession> rows = assignmentId != null
                ? sessions.findByUserIdAndAssignmentIdAndIdLessThanOrderByIdDesc(userId, assignmentId, before, page)
                : questionId == null
                ? sessions.findByUserIdAndIdLessThanOrderByIdDesc(userId, before, page)
                : sessions.findByUserIdAndQuestionIdAndIdLessThanOrderByIdDesc(userId, questionId, before, page);
        boolean more = rows.size() > size;
        List<WritingSession> shown = more ? rows.subList(0, size) : rows;
        Map<Long, Integer> scores = scores(shown);
        Map<Long, Boolean> sealed = new HashMap<>(); // một lượt nhiều phiên: hỏi cổng một lần
        return new CursorPage<>(shown.stream().map(s -> new SessionSummary(s.getId(), s.getQuestionId(), s.getStatus(),
                sealed.computeIfAbsent(s.getAttemptId() != null ? s.getAttemptId() : -s.getQuestionId(),
                        k -> gate.sealedSession(userId, s.getQuestionId(), s.getAttemptId())) ? null : scores.get(s.getGradeJobId()),
                s.getCreatedAt(), s.getAssignmentId())).toList(),
                more ? Cursors.encode(shown.get(size - 1).getId()) : null);
    }

    private Map<Long, Integer> scores(List<WritingSession> shown) {
        var ids = shown.stream().map(WritingSession::getGradeJobId).filter(Objects::nonNull).toList();
        Map<Long, Integer> out = new HashMap<>();
        for (AiJob j : jobs.findAllById(ids)) {
            JsonNode n = AiJob.DONE.equals(j.getStatus()) ? j.getOutput().path("scorePercent") : null;
            if (n != null && n.isNumber()) {
                out.put(j.getId(), n.asInt());
            }
        }
        return out;
    }
}
