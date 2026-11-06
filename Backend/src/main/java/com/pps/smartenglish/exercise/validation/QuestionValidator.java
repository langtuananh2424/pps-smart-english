package com.pps.smartenglish.exercise.validation;

import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;

/**
 * Strategy kiểm tra dữ liệu soạn của một dạng câu hỏi, khớp đúng định dạng mà grader (exercise.grading) và RewriteRules đọc.
 * payload được trả nguyên văn cho học viên nên mỗi dạng chỉ nhận đúng các trường của nó (whitelist), không có trường đáp án.
 */
public interface QuestionValidator {

    QuestionType type();

    /** Ném ApiException.validation (400) nêu rõ trường sai. payload/answerKey rỗng đã được chuẩn hóa về null. */
    void validate(QuestionDraft draft, Set<String> grammarCodes);
}
