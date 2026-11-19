package com.pps.smartenglish.catalog.dto;

/** Một mức thời hạn đang bán của khóa (D-127: số mức và số ngày do admin cấu hình). */
public record ProductOption(Long id, String name, long priceVnd, Integer accessDays) {}
