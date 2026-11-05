package com.pps.smartenglish.commerce.dto;

import java.time.Instant;

/** Dòng trong danh sách admin; cố ý không có token hay hash. */
public record ActivationLinkView(Long id, Long productId, String productName, int maxUses, int usedCount,
                                 Instant expiresAt, Instant revokedAt, String note) {}
