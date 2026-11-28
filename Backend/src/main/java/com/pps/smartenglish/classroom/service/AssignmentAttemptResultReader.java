package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.exercise.dto.AttemptReview;
import com.pps.smartenglish.exercise.service.AttemptReviewService;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-03, FR-ASG-04, D-174, NFR-SEC-05: giáo viên, trợ giảng đọc kết quả từng câu của một lượt nộp bài giao bộ câu, AttemptReview
 * (kết quả như học viên nhận, đề và câu trả lời đã lưu). Chỉ lượt đã nộp của đúng bài giao (kể cả học viên đã rời lớp); còn lại 404, kể cả bài đã hủy vẫn đọc được.
 */
@Service
public class AssignmentAttemptResultReader {

    private final ClassAccess access;
    private final AssignmentService assignments;
    private final AssignmentRepository repo;
    private final AttemptReviewService reviews;

    AssignmentAttemptResultReader(ClassAccess access, AssignmentService assignments, AssignmentRepository repo, AttemptReviewService reviews) {
        this.access = access;
        this.assignments = assignments;
        this.repo = repo;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public AttemptReview read(long callerId, long classId, long assignmentId, long attemptId) {
        access.requireStaff(callerId, classId);
        Assignment a = assignments.require(classId, assignmentId);
        if (a.isAiQuestion()) { // bài Viết/Nói đọc qua AssignmentSubmissionReader
            throw notFound();
        }
        var row = repo.submittedAttempt(attemptId, assignmentId).stream().findFirst().orElseThrow(AssignmentAttemptResultReader::notFound);
        // lượt chỉ có assignment_id khi bắt đầu qua bài giao; không đòi còn là thành viên đang học (như grade/grades)
        return reviews.review(((Number) row[1]).longValue(), attemptId);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt đã nộp của bài này");
    }
}
