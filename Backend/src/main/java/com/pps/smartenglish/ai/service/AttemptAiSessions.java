package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.speaking.SpeakingSubmission;
import com.pps.smartenglish.ai.speaking.SpeakingSubmissionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-145: web dựng lại thẻ câu Viết/Nói của một lượt từ máy chủ (không dựa vào bộ nhớ trình duyệt), để không mở phiên trống
 * đè phiên đã chấm. Chỉ trả id và trạng thái, không điểm, không bản sửa: điểm chỉ lộ sau khi nộp lượt.
 */
@Service
public class AttemptAiSessions {

    public enum State { NOT_SUBMITTED, GRADING, GRADED, FAILED }

    /** kind: WRITING | SPEAKING. id: sessionId (Viết) hoặc submissionId (Nói) của phiên mới nhất của câu. */
    public record Item(long questionId, String kind, long id, State status) {}

    private final AttemptLinks attempts;
    private final WritingSessionRepository writing;
    private final SpeakingSubmissionRepository speaking;
    private final AiJobRepository jobs;
    private final AttemptCreditSink sink;

    AttemptAiSessions(AttemptLinks attempts, WritingSessionRepository writing, SpeakingSubmissionRepository speaking, AiJobRepository jobs,
                      AttemptCreditSink sink) {
        this.sink = sink;
        this.attempts = attempts;
        this.writing = writing;
        this.speaking = speaking;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public List<Item> list(long userId, long attemptId) {
        attempts.requireOwned(userId, attemptId); // lượt người khác coi như không có
        Map<Long, Item> latest = new LinkedHashMap<>();
        for (var s : writing.findByAttemptIdOrderByIdAsc(attemptId)) { // id tăng dần: phiên sau ghi đè phiên trước
            if (!sink.countsWriting(s.getQuestionId())) {
                continue; // bước Viết của VIDEO_SHORT xem ở ReflexProgress, không lẫn với bản ghi Nói của câu
            }
            latest.put(s.getQuestionId(), new Item(s.getQuestionId(), "WRITING", s.getId(), writingState(s)));
        }
        for (var s : speaking.findByAttemptIdOrderByIdAsc(attemptId)) {
            latest.put(s.getQuestionId(), new Item(s.getQuestionId(), "SPEAKING", s.getId(), speakingState(s.getStatus())));
        }
        return new ArrayList<>(latest.values());
    }

    private State writingState(WritingSession s) {
        if (!WritingSession.SUBMITTED.equals(s.getStatus())) {
            return State.NOT_SUBMITTED;
        }
        var job = s.getGradeJobId() == null ? null : jobs.findById(s.getGradeJobId()).orElse(null);
        if (job == null) {
            return State.GRADING;
        }
        return AiJob.DONE.equals(job.getStatus()) ? State.GRADED : AiJob.FAILED.equals(job.getStatus()) ? State.FAILED : State.GRADING;
    }

    /** UNCLEAR, EXPIRED, FAILED đều là chưa có điểm và cho làm lại: gộp vào FAILED. */
    private static State speakingState(String status) {
        return switch (status) {
            case SpeakingSubmission.AWAITING_UPLOAD -> State.NOT_SUBMITTED;
            case SpeakingSubmission.GRADING -> State.GRADING;
            case SpeakingSubmission.GRADED -> State.GRADED;
            default -> State.FAILED;
        };
    }
}
