package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptResponse;
import java.util.List;

/** Chiến lược định hình kết quả sau nộp theo result_mode của lượt (FR-EX-10): FULL trả đầy đủ, COUNT_ONLY chỉ trả số câu đúng. */
interface ResultShaper {

    String mode();

    AttemptResponse shape(AttemptResponse full, StudentAttempt attempt, QuestionSet set, List<StudentAnswer> answers);
}
