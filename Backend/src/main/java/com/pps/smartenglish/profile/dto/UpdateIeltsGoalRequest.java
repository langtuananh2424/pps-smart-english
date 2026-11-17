package com.pps.smartenglish.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** D-80: band 4.0–8.0 bước 0.5 (như Q&A D-55); ngày mục tiêu tùy chọn, kiểm "sau hôm nay" ở service. */
public record UpdateIeltsGoalRequest(
        @NotNull(message = "Band mục tiêu là bắt buộc")
        @DecimalMin(value = "4.0", message = "Band mục tiêu từ 4.0 đến 8.0")
        @DecimalMax(value = "8.0", message = "Band mục tiêu từ 4.0 đến 8.0") BigDecimal targetBand,
        LocalDate targetDate) {

    @JsonIgnore
    @AssertTrue(message = "Band mục tiêu theo bước 0.5")
    public boolean isTargetBandStepValid() {
        return targetBand == null || targetBand.multiply(BigDecimal.TWO).stripTrailingZeros().scale() <= 0;
    }
}
