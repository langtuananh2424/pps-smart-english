package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.AssignmentTarget;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentTargetRepository extends JpaRepository<AssignmentTarget, Long> {

    boolean existsByAssignmentId(Long assignmentId);

    List<AssignmentTarget> findByAssignmentIdIn(Collection<Long> assignmentIds);
}
