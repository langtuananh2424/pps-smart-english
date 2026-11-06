package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ghi kết quả scaffold vào session; chạy trong transaction hoàn tất job (MANDATORY: không tự mở transaction riêng). */
@Component
public class WritingSessionUpdater {

    private final WritingSessionRepository sessions;
    private final ObjectMapper mapper;

    WritingSessionUpdater(WritingSessionRepository sessions, ObjectMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyFollowup(long sessionId, boolean done, String question) {
        sessions.findById(sessionId).filter(s -> WritingSession.ACTIVE.equals(s.getStatus())).ifPresent(s -> {
            if (done) {
                s.setStatus(WritingSession.READY);
            } else {
                ArrayNode turns = ((ArrayNode) s.getTurns()).deepCopy();
                turns.add(mapper.createObjectNode().put("role", WritingService.ASSISTANT).put("text", question));
                s.setTurns(turns);
                s.setStep(s.getStep() + 1);
            }
            sessions.save(s);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyDraft(long sessionId, String draft) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setDraft(draft);
            sessions.save(s);
        });
    }
}
