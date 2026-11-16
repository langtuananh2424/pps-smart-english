package com.pps.smartenglish.ai.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Chu kỳ nạp lượt của khóa (D-31): tuần bắt đầu thứ Hai, tháng bắt đầu ngày 1. */
public enum AiPeriod {
    WEEK {
        @Override public LocalDate start(LocalDate day) { return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
        @Override public LocalDate next(LocalDate start) { return start.plusWeeks(1); }
    },
    MONTH {
        @Override public LocalDate start(LocalDate day) { return day.withDayOfMonth(1); }
        @Override public LocalDate next(LocalDate start) { return start.plusMonths(1); }
    };

    /** Ngày đầu kỳ chứa day. */
    public abstract LocalDate start(LocalDate day);

    /** Ngày đầu kỳ kế tiếp. */
    public abstract LocalDate next(LocalDate start);
}
