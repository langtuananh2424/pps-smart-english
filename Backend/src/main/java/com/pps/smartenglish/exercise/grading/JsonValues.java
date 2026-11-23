package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Pattern;

/** Đọc JSON kiểu "khoan dung": kiểu sai trả null thay vì ném lỗi. */
final class JsonValues {

    /** \s kèm khoảng trắng Unicode (NBSP U+00A0...). */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern TRIM = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    private JsonValues() {}

    /** Chuỗi bỏ khoảng trắng đầu cuối, không phân biệt hoa thường; không phải chuỗi thì null. */
    static String normalized(JsonNode n) {
        return n != null && n.isTextual() ? TRIM.matcher(n.asText()).replaceAll("").toLowerCase(Locale.ROOT) : null;
    }

    /** Như normalized, nhưng gộp mọi chuỗi khoảng trắng liền nhau thành một dấu cách. */
    static String squashed(JsonNode n) {
        String s = normalized(n);
        return s == null ? null : WHITESPACE.matcher(s).replaceAll(" ");
    }

    static Long integer(JsonNode n) {
        return n != null && n.isIntegralNumber() ? n.asLong() : null;
    }

    /** Giá trị đơn (số hoặc chuỗi) dưới dạng chuỗi để so id; object, mảng, null thì null. */
    static String scalar(JsonNode n) {
        return n != null && n.isValueNode() && !n.isNull() ? n.asText() : null;
    }
}
