package com.pps.smartenglish.shared.event;

/** Phát trong transaction dùng link kích hoạt; ai cộng lượt AI (aiQuota = lượt của sản phẩm), ghi sổ cái ACTIVATION_LINK. */
public record ActivationLinkRedeemed(long redemptionId, long userId, int aiQuota) {}
