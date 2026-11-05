package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "question_choices")
public class QuestionChoice {

    @Id
    private Long id;

    @Column(name = "question_id")
    private Long questionId;

    private String label;
    private String content;

    @Column(name = "is_correct")
    private boolean correct;

    private String explanation;

    protected QuestionChoice() {}

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public String getLabel() { return label; }
    public String getContent() { return content; }
    public boolean isCorrect() { return correct; }
}
