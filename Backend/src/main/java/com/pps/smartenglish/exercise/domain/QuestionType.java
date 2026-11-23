package com.pps.smartenglish.exercise.domain;

/** Giá trị questions.type (FR-EX-01, FR-EX-02). */
public enum QuestionType {
    CONNECTIVE_MCQ,
    CONNECTIVE_MATCH,
    CONNECTIVE_CLOZE,
    CONNECTIVE_REWRITE,
    GRAMMAR_IDENTIFY,
    GRAMMAR_SPOT_FIX,
    GRAMMAR_BUILD,
    GRAMMAR_REWRITE,
    WRITING,
    SPEAKING,
    // Kiểu chấm mới (FR-EX-04, FR-EX-05): dạng hiển thị khác nhau dùng chung các kiểu này.
    CHOICE_MULTI,
    TRUE_FALSE,
    FILL_TEXT,
    WORD_BANK,
    MATCH_OPTIONS;

    /** Chấm lại bài cũ chỉ cho dạng có đáp án: WRITING chấm theo phiên riêng, SPEAKING chưa có. */
    public boolean regradable() {
        return this != WRITING && this != SPEAKING;
    }

    /** Đáp án nằm ở question_choices (is_correct) thay vì answer_key. */
    public boolean usesChoices() {
        return this == CONNECTIVE_MCQ || this == CHOICE_MULTI;
    }
}
