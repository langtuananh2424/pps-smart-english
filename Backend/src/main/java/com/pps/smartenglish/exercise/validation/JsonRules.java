package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Các phép kiểm JSON dùng chung của validator; luôn ném 400 kèm tên trường (vd. "payload.left[1].id"). */
final class JsonRules {

    private JsonRules() {}

    static ApiException bad(String field, String message) {
        return ApiException.validation(field, message);
    }

    /** Phải là object và chỉ có các trường cho phép: trường lạ là 400 (whitelist, không blacklist). */
    static JsonNode object(JsonNode n, String field, Set<String> allowed) {
        if (n == null || !n.isObject()) {
            throw bad(field, "Phải là một object");
        }
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            String name = it.next();
            if (!allowed.contains(name)) {
                throw bad(field + "." + name, "Trường không được phép cho dạng này");
            }
        }
        return n;
    }

    static String text(JsonNode obj, String name, String field) {
        JsonNode v = obj.get(name);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw bad(field + "." + name, "Bắt buộc, là chuỗi không rỗng");
        }
        return v.asText();
    }

    static JsonNode array(JsonNode obj, String name, String field, int min) {
        JsonNode v = obj.get(name);
        if (v == null || !v.isArray() || v.size() < min) {
            throw bad(field + "." + name, min <= 1 ? "Bắt buộc, là mảng không rỗng" : "Bắt buộc, là mảng từ " + min + " phần tử");
        }
        return v;
    }

    static int integer(JsonNode obj, String name, String field) {
        JsonNode v = obj.get(name);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt()) {
            throw bad(field + "." + name, "Bắt buộc, là số nguyên");
        }
        return v.asInt();
    }

    /** Mảng chuỗi không rỗng; unique = true thì không được lặp. */
    static Set<String> texts(JsonNode arr, String field, boolean unique) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonNode t = arr.get(i);
            if (!t.isTextual() || t.asText().isBlank()) {
                throw bad(field + "[" + i + "]", "Phải là chuỗi không rỗng");
            }
            if (!out.add(t.asText()) && unique) {
                throw bad(field + "[" + i + "]", "Bị lặp");
            }
        }
        return out;
    }

    /** Mảng {id, text}: trả tập id (không lặp). */
    static Set<String> idTextList(JsonNode obj, String name, String field, int min) {
        JsonNode arr = array(obj, name, field, min);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            String at = field + "." + name + "[" + i + "]";
            JsonNode item = object(arr.get(i), at, Set.of("id", "text"));
            if (!ids.add(text(item, "id", at))) {
                throw bad(at + ".id", "Bị lặp");
            }
            text(item, "text", at);
        }
        return ids;
    }

    static void requireNone(JsonNode n, String field, String message) {
        if (n != null) {
            throw bad(field, message);
        }
    }

    static Set<String> fieldNames(JsonNode obj) {
        Set<String> names = new HashSet<>();
        obj.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
