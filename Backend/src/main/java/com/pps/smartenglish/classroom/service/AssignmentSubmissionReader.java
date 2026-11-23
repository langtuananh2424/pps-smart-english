package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.ai.service.WritingHistoryService;
import com.pps.smartenglish.ai.speaking.SpeakingService;
import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AiSubmissionDetail;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-03, D-141, NFR-SEC-05: giáo viên đọc bài viết và nghe ghi âm của bài giao Viết/Nói khi chấm. Chỉ bài nộp mới nhất của
 * từng học viên cho đúng bài giao (AiAssignmentSubmissions.byId, cùng cổng với sửa điểm) mới mở được, kể cả khi học viên đã rút
 * hoặc bài giao đã hủy; phiên đang làm dở, bài nộp của bài giao khác hay phiên luyện tập đều 404.
 */
@Service
public class AssignmentSubmissionReader {

    private final ClassAccess access;
    private final AssignmentService assignments;
    private final AiAssignmentSubmissions submissions;
    private final WritingHistoryService writing;
    private final SpeakingService speaking;

    AssignmentSubmissionReader(ClassAccess access, AssignmentService assignments, AiAssignmentSubmissions submissions,
                               WritingHistoryService writing, SpeakingService speaking) {
        this.access = access;
        this.assignments = assignments;
        this.submissions = submissions;
        this.writing = writing;
        this.speaking = speaking;
    }

    @Transactional(readOnly = true)
    public AiSubmissionDetail read(long callerId, long classId, long assignmentId, long submissionId) {
        access.requireStaff(callerId, classId);
        Assignment a = assignments.require(classId, assignmentId);
        if (!a.isAiQuestion()) { // bài giao cả bộ câu hỏi không thuộc luồng này
            throw notFound();
        }
        var sub = submissions.byId(a, submissionId).orElseThrow(AssignmentSubmissionReader::notFound);
        if (Assignment.WRITING.equals(a.getQuestionType())) {
            return new AiSubmissionDetail(assignmentId, sub.userId(), a.getQuestionType(), sub.submittedAt(),
                    writing.viewForStaff(sub.id()), null, null);
        }
        var view = speaking.viewForStaff(sub.id());
        return new AiSubmissionDetail(assignmentId, sub.userId(), a.getQuestionType(), sub.submittedAt(), null,
                view.submission(), view.audioDeleted());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "submission_not_found", "Không tìm thấy bài nộp của bài này");
    }
}
