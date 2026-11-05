package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Topic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByLevelIdInOrderBySortOrderAscIdAsc(Collection<Long> levelIds);
}
