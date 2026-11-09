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
    SPEAKING;

    /** Chấm lại bài cũ chỉ cho dạng có đáp án: WRITING chấm theo phiên riêng, SPEAKING chưa có. */
    public boolean regradable() {
        return this != WRITING && this != SPEAKING;
    }
}
