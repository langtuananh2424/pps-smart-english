package com.pps.smartenglish.shared.web;

import com.pps.smartenglish.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Con trỏ mờ cho phân trang theo id giảm dần (system-design 6.1), dùng chung mọi danh sách. */
public final class Cursors {

    private Cursors() {}

    public static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    /** Rỗng/null = trang đầu (Long.MAX_VALUE); sai định dạng thì 400 validation_failed. */
    public static long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) { // NumberFormatException cũng là IllegalArgumentException
            throw ApiException.validation("cursor", "Con trỏ không hợp lệ");
        }
    }

    /** limit rỗng = mặc định; nhỏ hơn 1 hoặc không phải số thì 400; lớn hơn max thì cắt về max. */
    public static int size(String raw, int defaultSize, int maxSize) {
        if (raw == null || raw.isBlank()) {
            return defaultSize;
        }
        int n;
        try {
            n = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw ApiException.validation("limit", "Phải là số nguyên");
        }
        if (n < 1) {
            throw ApiException.validation("limit", "Phải từ 1 trở lên");
        }
        return Math.min(n, maxSize);
    }
}
