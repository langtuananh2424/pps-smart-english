package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.bulk.ImportSource.SourceRow;
import com.pps.smartenglish.shared.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.http.HttpStatus;
import org.xml.sax.InputSource;

/**
 * Đọc xlsx theo luồng SAX (không dựng cả workbook vào RAM) và dừng ngay khi vượt số dòng. Chặn file độc bằng
 * ZipSecureFile (tỉ lệ nén, cỡ mục giải nén) và parser XML của POI (XMLHelper) đã tắt DOCTYPE/thực thể ngoài (XXE).
 * Mỗi sheet ứng với một dạng câu hỏi theo tên sheet; dòng đầu là tiêu đề cột.
 */
final class ExcelSource implements ImportSource {

    /** Số cột tối đa đọc mỗi dòng: vượt là file bất thường, bỏ phần thừa. */
    private static final int MAX_COLUMNS = 60;

    private final byte[] data;
    private final TemplateRegistry templates;
    private final ImportProperties props;

    ExcelSource(byte[] data, TemplateRegistry templates, ImportProperties props) {
        this.data = data;
        this.templates = templates;
        this.props = props;
    }

    @Override
    public String name() {
        return "EXCEL";
    }

    @Override
    public Parsed read() {
        if (data.length > props.maxFileBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_file_too_large", "File quá lớn (tối đa " + props.maxFileBytes() / 1024 + " KB)");
        }
        // Cấu hình tĩnh của POI; đặt lại mỗi lần để không phụ thuộc thứ tự khởi động.
        ZipSecureFile.setMinInflateRatio(props.minInflateRatio());
        ZipSecureFile.setMaxEntrySize(props.maxEntryBytes());
        List<SourceRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(data))) {
            XSSFReader reader = new XSSFReader(pkg);
            var strings = new ReadOnlySharedStringsTable(pkg);
            var styles = reader.getStylesTable();
            var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream in = sheets.next()) {
                    readSheet(sheets.getSheetName(), in, strings, styles, rows, warnings);
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalid(e);
        }
        return new Parsed(rows, warnings);
    }

    private void readSheet(String sheetName, InputStream in, ReadOnlySharedStringsTable strings,
                           org.apache.poi.xssf.model.StylesTable styles, List<SourceRow> out, List<String> warnings) throws Exception {
        var template = templates.byName(sheetName);
        if (template.isEmpty()) {
            warnings.add("Bỏ qua sheet \"" + sheetName + "\": không phải tên một dạng câu hỏi");
            return;
        }
        var collector = new Collector(props.maxRows() + 1 - out.size()); // +1: dòng tiêu đề
        var xml = XMLHelper.newXMLReader();
        xml.setContentHandler(new XSSFSheetXMLHandler(styles, strings, collector, new DataFormatter(Locale.ROOT), false));
        xml.parse(new InputSource(in));
        if (collector.rows.isEmpty()) {
            return;
        }
        Map<Integer, String> header = collector.rows.get(0).cells;
        for (var raw : collector.rows.subList(1, collector.rows.size())) {
            Map<String, String> cells = new LinkedHashMap<>();
            raw.cells.forEach((col, value) -> {
                String h = header.get(col);
                if (h != null && !h.isBlank()) {
                    cells.put(h, value);
                }
            });
            out.add(new SourceRow(template.get().label(), raw.rowNum + 1, template.get(), cells, null));
        }
    }

    private ApiException invalid(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TooManyRows) {
                return new ApiException(HttpStatus.BAD_REQUEST, "import_too_many_rows", "Tối đa " + props.maxRows() + " câu mỗi lần nhập");
            }
        }
        // Không đưa thông điệp của thư viện ra ngoài: có thể lộ chi tiết nội bộ.
        return new ApiException(HttpStatus.BAD_REQUEST, "import_file_invalid",
                "Không đọc được file: cần file Excel .xlsx đúng định dạng và không bị hỏng");
    }

    private static final class TooManyRows extends RuntimeException {
        TooManyRows() {
            super(null, null, false, false);
        }
    }

    private record RawRow(int rowNum, TreeMap<Integer, String> cells) {}

    /** Gom các dòng không rỗng của một sheet; vượt limit thì ném TooManyRows để dừng parser giữa chừng. */
    private static final class Collector implements SheetContentsHandler {

        final List<RawRow> rows = new ArrayList<>();
        private final int limit;
        private RawRow current;

        Collector(int limit) {
            this.limit = limit;
        }

        @Override
        public void startRow(int rowNum) {
            current = new RawRow(rowNum, new TreeMap<>());
        }

        @Override
        public void endRow(int rowNum) {
            if (!current.cells.isEmpty()) {
                rows.add(current);
                if (rows.size() > limit) {
                    throw new TooManyRows();
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || formattedValue == null || formattedValue.isBlank()) {
                return;
            }
            int col = new CellReference(cellReference).getCol();
            if (col < MAX_COLUMNS) {
                current.cells.put(col, formattedValue.strip());
            }
        }
    }
}
