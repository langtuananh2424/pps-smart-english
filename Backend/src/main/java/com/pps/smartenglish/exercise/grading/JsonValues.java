package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;

/** Đọc JSON kiểu "khoan dung": kiểu sai trả null thay vì ném lỗi. */
final class JsonValues {

    private JsonValues() {}

    /** Chuỗi bỏ khoảng trắng đầu cuối, không phân biệt hoa thường; không phải chuỗi thì null. */
    static String normalized(JsonNode n) {
        return n != null && n.isTextual() ? n.asText().trim().toLowerCase(Locale.ROOT) : null;
    }

    static Long integer(JsonNode n) {
        return n != null && n.isIntegralNumber() ? n.asLong() : null;
    }

    /** Giá trị đơn (số hoặc chuỗi) dưới dạng chuỗi để so id; object, mảng, null thì null. */
    static String scalar(JsonNode n) {
        return n != null && n.isValueNode() && !n.isNull() ? n.asText() : null;
    }
}
