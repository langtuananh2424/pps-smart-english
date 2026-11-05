package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "question_banks")
public class QuestionBank {

    @Id
    private Long id;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "lesson_id")
    private Long lessonId;

    private String title;

    @Column(name = "is_trial")
    private boolean trial;

    protected QuestionBank() {}
}
