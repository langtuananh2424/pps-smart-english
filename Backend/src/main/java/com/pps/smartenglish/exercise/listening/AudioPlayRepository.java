package com.pps.smartenglish.exercise.listening;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioPlayRepository extends JpaRepository<AudioPlay, Long> {

    /** Lượt nghe mới nhất của part trong lượt làm; playNo cũng là số lượt đã cấp. */
    Optional<AudioPlay> findFirstByAttemptIdAndGroupIdOrderByPlayNoDesc(Long attemptId, Long groupId);
}
