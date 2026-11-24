package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester vòng 2 (#70, D-144): mẫu Excel 5 kiểu mới với ô độc, công thức, cách chấm sai, nhập vào bộ LOCKED. */
class CmsImportNewTypesAbuseTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private long bank(String playback) {
        long c = insert("courses", Map.of("slug", "nt-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        return insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "PUBLISHED",
                "playback_mode", playback));
    }

    /** sheet -> dòng (dòng đầu là tiêu đề); ô bắt đầu "f:" được ghi là công thức thật. */
    private static byte[] xlsx(Map<String, List<List<String>>> sheets) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            sheets.forEach((name, rows) -> {
                var sheet = wb.createSheet(name);
                for (int r = 0; r < rows.size(); r++) {
                    var row = sheet.createRow(r);
                    for (int c = 0; c < rows.get(r).size(); c++) {
                        String v = rows.get(r).get(c);
                        if (v.startsWith("f:")) {
                            row.createCell(c).setCellFormula(v.substring(2));
                        } else {
                            row.createCell(c).setCellValue(v);
                        }
                    }
                }
            });
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode preview(Account a, long bank, byte[] file) throws Exception {
        return json.readTree(send(multipart(CMS + "/banks/" + bank + "/imports/preview").file(new MockMultipartFile("file", "x.xlsx", XLSX, file)),
                a.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions confirm(Account a, long bank, JsonNode preview) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode r : preview.get("rows")) {
            if (r.get("valid").asBoolean()) {
                rows.add(Map.of("line", r.get("line").asInt(), "question", json.convertValue(r.get("question"), Map.class)));
            }
        }
        return send(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/imports").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("source", "EXCEL", "mode", "SKIP_INVALID", "rows", rows))), a.accessToken());
    }

    private static JsonNode row(JsonNode preview, String sheet, int line) {
        for (JsonNode r : preview.get("rows")) {
            if (r.get("line").asInt() == line && sheet.equals(r.get("sheet").asText())) {
                return r;
            }
        }
        throw new AssertionError("không thấy " + sheet + " dòng " + line + ": " + preview);
    }

    @Test
    void badCellsInNewTypesBecomeRowErrorsNotServerErrors() throws Exception {
        var a = admin();
        long bank = bank("FREE");
        var multiHead = List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Lựa chọn C", "Đáp án đúng", "Cách chấm");
        var tfHead = List.of("Đề", "Bộ lựa chọn", "Đáp án");
        var fillHead = List.of("Đề", "Đáp án chấp nhận", "Số từ tối đa");
        var wbHead = List.of("Đề", "Hộp từ", "Đáp án chỗ 1", "Đáp án chỗ 2");
        var moHead = List.of("Đề", "Mục 1", "Lựa chọn A", "Lựa chọn B", "Đáp án");
        byte[] file = xlsx(Map.of(
                "Chọn nhiều đáp án", List.of(multiHead,
                        List.of("Chọn số chẵn", "1", "2", "4", "B, C", "Từng phần"), // dòng 2: hợp lệ
                        List.of("Sai cách chấm", "1", "2", "3", "B", "Một nửa"), // dòng 3: cách chấm lạ
                        List.of("Nhãn lạ", "1", "2", "3", "Z", ""), // dòng 4
                        List.of("f:1+1", "=HYPERLINK(\"http://evil\",\"x\")", "<img src=x onerror=alert(1)>", "ok", "A, B", "")), // dòng 5
                "Đúng Sai", List.of(tfHead, List.of("Mặt trời mọc ở tây", "XYZ", "T"), List.of("Câu", "TF", "MAYBE")),
                "Điền chữ", List.of(fillHead, List.of("Thủ đô?", "Paris", "-3"), List.of("Thủ đô?", "Paris", "abc")),
                "Hộp từ", List.of(wbHead, List.of("Không có chỗ trống", "a | b", "a", ""), List.of("{1} và {2}", "a | b", "a", "")),
                "Chọn mục", List.of(moHead, List.of("Chọn", "Đoạn 1", "x", "y", "1-Z"), List.of("Chọn", "Đoạn 1", "x", "y", "9-A"))));
        var p = preview(a, bank, file);

        var ok = row(p, "Chọn nhiều đáp án", 2); // Map.of không giữ thứ tự sheet: tìm theo cả sheet
        assertTrue(ok.get("valid").asBoolean(), ok.toString());
        for (JsonNode r : p.get("rows")) {
            if (!(r.get("sheet").asText().equals("Chọn nhiều đáp án") && (r.get("line").asInt() == 2 || r.get("line").asInt() == 5))) {
                assertFalse(r.get("valid").asBoolean(), "dòng phải bị báo lỗi: " + r);
                assertFalse(r.get("errors").isEmpty(), r.toString());
            }
        }
        assertEquals(0, count("select count(*) from questions where bank_id = ?", bank), "xem trước không ghi gì");
        confirm(a, bank, p).andExpect(status().isCreated());
        String choices = jdbc.queryForList("select content from question_choices c join questions q on q.id = c.question_id where q.bank_id = ?",
                String.class, bank).toString();
        assertFalse(choices.contains("onerror"), "nội dung lựa chọn có HTML độc phải bị làm sạch/thoát như soạn tay: " + choices);
    }

    @Test
    void newTypesImportIntoLockedBankAndCountOnlyAttemptCountsThem() throws Exception {
        var a = admin();
        long bank = bank("LOCKED");
        byte[] file = xlsx(Map.of(
                "Chọn nhiều đáp án", List.of(List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Lựa chọn C", "Đáp án đúng", "Cách chấm"),
                        List.of("Chọn số chẵn", "1", "2", "4", "B, C", "Từng phần")),
                "Đúng Sai", List.of(List.of("Đề", "Bộ lựa chọn", "Đáp án"), List.of("The sun rises in the west.", "TF", "F"))));
        var p = preview(a, bank, file);
        assertEquals(2, p.get("valid").asInt(), p.toString());
        confirm(a, bank, p).andExpect(status().isCreated());
        assertEquals("PARTIAL", jdbc.queryForObject("select cast(payload as varchar) from questions where bank_id = ? and type = 'CHOICE_MULTI'",
                String.class, bank).replaceAll(".*\"scoring\"\\s*:\\s*\"(\\w+)\".*", "$1"));

        var s = register(newEmail(), PASSWORD);
        var login = login(s.email(), PASSWORD);
        long attempt = json.readTree(send(MockMvcRequestBuilders.post("/api/banks/" + bank + "/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), login.accessToken()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        var res = json.readTree(send(MockMvcRequestBuilders.post("/api/attempts/" + attempt + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[]}"), login.accessToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, res.at("/forced/totalCount").asInt(), res.toString());
        assertTrue(res.path("score").isMissingNode() && res.path("results").isMissingNode(), res.toString());
    }
}
