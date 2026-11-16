package com.pps.smartenglish.commerce.service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Ghi CSV theo RFC 4180 (CRLF) và chặn CSV injection: ô văn bản bắt đầu bằng = + - @ (hoặc tab, CR) thì thêm ' phía trước. */
final class OrderCsv {

    private OrderCsv() {}

    static void row(Writer out, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(cell(cells.get(i)));
        }
        out.write("\r\n");
    }

    /** Số tiền đi qua String.valueOf(long) nên không bao giờ có dấu '-' đầu dòng cần chặn; chỉ văn bản người dùng nhập mới qua đây. */
    static String cell(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        char c = v.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            v = "'" + v;
        }
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return quote ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
