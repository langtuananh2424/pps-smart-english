package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.dto.RewriteGrades;
import java.util.List;

/**
 * Nơi nhận kết quả chấm viết lại của AI; exercise cài đặt để ghi is_correct, student_answer_grading và điểm lượt làm bài.
 * Gọi trong transaction hoàn tất job, nên ghi kết quả và chuyển job sang DONE cùng thành công hoặc cùng không.
 */
public interface RewriteResultSink {

    /** items[].id là questionId của câu trả lời trong lượt làm bài attemptId. */
    void apply(long attemptId, List<RewriteGrades.Item> items);
}
