package com.pps.smartenglish.exercise.bulk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** File mẫu Excel: mỗi dạng một sheet, dòng tiêu đề cột và dòng ví dụ điền sẵn (question-editor-spec 5.3). */
@Component
public class TemplateWorkbook {

    /** Mẫu của các dạng đã cho theo thứ tự đưa vào. */
    public byte[] build(List<ImportTemplate> templates) {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            for (ImportTemplate t : templates) {
                var sheet = wb.createSheet(t.label());
                var header = sheet.createRow(0);
                List<String> cols = t.columns();
                for (int c = 0; c < cols.size(); c++) {
                    var cell = header.createCell(c);
                    cell.setCellValue(cols.get(c));
                    cell.setCellStyle(head);
                    sheet.setColumnWidth(c, 5200);
                }
                int r = 1;
                for (List<String> sample : t.samples()) {
                    var row = sheet.createRow(r++);
                    for (int c = 0; c < sample.size(); c++) {
                        row.createCell(c).setCellValue(sample.get(c));
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
