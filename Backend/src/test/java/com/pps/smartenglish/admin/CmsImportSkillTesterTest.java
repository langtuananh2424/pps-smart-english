package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester D-164: cột "Kỹ năng" khi nhập Excel thật (xem trước + xác nhận) và dòng "Kỹ năng:" khi dán nhanh. */
class CmsImportSkillTesterTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<String> MCQ_HEAD = List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Đáp án đúng", "Kỹ năng");

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long bank(Account a, String mode) throws Exception {
        long c = insert("courses", Map.of("slug", "ist-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "status", "PUBLISHED"));
        return body(call(MockMvcRequestBuilders.post(CMS + "/topics/" + t + "/banks"), a, Map.of("title", "Bộ " + UUID.randomUUID(), "mode", mode))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Bộ thi có bảng band READING (cần một câu READING trước). */
    private long bandedBank(Account a) throws Exception {
        long b = bank(a, "EXAM");
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + b + "/questions"), a, Map.of("type", "CONNECTIVE_MCQ", "prompt", "Câu gốc " + UUID.randomUUID(), "skill", "READING",
                "choices", List.of(Map.of("label", "A", "content", "x", "isCorrect", true), Map.of("label", "B", "content", "y", "isCorrect", false))))
                .andExpect(status().is2xxSuccessful());
        call(put(CMS + "/banks/" + b + "/band-rules"), a, Map.of("rules", List.of(Map.of("skill", "READING", "minPercent", 0, "band", 4.0),
                Map.of("skill", "READING", "minPercent", 100, "band", 6.0)))).andExpect(status().isOk());
        return b;
    }

    private static List<String> mcq(String skill) {
        return List.of("Câu " + UUID.randomUUID(), "x", "y", "A", skill);
    }

    @SafeVarargs
    private static byte[] xlsx(Map.Entry<String, List<List<String>>>... sheets) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            for (var s : sheets) {
                var sheet = wb.createSheet(s.getKey());
                for (int r = 0; r < s.getValue().size(); r++) {
                    var row = sheet.createRow(r);
                    for (int c = 0; c < s.getValue().get(r).size(); c++) {
                        if (!s.getValue().get(r).get(c).isEmpty()) {
                            row.createCell(c).setCellValue(s.getValue().get(r).get(c));
                        }
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @SafeVarargs
    private static Map.Entry<String, List<List<String>>> sheet(String name, List<String> head, List<String>... rows) {
        var all = new ArrayList<List<String>>();
        all.add(head);
        all.addAll(Arrays.asList(rows));
        return Map.entry(name, all);
    }

    private JsonNode preview(Account a, long bank, byte[] file) throws Exception {
        return body(send(multipart(CMS + "/banks/" + bank + "/imports/preview").file(new MockMultipartFile("file", "de.xlsx", XLSX, file)),
                a.accessToken()).andExpect(status().isOk()));
    }

    /** Gửi lại mọi dòng (kể cả dòng lỗi mà giao diện vẫn có question) ở bước xác nhận. */
    private ResultActions confirm(Account a, long bank, JsonNode preview, String mode) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode r : preview.get("rows")) {
            if (r.hasNonNull("question")) {
                rows.add(Map.of("line", r.get("line").asInt(), "question", json.convertValue(r.get("question"), Map.class)));
            }
        }
        return call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/imports"), a, Map.of("source", "EXCEL", "mode", mode, "rows", rows));
    }

    private List<String> skills(long bank) {
        return jdbc.queryForList("select coalesce(skill, '-') from questions where bank_id = ? and prompt like 'Câu %' and prompt not like 'Câu gốc%' order by sort_order",
                String.class, bank);
    }

    private static String errorFields(JsonNode row) {
        var sb = new StringBuilder();
        row.get("errors").forEach(e -> sb.append(e.get("field").asText()).append(": ").append(e.get("message").asText()).append("; "));
        return sb.toString();
    }

    @Test
    void codesAndVietnameseLabelsAreSavedAsSkillCodes() throws Exception {
        var a = admin();
        long bank = bank(a, "EXAM");
        var p = preview(a, bank, xlsx(sheet("Trắc nghiệm", MCQ_HEAD, mcq("READING"), mcq("listening"), mcq("Nghe"), mcq("  Đọc "),
                mcq("NGỮ PHÁP"), mcq(""))));
        assertEquals(6, p.get("valid").asInt(), p.toString());
        List<String> previewSkills = new ArrayList<>();
        p.get("rows").forEach(r -> previewSkills.add(r.at("/question/skill").isNull() || r.at("/question/skill").isMissingNode()
                ? "-" : r.at("/question/skill").asText()));
        assertEquals(List.of("READING", "LISTENING", "LISTENING", "READING", "GRAMMAR", "-"), previewSkills);
        confirm(a, bank, p, "ALL_OR_NOTHING").andExpect(status().isCreated());
        assertEquals(List.of("READING", "LISTENING", "LISTENING", "READING", "GRAMMAR", "-"), skills(bank));
    }

    @Test
    void unknownSkillValuesAreRowErrorsAndNothingIsWritten() throws Exception {
        var a = admin();
        long bank = bank(a, "EXAM");
        var p = preview(a, bank, xlsx(sheet("Trắc nghiệm", MCQ_HEAD, mcq("READING"), mcq("SPEAKING"), mcq("Viết"), mcq("abc"))));
        assertEquals(1, p.get("valid").asInt());
        for (int i = 1; i <= 3; i++) {
            var row = p.get("rows").get(i);
            assertFalse(row.get("valid").asBoolean());
            assertTrue(errorFields(row).contains("skill"), "dòng " + row.get("line") + " phải báo lỗi ở skill: " + errorFields(row));
        }
        confirm(a, bank, p, "ALL_OR_NOTHING").andExpect(status().isBadRequest());
        assertEquals(List.of(), skills(bank));
    }

    @Test
    void bandedBankRejectsAutoRowsWithoutSkill() throws Exception {
        var a = admin();
        long bank = bandedBank(a);
        var p = preview(a, bank, xlsx(sheet("Trắc nghiệm", MCQ_HEAD, mcq("Đọc"), mcq(""))));
        assertEquals(1, p.get("valid").asInt());
        assertTrue(errorFields(p.get("rows").get(1)).contains("skill"), errorFields(p.get("rows").get(1)));
        confirm(a, bank, p, "ALL_OR_NOTHING").andExpect(status().isBadRequest());
        assertEquals(List.of(), skills(bank));
        confirm(a, bank, p, "SKIP_INVALID").andExpect(status().isCreated());
        assertEquals(List.of("READING"), skills(bank));
    }

    @Test
    void writingAndSpeakingSheetsRejectSkillButAcceptBlank() throws Exception {
        var a = admin();
        long bank = bandedBank(a); // có band: Viết/Nói trống kỹ năng vẫn hợp lệ (không tự chấm)
        var p = preview(a, bank, xlsx(
                sheet("Viết", List.of("Đề", "Số từ", "Kỹ năng"), List.of("Câu viết có skill", "120", "READING"), List.of("Câu viết trống", "120", "")),
                sheet("Nói", List.of("Đề", "Part", "Kỹ năng"), List.of("Câu nói có skill", "1", "Nghe"), List.of("Câu nói trống", "1", ""))));
        var rows = new LinkedHashMap<String, JsonNode>();
        p.get("rows").forEach(r -> rows.put(r.at("/question/prompt").asText(r.get("prompt").asText()), r));
        assertEquals(4, p.get("total").asInt(), p.toString());
        assertEquals(2, p.get("valid").asInt(), p.toString());
        p.get("rows").forEach(r -> {
            boolean hasSkill = r.at("/question/skill").isTextual();
            assertEquals(!hasSkill, r.get("valid").asBoolean(), "sheet " + r.get("sheet") + " dòng " + r.get("line") + ": " + errorFields(r));
        });
        confirm(a, bank, p, "SKIP_INVALID").andExpect(status().isCreated());
        assertEquals(0, count("select count(*) from questions where bank_id = ? and type in ('WRITING', 'SPEAKING') and skill is not null", bank));
        assertEquals(2, count("select count(*) from questions where bank_id = ? and type in ('WRITING', 'SPEAKING')", bank));
    }

    @Test
    void practiceBankRejectsSkillColumn() throws Exception {
        var a = admin();
        long bank = bank(a, "PRACTICE");
        var p = preview(a, bank, xlsx(sheet("Trắc nghiệm", MCQ_HEAD, mcq("READING"), mcq(""))));
        assertEquals(1, p.get("valid").asInt());
        assertFalse(p.get("rows").get(0).get("valid").asBoolean());
    }

    @Test
    void pasteSkillLineUsesVietnameseLabel() throws Exception {
        var a = admin();
        long bank = bank(a, "EXAM");
        String text = "# Trắc nghiệm\nQ: Câu dán " + UUID.randomUUID() + "\nA) a\n*B) b\nKỹ năng: Ngữ pháp\n";
        var p = body(send(multipart(CMS + "/banks/" + bank + "/imports/preview").param("text", text), a.accessToken()).andExpect(status().isOk()));
        assertEquals("GRAMMAR", p.at("/rows/0/question/skill").asText(), p.toString());
    }
}
