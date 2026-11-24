package com.pps.smartenglish.exercise.bulk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.import.*: trần tài nguyên của nhập hàng loạt (D-138: con số cấu hình được, có mặc định).
 * maxEntryBytes và minInflateRatio chặn zip bomb trong file xlsx; maxHashFill giới hạn số câu cũ điền dấu vân tay mỗi lần so trùng.
 */
@ConfigurationProperties(prefix = "app.import")
public record ImportProperties(
        @DefaultValue("2097152") long maxFileBytes,
        @DefaultValue("500") int maxRows,
        @DefaultValue("200000") int maxPasteChars,
        @DefaultValue("20971520") long maxEntryBytes,
        @DefaultValue("0.01") double minInflateRatio,
        @DefaultValue("2000") int maxHashFill) {}
