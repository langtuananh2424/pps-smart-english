package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiTask;

/**
 * ai cần câu hỏi viết của exercise nhưng không phụ thuộc ngược vào exercise: exercise cài đặt interface này,
 * kiểm quyền vào bộ câu hỏi bằng đúng cơ chế bank hiện có (BankAccessPolicy).
 */
public interface WritingQuestionSource {

    /** Câu hỏi không tồn tại thì 404, không phải dạng WRITING thì 400, không có quyền vào bộ câu hỏi thì 403. */
    default WritingQuestion find(long questionId, long userId) {
        return find(questionId, userId, false);
    }

    /** inAssignment: người gọi đã kiểm là người nhận bài giao đúng câu này, nên bỏ khóa unit tuần tự cho riêng câu đó (FR-ASG-01). */
    default WritingQuestion find(long questionId, long userId, boolean inAssignment) {
        return find(questionId, userId, inAssignment, false);
    }

    /**
     * inAttempt: phiên nằm trong lượt làm bài đã được kiểm (AttemptLinks), nên câu của bộ thi khóa cũng làm được (D-82, D-145).
     * Không có lượt thì bộ thi vẫn bị chặn.
     */
    WritingQuestion find(long questionId, long userId, boolean inAssignment, boolean inAttempt);

    /** Mở phiên MỚI: như find nhưng câu đã lưu trữ (D-138) bị 400 question_archived; phiên cũ vẫn dùng find để xem lại. */
    WritingQuestion findForNewSession(long questionId, long userId, boolean inAssignment, boolean inAttempt);

    /** D-141: như find nhưng không kiểm quyền vào bộ câu hỏi; chỉ dùng khi người gọi đã kiểm quyền đọc bài nộp (giáo viên của lớp). */
    WritingQuestion findForStaff(long questionId);

    /**
     * prompt: câu hỏi gốc do Admin soạn. task: đề bài chấm (payload.task, mặc định là prompt).
     * requiredWords: số từ yêu cầu (payload.requiredWords), null nếu đề không ghi.
     * courseId và aiTask (WRITING_TASK1 khi payload.ieltsTask = TASK1, còn lại TASK2) để tính lượt AI (D-32, D-47).
     */
    record WritingQuestion(long id, String prompt, String task, Integer requiredWords, String courseTitle, JsonNode payload,
                           Long courseId, AiTask aiTask) {}
}
