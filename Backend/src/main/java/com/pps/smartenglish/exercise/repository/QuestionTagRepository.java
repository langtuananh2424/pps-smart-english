package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTagRepository extends JpaRepository<QuestionTag, QuestionTag.Key> {

    List<QuestionTag> findByQuestionIdIn(Collection<Long> questionIds);

    void deleteByQuestionId(Long questionId);
}
