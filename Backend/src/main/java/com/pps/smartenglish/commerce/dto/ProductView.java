package com.pps.smartenglish.commerce.dto;

import java.util.List;

public record ProductView(Long id, String type, String name, long priceVnd, Integer accessDays, Integer aiQuota,
                          List<Long> levelIds) {}
