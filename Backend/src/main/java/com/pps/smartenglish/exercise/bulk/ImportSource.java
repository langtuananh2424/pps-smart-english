package com.pps.smartenglish.exercise.bulk;

import java.util.List;
import java.util.Map;

/**
 * Nguồn nhập (Strategy): dán nhanh hoặc Excel. Chỉ phân tích cú pháp thành dòng theo cột của template; kiểm nội dung
 * là việc chung của ImportService nên hai nguồn không bao giờ lệch luật.
 */
public interface ImportSource {

    /** PASTE hoặc EXCEL: lưu vào import_batches.source. */
    String name();

    /** Ném ApiException 400 khi cả đầu vào không dùng được (file hỏng, quá lớn, quá nhiều dòng). */
    Parsed read();

    /** Một dòng/khối đã tách ô. error khác null khi chính dòng đó sai cú pháp (template null nếu chưa biết dạng). */
    record SourceRow(String sheet, int line, ImportTemplate template, Map<String, String> cells, String error) {

        static SourceRow bad(String sheet, int line, String error) {
            return new SourceRow(sheet, line, null, Map.of(), error);
        }
    }

    record Parsed(List<SourceRow> rows, List<String> warnings) {}
}
