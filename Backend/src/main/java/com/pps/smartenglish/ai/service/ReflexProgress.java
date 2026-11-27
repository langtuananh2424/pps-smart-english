package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.config.VideoReflexProperties;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.WritingSession;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.speaking.SpeakingQuestionSource;
import com.pps.smartenglish.ai.speaking.SpeakingQuestionSource.SpeakingQuestion;
import com.pps.smartenglish.ai.speaking.SpeakingSubmission;
import com.pps.smartenglish.ai.speaking.SpeakingSubmissionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-158 (FR-AI-03): tiến độ một câu Video phản xạ trong lượt. Máy chủ là nơi quyết định bước Nói của VIDEO_SHORT đã mở chưa và
 * còn mấy lần ghi; web chỉ hiện. Không lộ điểm hay bản sửa (D-147): chỉ cờ đạt và số lần.
 */
@Service
public class ReflexProgress {

    /** Mỗi câu chỉ tính bản ghi đã nộp chấm; bản chấm lỗi hệ thống (FAILED) đã hoàn lượt nên không tốn lần ghi. */
    private static final List<String> COUNTED = List.of(SpeakingSubmission.GRADING, SpeakingSubmission.GRADED, SpeakingSubmission.UNCLEAR);

    public record View(boolean writingFirst, int writeUsed, int writeMax, boolean writePassed, boolean writePending, boolean speakOpen,
                       int recordUsed, int recordMax, int maxSeconds) {}

    private final AttemptLinks attempts;
    private final SpeakingQuestionSource questions;
    private final WritingSessionRepository writing;
    private final SpeakingSubmissionRepository speaking;
    private final AiJobRepository jobs;
    private final VideoReflexProperties props;

    ReflexProgress(AttemptLinks attempts, SpeakingQuestionSource questions, WritingSessionRepository writing,
                   SpeakingSubmissionRepository speaking, AiJobRepository jobs, VideoReflexProperties props) {
        this.attempts = attempts;
        this.questions = questions;
        this.writing = writing;
        this.speaking = speaking;
        this.jobs = jobs;
        this.props = props;
    }

    /** Lượt người khác thì 404; câu không phải Video phản xạ thì 400. */
    @Transactional(readOnly = true)
    public View view(long userId, long attemptId, long questionId) {
        attempts.requireOwned(userId, attemptId);
        SpeakingQuestion q = questions.find(questionId, userId, false, true);
        if (q.maxRecordings() == 0) {
            throw ApiException.validation("questionId", "Không phải câu Video phản xạ");
        }
        var w = writeState(attemptId, questionId);
        int used = recordings(attemptId, questionId);
        return new View(q.writingFirst(), w.used, props.maxWriteSubmits(), w.passed, w.pending, speakOpen(q, w), used, q.maxRecordings(), q.maxSeconds());
    }

    /** Gọi lúc xin quyền tải bản ghi: bước Viết (nếu có) phải xong và còn lần ghi. */
    @Transactional(readOnly = true)
    public void requireSpeakable(long attemptId, SpeakingQuestion q) {
        if (q.writingFirst() && !speakOpen(q, writeState(attemptId, q.id()))) {
            throw new ApiException(HttpStatus.CONFLICT, "writing_step_required", "Hãy hoàn thành bước Viết trước khi nói");
        }
        requireRecordingSlot(attemptId, q);
    }

    /** Chặn lần ghi thứ maxRecordings + 1. Gọi lại trong transaction nộp bản ghi (đã khóa dòng lượt) nên không vượt khi gửi đồng thời. */
    @Transactional(readOnly = true)
    public void requireRecordingSlot(long attemptId, SpeakingQuestion q) {
        if (q.maxRecordings() > 0 && recordings(attemptId, q.id()) >= q.maxRecordings()) {
            throw new ApiException(HttpStatus.CONFLICT, "recording_limit_reached", "Đã dùng hết " + q.maxRecordings() + " lần ghi âm của câu này");
        }
    }

    /** Gọi trong transaction nộp bài viết (đã khóa dòng lượt): chặn lần nộp thứ maxSubmits + 1. */
    @Transactional(readOnly = true)
    public void requireWriteSlot(long attemptId, long questionId, int maxSubmits) {
        if (maxSubmits > 0 && writeState(attemptId, questionId).used >= maxSubmits) {
            throw new ApiException(HttpStatus.CONFLICT, "writing_limit_reached", "Đã dùng hết " + maxSubmits + " lần nộp bài viết của câu này");
        }
    }

    private int recordings(long attemptId, long questionId) {
        return (int) speaking.countByAttemptIdAndQuestionIdAndStatusIn(attemptId, questionId, COUNTED);
    }

    private boolean speakOpen(SpeakingQuestion q, WriteState w) {
        return !q.writingFirst() || w.passed || (w.used >= props.maxWriteSubmits() && !w.pending);
    }

    private record WriteState(int used, boolean passed, boolean pending) {}

    private WriteState writeState(long attemptId, long questionId) {
        int used = 0;
        boolean passed = false;
        boolean pending = false;
        for (var s : writing.findByAttemptIdOrderByIdAsc(attemptId)) {
            if (s.getQuestionId() != questionId || !WritingSession.SUBMITTED.equals(s.getStatus())) {
                continue;
            }
            var job = s.getGradeJobId() == null ? null : jobs.findById(s.getGradeJobId()).orElse(null);
            if (job == null || AiJob.FAILED.equals(job.getStatus())) {
                continue; // chấm lỗi hệ thống đã hoàn lượt: không tính lần nộp
            }
            used++;
            if (AiJob.DONE.equals(job.getStatus())) {
                passed |= job.getOutput() != null && job.getOutput().path("scorePercent").asInt(0) >= props.writePassPct();
            } else {
                pending = true;
            }
        }
        return new WriteState(used, passed, pending);
    }
}
