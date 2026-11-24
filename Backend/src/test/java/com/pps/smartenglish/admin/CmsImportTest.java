package com.pps.smartenglish.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * FR-ADM-02, D-133, D-138: nhập hàng loạt (dán nhanh, Excel), xem trước không ghi DB, xác nhận một lô, gợi ý câu trùng,
 * hoàn tác lô (xóa hoặc ẩn), giới hạn tài nguyên và quyền.
 */
@TestPropertySource(properties = {"app.import.max-rows=20", "app.import.max-file-bytes=60000", "app.import.max-entry-bytes=100000"})
class CmsImportTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String PASTE = """
            # Trắc nghiệm
            Q: She was tired ___ she kept working.
            A) because
            *B) although
            C) so
            Giải thích: "although" diễn tả sự tương phản.
            Thẻ: OTHER_COMMON

            # Điền từ
            Q: He stayed home {1} it rained. {2} he was ill, he went out.
            1: because | since
            2: Although | Though

            # Ghép
            Q: Nối hai vế
            cause -> effect
            rain -> wet

            # Xếp khối
            Q: She / has / lived / here / since / 2010
            """;

    // ---- khung dùng chung (cùng kiểu CmsQuestionTest) ----

    private static MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account withRole(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return a == null ? mvc.perform(req) : send(req, a.accessToken());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long idOf(ResultActions r) throws Exception {
        return body(r).get("id").asLong();
    }

    private long topic() {
        long c = insert("courses", Map.of("slug", "i-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        return insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
    }

    private long bank(Account a, long topic) throws Exception {
        return idOf(call(post(CMS + "/topics/" + topic + "/banks"), a, Map.of("title", "Bộ " + UUID.randomUUID(), "isTrial", false))
                .andExpect(status().isCreated()));
    }

    private int questionCount(long bank) {
        return count("select count(*) from questions where bank_id = ?", bank);
    }

    private ResultActions previewPaste(Account a, long bank, String text) throws Exception {
        return send(multipart(CMS + "/banks/" + bank + "/imports/preview").param("text", text), a.accessToken());
    }

    private ResultActions previewFile(Account a, long bank, byte[] bytes) throws Exception {
        return send(multipart(CMS + "/banks/" + bank + "/imports/preview").file(new MockMultipartFile("file", "de.xlsx", XLSX, bytes)),
                a.accessToken());
    }

    /** Đổi các dòng hợp lệ của kết quả xem trước thành yêu cầu xác nhận (như giao diện làm). */
    private Map<String, Object> confirmBody(JsonNode preview, String source, String mode) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode r : preview.get("rows")) {
            if (r.get("question") != null && !r.get("question").isNull()) {
                rows.add(Map.of("line", r.get("line").asInt(), "question", json.convertValue(r.get("question"), Map.class)));
            }
        }
        return Map.of("source", source, "mode", mode, "rows", rows);
    }

    private ResultActions confirm(Account a, long bank, Map<String, Object> body) throws Exception {
        return call(post(CMS + "/banks/" + bank + "/imports"), a, body);
    }

    /** Dán nhanh rồi xác nhận một lô; trả id lô. */
    private long importPaste(Account a, long bank, String text) throws Exception {
        var preview = body(previewPaste(a, bank, text).andExpect(status().isOk()));
        return body(confirm(a, bank, confirmBody(preview, "PASTE", "ALL_OR_NOTHING")).andExpect(status().isCreated())).get("batchId").asLong();
    }

    private static byte[] xlsx(Map<String, List<List<String>>> sheets) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            sheets.forEach((name, rows) -> {
                var sheet = wb.createSheet(name);
                for (int r = 0; r < rows.size(); r++) {
                    var row = sheet.createRow(r);
                    for (int c = 0; c < rows.get(r).size(); c++) {
                        row.createCell(c).setCellValue(rows.get(r).get(c));
                    }
                }
            });
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---- dán nhanh ----

    @Test
    void pastePreviewWritesNothingThenConfirmCreatesOneBatch() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());

        var preview = body(previewPaste(admin, bank, PASTE).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("PASTE"))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.valid").value(4)).andExpect(jsonPath("$.invalid").value(0))
                .andExpect(jsonPath("$.rows[0].type").value("CONNECTIVE_MCQ")).andExpect(jsonPath("$.rows[1].type").value("CONNECTIVE_CLOZE"))
                .andExpect(jsonPath("$.rows[2].type").value("CONNECTIVE_MATCH")).andExpect(jsonPath("$.rows[3].type").value("GRAMMAR_BUILD")));
        assertEquals(0, questionCount(bank), "xem trước không được ghi câu nào");
        assertEquals(0, count("select count(*) from import_batches where bank_id = ?", bank));

        var result = body(confirm(admin, bank, confirmBody(preview, "PASTE", "ALL_OR_NOTHING")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(4)).andExpect(jsonPath("$.skipped", hasSize(0))));
        long batch = result.get("batchId").asLong();
        assertEquals(4, questionCount(bank));
        assertEquals(4, count("select count(*) from questions where import_batch_id = ?", batch));
        assertEquals(4, count("select count(*) from questions where import_batch_id = ? and prompt_hash is not null", batch));
        assertEquals(1, count("select count(*) from audit_logs where action = 'IMPORT' and target_id = ?", String.valueOf(batch)));

        // câu trắc nghiệm có đúng 1 đáp án đúng và kèm thẻ ngữ pháp; thứ tự nối tiếp
        var detail = body(call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(4))));
        assertEquals(List.of(1, 2, 3, 4), List.of(detail.at("/questions/0/sortOrder").asInt(), detail.at("/questions/1/sortOrder").asInt(),
                detail.at("/questions/2/sortOrder").asInt(), detail.at("/questions/3/sortOrder").asInt()));
        assertEquals("OTHER_COMMON", detail.at("/questions/0/grammarElementCodes/0").asText());
        long correct = 0;
        for (JsonNode c : detail.at("/questions/0/choices")) {
            correct += c.get("isCorrect").asBoolean() ? 1 : 0;
        }
        assertEquals(1, correct);

        // lịch sử nhập của bộ
        call(get(CMS + "/banks/" + bank + "/imports"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(batch))
                .andExpect(jsonPath("$[0].status").value("APPLIED")).andExpect(jsonPath("$[0].questionCount").value(4));
    }

    @Test
    void pasteReportsErrorsPerLineAndAllOrNothingWritesNothing() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        String text = """
                # Trắc nghiệm
                Q: Câu tốt
                A) a
                *B) b

                Q: Không có đáp án đúng
                A) a
                B) b

                # Điền từ
                Q: Thiếu đáp án {1} và {2}
                1: x

                # Viết lại
                Q: dạng không dán được
                """;
        var preview = body(previewPaste(admin, bank, text).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.valid").value(1)).andExpect(jsonPath("$.invalid").value(3))
                .andExpect(jsonPath("$.rows[1].line").value(6)).andExpect(jsonPath("$.rows[1].valid").value(false))
                .andExpect(jsonPath("$.rows[1].errors[0].field").value("Đáp án đúng"))
                .andExpect(jsonPath("$.rows[2].line").value(11)).andExpect(jsonPath("$.rows[2].errors[0].field").value("Đáp án chỗ 2"))
                .andExpect(jsonPath("$.rows[3].errors[0].message", containsString("chưa hỗ trợ dán nhanh"))));
        assertEquals(0, questionCount(bank));

        // dòng lỗi vẫn gửi lại ở bước xác nhận (giao diện sửa dở): mặc định không ghi gì
        var good = (Map<String, Object>) json.convertValue(preview.at("/rows/0/question"), Map.class);
        var bad = new LinkedHashMap<>(good);
        bad.put("type", "CONNECTIVE_MCQ");
        bad.put("choices", List.of(Map.of("label", "A", "content", "chỉ một lựa chọn", "isCorrect", true)));
        var rows = List.of(Map.of("line", 1, "question", good), Map.of("line", 6, "question", bad));
        confirm(admin, bank, Map.of("source", "PASTE", "rows", rows)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_has_errors")).andExpect(jsonPath("$.error.details['dòng 6']").exists());
        assertEquals(0, questionCount(bank));
        assertEquals(0, count("select count(*) from import_batches where bank_id = ?", bank));

        // bỏ qua dòng lỗi: chỉ nhập câu hợp lệ và báo dòng bị bỏ
        confirm(admin, bank, Map.of("source", "PASTE", "mode", "SKIP_INVALID", "rows", rows)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(1)).andExpect(jsonPath("$.skipped[0].line").value(6));
        assertEquals(1, questionCount(bank));
    }

    @Test
    void maliciousHtmlIsSanitizedLikeSingleCreate() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        String text = """
                # Trắc nghiệm
                Q: <script>alert(1)</script><b>Chọn đáp án</b> <img src=x onerror=alert(2)>
                A) <i onclick="x()">hai</i>
                *B) ba
                """;
        importPaste(admin, bank, text);
        var q = body(call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk())).at("/questions/0");
        assertEquals("Chọn đáp án", q.get("prompt").asText());
        assertTrue(q.get("promptHtml").asText().contains("<b>Chọn đáp án</b>"));
        assertTrue(!q.get("promptHtml").asText().contains("script") && !q.get("promptHtml").asText().contains("onerror"));
        String stored = jdbc.queryForObject("select content_html from question_choices where question_id = ? and label = 'A'", String.class,
                q.get("id").asLong());
        assertTrue(!stored.contains("onclick") && stored.contains("hai"));
    }

    // ---- Excel ----

    @Test
    void excelImportsOldTypesAndPointsErrorsAtTheRightRow() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        var sheets = new LinkedHashMap<String, List<List<String>>>();
        sheets.put("Trắc nghiệm", List.of(
                List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Lựa chọn C", "Đáp án đúng", "Giải thích"),
                List.of("Hai cộng hai?", "3", "4", "5", "B", "Số học"),
                List.of("Đáp án sai nhãn", "x", "y", "", "E", "")));
        sheets.put("Tìm và sửa lỗi", List.of(List.of("Câu", "Vị trí từ sai", "Cách sửa"),
                List.of("The letter was write by Tom .", "4", "written"), List.of("Quá vị trí", "9", "x")));
        sheets.put("Nhận diện cấu trúc", List.of(List.of("Câu", "Cụm"),
                List.of("The book that I bought was good .", "3-5:RELATIVE_CLAUSE")));
        sheets.put("Ghi chú", List.of(List.of("không phải dạng câu hỏi")));

        previewFile(admin, bank, xlsx(sheets)).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("EXCEL"))
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.valid").value(3)).andExpect(jsonPath("$.invalid").value(2))
                .andExpect(jsonPath("$.warnings[0]", containsString("Ghi chú")))
                .andExpect(jsonPath("$.rows[0].line").value(2)).andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[1].line").value(3)).andExpect(jsonPath("$.rows[1].errors[0].field").value("Đáp án đúng"))
                .andExpect(jsonPath("$.rows[2].type").value("GRAMMAR_SPOT_FIX")).andExpect(jsonPath("$.rows[2].valid").value(true))
                .andExpect(jsonPath("$.rows[3].line").value(3)).andExpect(jsonPath("$.rows[3].errors[0].field").value("Vị trí từ sai"))
                .andExpect(jsonPath("$.rows[4].type").value("GRAMMAR_IDENTIFY")).andExpect(jsonPath("$.rows[4].valid").value(true));
        assertEquals(0, questionCount(bank));

        var preview = body(previewFile(admin, bank, xlsx(sheets)).andExpect(status().isOk()));
        confirm(admin, bank, confirmBody(preview, "EXCEL", "SKIP_INVALID")).andExpect(status().isCreated()).andExpect(jsonPath("$.imported").value(3));
        // vị trí người nhập đếm từ 1 (4) được lưu theo chỉ số 0 (3); Giải thích và thẻ đi theo dòng
        var detail = body(call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(3))));
        assertEquals("Số học", detail.at("/questions/0/explanation").asText());
        assertEquals(3, detail.at("/questions/1/answerKey/position").asInt());
        assertEquals(2, detail.at("/questions/2/answerKey/spans/0/start").asInt());
    }

    @Test
    void generatedTemplateFileImportsCleanlyForEveryType() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        byte[] file = call(get(CMS + "/import/templates.xlsx"), admin, null).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX)).andReturn().getResponse().getContentAsByteArray();
        var preview = body(previewFile(admin, bank, file).andExpect(status().isOk()));
        assertEquals(0, preview.get("invalid").asInt(), preview.toString());
        assertEquals(15, preview.get("rows").findValues("type").stream().map(JsonNode::asText).distinct().count(), "đủ 15 dạng");

        // một dạng
        call(get(CMS + "/import/templates.xlsx").param("type", "WRITING"), admin, null).andExpect(status().isOk());
        call(get(CMS + "/import/templates.xlsx").param("type", "NOPE"), admin, null).andExpect(status().isBadRequest());

        // mọi dạng có nhãn, cột và hướng dẫn; tài liệu nhờ AI nhắc đủ dạng
        call(get(CMS + "/import/templates"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(15)));
        call(get(CMS + "/import/guide"), admin, null).andExpect(status().isOk()).andExpect(content().string(containsString("Đổi đề Word sang Excel")))
                .andExpect(content().string(containsString("### Trắc nghiệm"))).andExpect(content().string(containsString("### Nói"))).andExpect(content().string(containsString("### Hộp từ")))
                .andExpect(content().string(containsString("### Chọn mục")));
    }

    @Test
    void rejectsBadFilesAndOversizedInput() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());

        previewFile(admin, bank, "đây không phải xlsx".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_file_invalid"));
        previewFile(admin, bank, new byte[61_000]).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("import_file_too_large"));

        // quá nhiều dòng (trần 20)
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("Các khối"));
        for (int i = 0; i < 21; i++) {
            rows.add(List.of("a / b " + i));
        }
        previewFile(admin, bank, xlsx(Map.of("Xếp khối", rows))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_too_many_rows"));
        previewPaste(admin, bank, "# Xếp khối\n" + "Q: a / b\n".repeat(21)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_too_many_rows"));

        // zip bomb: một mục giải nén vượt trần 100000 byte dù file nén nhỏ
        var big = new ArrayList<List<String>>();
        big.add(List.of("Các khối"));
        big.add(List.of("a / b " + "x".repeat(30_000)));
        big.add(List.of("a / b " + "y".repeat(30_000)));
        big.add(List.of("a / b " + "z".repeat(30_000)));
        big.add(List.of("a / b " + "w".repeat(30_000)));
        byte[] bomb = xlsx(Map.of("Xếp khối", big));
        assertTrue(bomb.length < 60_000);
        previewFile(admin, bank, bomb).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("import_file_invalid"));

        // chữ dán quá dài, thiếu cả file lẫn text, gửi cả hai
        previewPaste(admin, bank, "x".repeat(200_001)).andExpect(status().isBadRequest());
        send(multipart(CMS + "/banks/" + bank + "/imports/preview"), admin.accessToken()).andExpect(status().isBadRequest());
        assertEquals(0, questionCount(bank));
    }

    @Test
    void xlsxWithExternalEntityIsRefused() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        byte[] ok = xlsx(Map.of("Xếp khối", List.of(List.of("Các khối"), List.of("a / b"))));
        var out = new ByteArrayOutputStream();
        try (var in = new ZipInputStream(new ByteArrayInputStream(ok)); var zip = new ZipOutputStream(out)) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    data = xml.replaceFirst("(<\\?xml[^>]*\\?>)", "$1<!DOCTYPE x [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>")
                            .getBytes(StandardCharsets.UTF_8);
                }
                zip.putNextEntry(new ZipEntry(e.getName()));
                zip.write(data);
                zip.closeEntry();
            }
        }
        previewFile(admin, bank, out.toByteArray()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("import_file_invalid"));
    }

    // ---- 5 kiểu chấm mới (D-144) ----

    private static Map<String, List<List<String>>> newTypeSheets() {
        var sheets = new LinkedHashMap<String, List<List<String>>>();
        sheets.put("Chọn nhiều đáp án", List.of(List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Lựa chọn C", "Đáp án đúng", "Cách chấm"),
                List.of("Chọn số chẵn", "1", "2", "4", "B, C", "Cả câu")));
        sheets.put("Đúng Sai", List.of(List.of("Đề", "Bộ lựa chọn", "Đáp án"), List.of("The sun rises in the west.", "TF", "F")));
        sheets.put("Điền chữ", List.of(List.of("Đề", "Đáp án chấp nhận", "Số từ tối đa"), List.of("Capital of France?", "Paris | the city of Paris", "4")));
        sheets.put("Hộp từ", List.of(List.of("Đề", "Hộp từ", "Đáp án chỗ 1", "Đáp án chỗ 2"),
                List.of("The {1} sat on the {2}.", "cat | mat | sky", "cat", "mat")));
        sheets.put("Chọn mục", List.of(List.of("Đề", "Mục 1", "Mục 2", "Lựa chọn A", "Lựa chọn B", "Lựa chọn C", "Đáp án"),
                List.of("Chọn tiêu đề", "Para one", "Para two", "Cats", "Dogs", "Birds", "1-A, 2-B")));
        return sheets;
    }

    @Test
    void newTypeRowsReportErrorsAtTheRightRowAndColumn() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        var sheets = newTypeSheets();
        // thêm mỗi sheet một dòng lỗi ở dòng 3
        var bad = Map.of(
                "Chọn nhiều đáp án", List.of("x", "1", "2", "3", "E", ""),
                "Đúng Sai", List.of("x", "XX", "TRUE"),
                "Điền chữ", List.of("x", "two words here", "1"),
                "Hộp từ", List.of("Không có chỗ trống", "a | b", "a", ""),
                "Chọn mục", List.of("x", "i1", "i2", "A", "B", "", "1-Z"));
        sheets.replaceAll((name, rows) -> {
            var more = new ArrayList<>(rows);
            more.add(bad.get(name));
            return more;
        });
        var rows = body(previewFile(admin, bank, xlsx(sheets)).andExpect(status().isOk())).get("rows");
        assertEquals(10, rows.size());
        var errors = new LinkedHashMap<String, String>();
        for (JsonNode r : rows) {
            assertEquals(r.get("line").asInt() == 2, r.get("valid").asBoolean(), r.toString());
            if (!r.get("valid").asBoolean()) {
                errors.put(r.get("sheet").asText(), r.at("/errors/0/field").asText());
            }
        }
        assertEquals("Đáp án đúng", errors.get("Chọn nhiều đáp án"));
        assertEquals("Bộ lựa chọn", errors.get("Đúng Sai"));
        assertEquals("answerKey.accepted", errors.get("Điền chữ"));
        assertEquals("Đề", errors.get("Hộp từ"));
        assertEquals("Đáp án", errors.get("Chọn mục"));

        // các lỗi còn lại đến từ validator chung: đáp án ngoài bộ, từ ngoài hộp, cách chấm lạ
        var more = new LinkedHashMap<String, List<List<String>>>();
        more.put("Đúng Sai", List.of(List.of("Đề", "Bộ lựa chọn", "Đáp án"), List.of("x", "TF", "NG")));
        more.put("Hộp từ", List.of(List.of("Đề", "Hộp từ", "Đáp án chỗ 1"), List.of("A {1}", "a | b", "zzz")));
        more.put("Chọn nhiều đáp án", List.of(List.of("Đề", "Lựa chọn A", "Lựa chọn B", "Đáp án đúng", "Cách chấm"), List.of("x", "1", "2", "A", "Tùy")));
        var r2 = body(previewFile(admin, bank, xlsx(more)).andExpect(status().isOk())).get("rows");
        assertEquals("answerKey.answer", r2.at("/0/errors/0/field").asText());
        assertEquals("answerKey.blanks.1", r2.at("/1/errors/0/field").asText());
        assertEquals("Cách chấm", r2.at("/2/errors/0/field").asText());
        assertEquals(0, questionCount(bank));
    }

    @Test
    void importedNewTypeQuestionsAreGradedCorrectly() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, topic());
        var preview = body(previewFile(admin, bank, xlsx(newTypeSheets())).andExpect(status().isOk()).andExpect(jsonPath("$.invalid").value(0)));
        confirm(admin, bank, confirmBody(preview, "EXCEL", "ALL_OR_NOTHING")).andExpect(status().isCreated()).andExpect(jsonPath("$.imported").value(5));
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());

        var detail = body(call(get(CMS + "/banks/" + bank), admin, null).andExpect(status().isOk()));
        var byType = new LinkedHashMap<String, JsonNode>();
        detail.get("questions").forEach(q -> byType.put(q.get("type").asText(), q));
        assertEquals("ALL", byType.get("CHOICE_MULTI").at("/payload/scoring").asText());
        List<Long> evenIds = new ArrayList<>();
        for (JsonNode c : byType.get("CHOICE_MULTI").get("choices")) {
            if (c.get("isCorrect").asBoolean()) {
                evenIds.add(c.get("id").asLong());
            }
        }
        assertEquals(2, evenIds.size());

        long attempt = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        call(post("/api/attempts/" + attempt + "/submit"), student, Map.of("answers", List.of(
                Map.of("questionId", byType.get("CHOICE_MULTI").get("id").asLong(), "answer", Map.of("choiceIds", evenIds)),
                Map.of("questionId", byType.get("TRUE_FALSE").get("id").asLong(), "answer", Map.of("value", "FALSE")),
                Map.of("questionId", byType.get("FILL_TEXT").get("id").asLong(), "answer", Map.of("text", " PARIS ")),
                Map.of("questionId", byType.get("WORD_BANK").get("id").asLong(), "answer", Map.of("blanks", Map.of("1", "cat", "2", "mat"))),
                Map.of("questionId", byType.get("MATCH_OPTIONS").get("id").asLong(),
                        "answer", Map.of("matches", Map.of("i1", "A", "i2", "B"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(5)).andExpect(jsonPath("$.maxScore").value(5));
    }

    // ---- câu nghi trùng ----

    @Test
    void suggestsDuplicatesInFileSameBankAndSameTopicOnly() throws Exception {
        var admin = withRole("ADMIN");
        long topic = topic();
        long bankA = bank(admin, topic);
        long bankB = bank(admin, topic);
        long other = bank(admin, topic());
        importPaste(admin, bankA, "# Xếp khối\nQ: She / has / lived / here / since / 2010\n");

        // chuẩn hóa: hoa thường, khoảng trắng và thứ tự khối trong payload không làm đổi dấu vân tay
        String again = "# Xếp khối\nQ: SHE /  has / lived / here / since / 2010\n\nQ: Câu khác / hoàn toàn / mới\n\nQ: Câu khác / hoàn toàn / mới\n";
        previewPaste(admin, bankA, again).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].duplicates[0].scope").value("BANK"))
                .andExpect(jsonPath("$.rows[1].duplicates", hasSize(0)))
                .andExpect(jsonPath("$.rows[2].duplicates[0].scope").value("FILE")).andExpect(jsonPath("$.rows[2].duplicates[0].line").value(4));
        previewPaste(admin, bankB, again).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].duplicates[0].scope").value("TOPIC"))
                .andExpect(jsonPath("$.rows[0].duplicates[0].bankId").value(bankA)).andExpect(jsonPath("$.rows[0].duplicates[0].bankTitle").exists());
        previewPaste(admin, other, again).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].duplicates", hasSize(0)));
        // gợi ý không chặn: vẫn nhập được
        var preview = body(previewPaste(admin, bankB, again));
        confirm(admin, bankB, confirmBody(preview, "PASTE", "ALL_OR_NOTHING")).andExpect(status().isCreated());
    }

    @Test
    void oldQuestionsWithoutHashAreHashedLazilyWhenCompared() throws Exception {
        var admin = withRole("ADMIN");
        long bank = bank(admin, topic());
        long qid = idOf(call(post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "CONNECTIVE_MCQ", "prompt", "Câu cũ trước #70",
                "choices", List.of(Map.of("label", "A", "content", "a", "isCorrect", true), Map.of("label", "B", "content", "b", "isCorrect", false))))
                .andExpect(status().isCreated()));
        jdbc.update("update questions set prompt_hash = null where id = ?", qid);

        previewPaste(admin, bank, "# Trắc nghiệm\nQ: câu cũ  TRƯỚC #70\nA) a\n*B) b\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].duplicates[0].questionId").value(qid));
        assertNotNull(jdbc.queryForObject("select prompt_hash from questions where id = ?", String.class, qid));
    }

    // ---- hoàn tác ----

    @Test
    void undoDeletesUnusedQuestionsAndArchivesUsedOnes() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, topic());
        long batch = importPaste(admin, bank, "# Xếp khối\nQ: one / two / three\n\nQ: four / five / six\n");
        List<Long> ids = jdbc.queryForList("select id from questions where import_batch_id = ? order by id", Long.class, batch);
        long attempt = insert("student_exercise_attempts", Map.of("user_id", student.id(), "bank_id", bank));
        jdbc.update("insert into student_answers (attempt_id, question_id) values (?, ?)", attempt, ids.get(0));

        call(post(CMS + "/imports/" + batch + "/undo"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(1))
                .andExpect(jsonPath("$.archived").value(1));
        assertEquals(0, count("select count(*) from questions where id = ?", ids.get(1)));
        assertNotNull(jdbc.queryForObject("select archived_at from questions where id = ?", Object.class, ids.get(0)));
        assertEquals(1, count("select count(*) from student_answers where question_id = ?", ids.get(0)));
        assertEquals("UNDONE", jdbc.queryForObject("select status from import_batches where id = ?", String.class, batch));
        assertEquals(1, count("select count(*) from audit_logs where action = 'UNDO_IMPORT' and target_id = ?", String.valueOf(batch)));

        // hoàn tác lại: lỗi rõ; lô không tồn tại: 404
        call(post(CMS + "/imports/" + batch + "/undo"), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("import_already_undone"));
        call(post(CMS + "/imports/99999999/undo"), admin, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("import_batch_not_found"));
    }

    @Test
    void archivedQuestionsLeaveNewAttemptsButStayInStudentHistory() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, topic());
        call(post(CMS + "/banks/" + bank + "/questions"), admin, Map.of("type", "CONNECTIVE_MCQ", "prompt", "Câu soạn tay",
                "choices", List.of(Map.of("label", "A", "content", "a", "isCorrect", true), Map.of("label", "B", "content", "b", "isCorrect", false))))
                .andExpect(status().isCreated());
        long batch = importPaste(admin, bank, "# Xếp khối\nQ: one / two / three\n\nQ: four / five / six\n");
        call(post(CMS + "/banks/" + bank + "/publish"), admin, null).andExpect(status().isOk());

        // học viên làm bài khi đủ 3 câu
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(jsonPath("$.questions", hasSize(3)));
        long attempt = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        call(post("/api/attempts/" + attempt + "/submit"), student, Map.of("answers", List.of())).andExpect(status().isOk());

        // nhập thêm sau khi đã có lượt làm vẫn được; lô mới chưa có lượt làm thì hoàn tác là xóa
        long later = importPaste(admin, bank, "# Xếp khối\nQ: seven / eight / nine\n");
        call(post(CMS + "/imports/" + later + "/undo"), admin, null).andExpect(jsonPath("$.deleted").value(1)).andExpect(jsonPath("$.archived").value(0));

        call(post(CMS + "/imports/" + batch + "/undo"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(0))
                .andExpect(jsonPath("$.archived").value(2));

        // lượt làm mới và màn soạn chỉ thấy câu còn hiệu lực; số câu của bộ cũng vậy
        send(get("/api/banks/" + bank), student.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(content().string(not(containsString("one"))));
        call(get(CMS + "/banks/" + bank), admin, null).andExpect(jsonPath("$.questions", hasSize(1))).andExpect(jsonPath("$.bank.questionCount").value(1));
        long attempt2 = idOf(call(post("/api/banks/" + bank + "/attempts"), student, null).andExpect(status().isCreated()));
        call(post("/api/attempts/" + attempt2 + "/submit"), student, Map.of("answers", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(1));

        // lịch sử bài đã nộp trước đó vẫn đủ 3 câu
        send(get("/api/attempts/" + attempt), student.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.results", hasSize(3)));
        assertNull(jdbc.queryForObject("select max(archived_at) from questions where bank_id = ? and import_batch_id is null", Object.class, bank));
    }

    // ---- quyền và phạm vi ----

    @Test
    void requiresContentPermissionAndRejectsPlacementBanks() throws Exception {
        var admin = withRole("ADMIN");
        var student = register(newEmail(), PASSWORD);
        long bank = bank(admin, topic());
        long batch = importPaste(admin, bank, "# Xếp khối\nQ: a / b\n");

        send(multipart(CMS + "/banks/" + bank + "/imports/preview").param("text", PASTE), student.accessToken()).andExpect(status().isForbidden());
        call(post(CMS + "/banks/" + bank + "/imports"), student, Map.of("source", "PASTE", "rows", List.of())).andExpect(status().isForbidden());
        call(post(CMS + "/imports/" + batch + "/undo"), student, null).andExpect(status().isForbidden());
        call(get(CMS + "/banks/" + bank + "/imports"), student, null).andExpect(status().isForbidden());
        call(get(CMS + "/import/templates.xlsx"), student, null).andExpect(status().isForbidden());
        call(get(CMS + "/import/guide"), student, null).andExpect(status().isForbidden());
        mvc.perform(multipart(CMS + "/banks/" + bank + "/imports/preview").param("text", PASTE)).andExpect(status().isUnauthorized());
        assertEquals("APPLIED", jdbc.queryForObject("select status from import_batches where id = ?", String.class, batch));

        var placement = idOf(call(post(CMS + "/placement/banks"), admin, Map.of("title", "Đầu vào", "timeLimitSec", 600)));
        previewPaste(admin, placement, PASTE).andExpect(status().isBadRequest());
        call(get(CMS + "/banks/999999999/imports"), admin, null).andExpect(status().isNotFound());
        previewPaste(admin, 999999999L, PASTE).andExpect(status().isNotFound());
    }
}
