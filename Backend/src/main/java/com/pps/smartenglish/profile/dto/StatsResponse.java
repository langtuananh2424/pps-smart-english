package com.pps.smartenglish.profile.dto;

import java.time.LocalDate;

/**
 * Chỉ số học tập của tôi (FR-RET-01, 02). streakCurrent là 0 khi chuỗi đã đứt (không học hôm qua lẫn hôm nay).
 * nextTitle và nextTitleXp null khi đã ở danh hiệu cao nhất.
 */
public record StatsResponse(long xp, int streakCurrent, int streakBest, LocalDate lastActiveDate, String title, String nextTitle,
                            Long nextTitleXp, boolean activeToday) {}
