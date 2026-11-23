package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.GradeOverride;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeOverrideRepository extends JpaRepository<GradeOverride, Long> {

    List<GradeOverride> findByAttemptIdOrderByIdAsc(Long attemptId);

    List<GradeOverride> findByAttemptIdInOrderByIdAsc(Collection<Long> attemptIds);

    List<GradeOverride> findByWritingSessionIdOrderByIdAsc(Long id);

    List<GradeOverride> findByWritingSessionIdInOrderByIdAsc(Collection<Long> ids);

    List<GradeOverride> findBySpeakingSubmissionIdOrderByIdAsc(Long id);

    List<GradeOverride> findBySpeakingSubmissionIdInOrderByIdAsc(Collection<Long> ids);
}
