package com.pps.smartenglish.profile.dto;

import java.time.LocalDate;

public record StatsResponse(long xp, int streakCurrent, int streakBest, LocalDate lastActiveDate) {}
