package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.exercise-layout.* (D-154, P0 giới hạn tài nguyên): trần kích thước question_groups.layout.
 * maxRows = số hàng bảng/dòng ghi chú/bước/dòng phiếu; maxCols = số cột bảng; maxOptions = số lựa chọn/từ/điểm trên bản đồ;
 * maxPoints = số điểm trên sơ đồ; maxChars = độ dài JSON tối đa của cả layout.
 */
@ConfigurationProperties(prefix = "app.exercise-layout")
public record LayoutProperties(@DefaultValue("50") int maxRows, @DefaultValue("10") int maxCols, @DefaultValue("30") int maxOptions,
                               @DefaultValue("30") int maxPoints, @DefaultValue("100000") int maxChars) {

    public LayoutProperties {
        if (maxRows < 1 || maxCols < 1 || maxOptions < 2 || maxPoints < 1 || maxChars < 1000) {
            throw new IllegalArgumentException("app.exercise-layout: rows, cols, points >= 1, options >= 2, chars >= 1000");
        }
    }
}
