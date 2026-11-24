package com.pps.smartenglish.exercise.bulk;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Các ô của một dòng theo tên cột. Tên cột so không phân biệt hoa thường và khoảng trắng thừa; ô trống coi như vắng. */
public final class RowCells {

    private final Map<String, String> cells = new LinkedHashMap<>();

    public static String key(String header) {
        return header.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Ô trống bị bỏ; cùng cột lặp thì giữ ô đầu. */
    public RowCells put(String header, String value) {
        if (header != null && value != null && !value.isBlank()) {
            cells.putIfAbsent(key(header), value.strip());
        }
        return this;
    }

    /** null nếu ô trống. */
    public String get(String column) {
        return cells.get(key(column));
    }

    public String need(String column) {
        String v = get(column);
        if (v == null) {
            throw new RowException(column, "Không được để trống");
        }
        return v;
    }

    /** Các cột bắt đầu bằng prefix (vd "Lựa chọn "): trả phần đuôi viết thường (a, b / 1, 2) theo thứ tự cột. */
    public Map<String, String> suffixed(String prefix) {
        String p = key(prefix) + " ";
        Map<String, String> out = new LinkedHashMap<>();
        cells.forEach((k, v) -> {
            if (k.startsWith(p) && k.length() > p.length()) {
                out.put(k.substring(p.length()), v);
            }
        });
        return out;
    }
}
