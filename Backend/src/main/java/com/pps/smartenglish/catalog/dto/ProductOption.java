package com.pps.smartenglish.catalog.dto;

/** Gói LEVEL đang bán của một khóa (D-34: 180 và 365 ngày). */
public record ProductOption(Long id, String name, long priceVnd, Integer accessDays) {}
