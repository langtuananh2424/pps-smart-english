package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tester #70: đầu vào xấu ở bước xem trước/xác nhận nhập hàng loạt không làm 500 và không ghi gì. */
@TestPropertySource(properties = {"app.import.max-rows=20", "app.import.max-file-bytes=60000", "app.import.max-entry-bytes=100000"})
class CmsImportAbuseTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    private Account admin() throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = 'ADMIN'", u.id());
        return login(u.email(), PASSWORD);
    }

    private long bank() {
        long c = insert("courses", Map.of("slug", "ia-" + UUID.randomUUID(), "title", "Khóa", "status", "PUBLISHED"));
        long lv = insert("course_levels", Map.of("course_id", c, "code", "A", "title", "L", "status", "PUBLISHED"));
        long t = insert("topics", Map.of("level_id", lv, "title", "T", "is_free_trial", true, "status", "PUBLISHED"));
        return insert("question_banks", Map.of("topic_id", t, "title", "Bộ", "is_trial", false, "status", "DRAFT"));
    }

    private ResultActions confirm(Account a, long bank, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/imports").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), a.accessToken());
    }

    private static Map<String, Object> mcq(String prompt, Long groupId) {
        var q = new java.util.HashMap<String, Object>(Map.of("type", "CONNECTIVE_MCQ", "prompt", prompt, "choices",
                List.of(Map.of("label", "A", "content", "a", "isCorrect", true), Map.of("label", "B", "content", "b", "isCorrect", false))));
        if (groupId != null) {
            q.put("groupId", groupId);
        }
        return q;
    }

    private int questions(long bank) {
        return count("select count(*) from questions where bank_id = ?", bank);
    }

    @Test
    void zipThatIsNotAnXlsxIsRejectedCleanly() throws Exception {
        var a = admin();
        long bank = bank();
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../../evil.txt"));
            zip.write("hello".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        send(multipart(CMS + "/banks/" + bank + "/imports/preview")
                .file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", out.toByteArray())), a.accessToken())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("import_file_invalid"));
    }

    @Test
    void sendingBothTextAndFileIsRejected() throws Exception {
        var a = admin();
        long bank = bank();
        send(multipart(CMS + "/banks/" + bank + "/imports/preview").file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[10]))
                .param("text", "# Xếp khối\nQ: a / b\n"), a.accessToken()).andExpect(status().isBadRequest());
        assertEquals(0, questions(bank));
    }

    @Test
    void confirmEnforcesRowCapAndBankOwnershipOfGroupsAndWritesNothing() throws Exception {
        var a = admin();
        long bank = bank();
        long otherBank = bank();
        long foreignGroup = insert("question_groups", Map.of("bank_id", otherBank, "sort_order", 1, "title", "Part khác"));

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(Map.of("line", i + 1, "question", mcq("Câu " + i, null)));
        }
        confirm(a, bank, Map.of("source", "EXCEL", "rows", many)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_too_many_rows"));

        // groupId của bộ khác gửi thẳng ở bước xác nhận: dòng lỗi, không gắn câu vào part bộ khác
        confirm(a, bank, Map.of("source", "PASTE", "rows", List.of(Map.of("line", 1, "question", mcq("Câu lạ", foreignGroup)))))
                .andExpect(status().isBadRequest());
        confirm(a, bank, Map.of("source", "PASTE", "mode", "SKIP_INVALID",
                "rows", List.of(Map.of("line", 1, "question", mcq("Câu lạ", foreignGroup))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("import_empty"));
        // nguồn lạ: 400 kiểm tra đầu vào, không rơi xuống ràng buộc CHECK của DB
        confirm(a, bank, Map.of("source", "CSV", "rows", List.of(Map.of("line", 1, "question", mcq("Câu", null)))))
                .andExpect(status().isBadRequest());
        assertEquals(0, questions(bank));
        assertEquals(0, count("select count(*) from questions where group_id = ?", foreignGroup));
        assertEquals(0, count("select count(*) from import_batches where bank_id = ?", bank));
    }

    @Test
    void scriptHtmlInConfirmedRowsIsSanitized() throws Exception {
        var a = admin();
        long bank = bank();
        var q = new java.util.HashMap<String, Object>(mcq("x", null));
        q.remove("prompt");
        q.put("promptHtml", "<p>Chọn<script>alert(1)</script><img src=x onerror=alert(2)></p>");
        JsonNode res = json.readTree(confirm(a, bank, Map.of("source", "PASTE", "rows", List.of(Map.of("line", 1, "question", q))))
                .andReturn().getResponse().getContentAsString());
        if (res.has("batchId")) {
            String html = jdbc.queryForObject("select prompt_html from questions where bank_id = ?", String.class, bank);
            assertEquals(false, html != null && (html.contains("<script") || html.contains("onerror")), html);
        }
    }
}
