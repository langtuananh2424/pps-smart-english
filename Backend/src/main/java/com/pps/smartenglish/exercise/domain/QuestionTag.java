package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Thẻ yếu tố ngữ pháp gắn vào câu hỏi (question_grammar_tags). */
@Entity
@Table(name = "question_grammar_tags")
@IdClass(QuestionTag.Key.class)
public class QuestionTag {

    public record Key(Long questionId, Long grammarElementId) implements Serializable {}

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Id
    @Column(name = "grammar_element_id")
    private Long grammarElementId;

    protected QuestionTag() {}

    public QuestionTag(Long questionId, Long grammarElementId) {
        this.questionId = questionId;
        this.grammarElementId = grammarElementId;
    }

    public Long getQuestionId() { return questionId; }
    public Long getGrammarElementId() { return grammarElementId; }
}
