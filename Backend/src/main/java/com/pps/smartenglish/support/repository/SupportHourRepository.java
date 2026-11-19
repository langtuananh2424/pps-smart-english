package com.pps.smartenglish.support.repository;

import com.pps.smartenglish.support.domain.SupportHour;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportHourRepository extends JpaRepository<SupportHour, Long> {

    List<SupportHour> findAllByOrderByDayOfWeekAscOpenTimeAsc();
}
