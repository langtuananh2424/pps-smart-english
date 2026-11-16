package com.pps.smartenglish.ai.speaking;

import java.util.List;

/**
 * ai cần câu SPEAKING của exercise nhưng không phụ thuộc ngược vào exercise: exercise cài đặt interface này,
 * kiểm quyền vào bộ câu hỏi bằng đúng cơ chế bank hiện có.
 */
public interface SpeakingQuestionSource {

    /** Không tồn tại thì 404, không phải SPEAKING thì 400, không có quyền vào bộ câu hỏi thì 403. */
    SpeakingQuestion find(long questionId, long userId);

    /** maxSeconds đã áp mặc định theo part (D-43). cueCard rỗng nếu không phải part 2. */
    record SpeakingQuestion(long id, int part, String prompt, List<String> cueCard, int maxSeconds, String courseTitle, Long courseId) {}
}
