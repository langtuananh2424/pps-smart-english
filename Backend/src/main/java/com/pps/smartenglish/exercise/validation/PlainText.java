package com.pps.smartenglish.exercise.validation;

import com.pps.smartenglish.shared.error.ApiException;
import java.util.regex.Pattern;

/** Văn bản thuần cho nội dung Cam: trim, bỏ ký tự điều khiển (giữ xuống dòng, tab), không HTML, giới hạn độ dài. */
public final class PlainText {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    // shortcut: chỉ chặn thẻ HTML thô; giao diện vẫn phải hiển thị như văn bản thuần, không dùng innerHTML.
    private static final Pattern TAG = Pattern.compile("<[A-Za-z/!?]");

    private PlainText() {}

    /** Trả chuỗi đã làm sạch, có thể rỗng; null vào thì ra null. */
    public static String clean(String field, String raw, int max) {
        if (raw == null) {
            return null;
        }
        String s = CONTROL.matcher(raw.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("").strip();
        if (TAG.matcher(s).find()) {
            throw ApiException.validation(field, "Chỉ nhận văn bản thuần, không HTML");
        }
        if (s.length() > max) {
            throw ApiException.validation(field, "Tối đa " + max + " ký tự");
        }
        return s;
    }

    /** Bắt buộc có nội dung sau khi làm sạch. */
    public static String required(String field, String raw, int max) {
        String s = clean(field, raw, max);
        if (s == null || s.isEmpty()) {
            throw ApiException.validation(field, "Không được để trống");
        }
        return s;
    }
}
