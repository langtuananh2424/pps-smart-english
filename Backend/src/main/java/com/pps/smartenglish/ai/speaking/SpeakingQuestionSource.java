package com.pps.smartenglish.ai.speaking;

import java.util.List;

/**
 * ai cần câu SPEAKING của exercise nhưng không phụ thuộc ngược vào exercise: exercise cài đặt interface này,
 * kiểm quyền vào bộ câu hỏi bằng đúng cơ chế bank hiện có.
 */
public interface SpeakingQuestionSource {

    /** Không tồn tại thì 404, không phải SPEAKING thì 400, không có quyền vào bộ câu hỏi thì 403. */
    default SpeakingQuestion find(long questionId, long userId) {
        return find(questionId, userId, false);
    }

    /** inAssignment: người gọi đã kiểm là người nhận bài giao đúng câu này, nên bỏ khóa unit tuần tự cho riêng câu đó (FR-ASG-01). */
    default SpeakingQuestion find(long questionId, long userId, boolean inAssignment) {
        return find(questionId, userId, inAssignment, false);
    }

    /** inAttempt: bản nộp nằm trong lượt làm bài đã được kiểm (AttemptLinks), nên câu của bộ thi khóa cũng nói được (D-82, D-145). */
    SpeakingQuestion find(long questionId, long userId, boolean inAssignment, boolean inAttempt);

    /** Mở phiên MỚI: như find nhưng câu đã lưu trữ (D-138) bị 400 question_archived. */
    SpeakingQuestion findForNewSession(long questionId, long userId, boolean inAssignment, boolean inAttempt);

    /**
     * maxSeconds đã áp mặc định theo part (D-43). cueCard rỗng nếu không phải part 2. Video phản xạ (D-158): maxRecordings > 0 là
     * số lần ghi tối đa mỗi câu mỗi lượt (0 = không giới hạn); writingFirst = phải qua bước Viết trước (VIDEO_SHORT).
     */
    record SpeakingQuestion(long id, int part, String prompt, List<String> cueCard, int maxSeconds, String courseTitle, Long courseId,
                            int maxRecordings, boolean writingFirst) {}
}
