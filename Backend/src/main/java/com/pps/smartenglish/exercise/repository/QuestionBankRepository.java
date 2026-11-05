package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.BankInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    @Query("""
            select new com.pps.smartenglish.exercise.dto.BankInfo(b.id, b.title, b.lessonId, b.trial, t.freeTrial)
            from QuestionBank b, Topic t, CourseLevel lv, Course c
            where b.id = :id and t.id = b.topicId and lv.id = t.levelId and c.id = lv.courseId and c.status = 'PUBLISHED'
            """)
    Optional<BankInfo> findInfo(@Param("id") Long id);
}
