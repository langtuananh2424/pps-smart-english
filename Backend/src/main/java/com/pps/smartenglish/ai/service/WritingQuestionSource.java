package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiTask;

/**
 * ai cần câu hỏi viết của exercise nhưng không phụ thuộc ngược vào exercise: exercise cài đặt interface này,
 * kiểm quyền vào bộ câu hỏi bằng đúng cơ chế bank hiện có (BankAccessPolicy).
 */
public interface WritingQuestionSource {

    /** Câu hỏi không tồn tại thì 404, không phải dạng WRITING thì 400, không có quyền vào bộ câu hỏi thì 403. */
    WritingQuestion find(long questionId, long userId);

    /**
     * prompt: câu hỏi gốc do Admin soạn. task: đề bài chấm (payload.task, mặc định là prompt).
     * requiredWords: số từ yêu cầu (payload.requiredWords), null nếu đề không ghi.
     * courseId và aiTask (WRITING_TASK1 khi payload.ieltsTask = TASK1, còn lại TASK2) để tính lượt AI (D-32, D-47).
     */
    record WritingQuestion(long id, String prompt, String task, Integer requiredWords, String courseTitle, JsonNode payload,
                           Long courseId, AiTask aiTask) {}
}
