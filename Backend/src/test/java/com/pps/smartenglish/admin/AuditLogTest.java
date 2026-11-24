package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-ADM-05: thay đổi CMS được ghi lại với người thao tác, giá trị trước và sau; chỉ người có audit.view được xem. */
class AuditLogTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";
    private static final String CMS = "/api/admin/cms";
    private static final String AUDIT = "/api/admin/audit-logs";

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

    private JsonNode readBody(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void cmsChangesRecordActorBeforeAndAfter() throws Exception {
        var admin = withRole("ADMIN");
        long id = json.readTree(call(MockMvcRequestBuilders.post(CMS + "/courses"), admin,
                Map.of("slug", "aud-" + UUID.randomUUID(), "title", "Khóa gốc", "targetType", "IELTS"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        call(patch(CMS + "/courses/" + id), admin, Map.of("title", "Khóa mới")).andExpect(status().isOk());

        JsonNode page = readBody(call(get(AUDIT).param("targetType", "COURSE").param("targetId", String.valueOf(id)), admin, null)
                .andExpect(status().isOk()));
        JsonNode rows = page.get("data");
        assertEquals(2, rows.size());
        JsonNode update = rows.get(0); // mới nhất trước
        assertEquals("UPDATE", update.get("action").asText());
        assertEquals("Khóa gốc", update.get("before").get("title").asText());
        assertEquals("Khóa mới", update.get("after").get("title").asText());
        assertEquals(admin.id(), update.get("actorId").asLong());
        JsonNode create = rows.get(1);
        assertEquals("CREATE", create.get("action").asText());
        assertTrue(create.get("before").isNull()); // JSON null, không phải thiếu trường
        assertNotNull(create.get("createdAt"));
    }

    @Test
    void entriesCarryActorEmailAndName() throws Exception {
        var admin = withRole("ADMIN");
        call(MockMvcRequestBuilders.post(CMS + "/courses"), admin,
                Map.of("slug", "ae-" + UUID.randomUUID(), "title", "Khóa", "targetType", "IELTS")).andExpect(status().isCreated());
        JsonNode row = readBody(call(get(AUDIT).param("actorId", String.valueOf(admin.id())).param("limit", "1"), admin, null)
                .andExpect(status().isOk())).get("data").get(0);
        assertEquals(admin.email(), row.get("actorEmail").asText());
        assertEquals("Học viên", row.get("actorName").asText());
    }

    @Test
    void auditLogIsReadableOnlyWithPermission() throws Exception {
        var student = register(newEmail(), PASSWORD);
        var editor = withRole("EDITOR");
        call(get(AUDIT), null, null).andExpect(status().isUnauthorized());
        call(get(AUDIT), student, null).andExpect(status().isForbidden());
        call(get(AUDIT), editor, null).andExpect(status().isForbidden()); // EDITOR không có audit.view
    }

    @Test
    void filtersByActorAndPagesNewestFirst() throws Exception {
        var admin = withRole("ADMIN");
        for (int i = 0; i < 2; i++) {
            call(MockMvcRequestBuilders.post(CMS + "/courses"), admin,
                    Map.of("slug", "pg-" + UUID.randomUUID(), "title", "Khóa " + i, "targetType", "IELTS"))
                    .andExpect(status().isCreated());
        }
        JsonNode first = readBody(call(get(AUDIT).param("actorId", String.valueOf(admin.id())).param("limit", "1"), admin, null)
                .andExpect(status().isOk()));
        assertEquals(1, first.get("data").size());
        String cursor = first.get("nextCursor").asText();
        assertFalse(cursor.isBlank());
        JsonNode second = readBody(call(get(AUDIT).param("actorId", String.valueOf(admin.id())).param("limit", "1")
                .param("cursor", cursor), admin, null).andExpect(status().isOk()));
        assertEquals(1, second.get("data").size());
        assertFalse(first.get("data").get(0).get("id").asLong() == second.get("data").get(0).get("id").asLong());
    }

    @Test
    void inconsistentDateRangeIsRejected() throws Exception {
        var admin = withRole("ADMIN");
        call(get(AUDIT).param("from", "2026-02-01").param("to", "2026-01-01"), admin, null)
                .andExpect(status().isBadRequest());
    }

    private ResultActions createCourse(Account admin, String reasonHeader) throws Exception {
        var req = MockMvcRequestBuilders.post(CMS + "/courses");
        if (reasonHeader != null) {
            req.header("X-Audit-Reason", reasonHeader);
        }
        return call(req, admin, Map.of("slug", "rs-" + UUID.randomUUID(), "title", "Khóa", "targetType", "IELTS"));
    }

    private JsonNode lastLogOf(Account admin, long courseId) throws Exception {
        return readBody(call(get(AUDIT).param("targetType", "COURSE").param("targetId", String.valueOf(courseId)), admin, null)
                .andExpect(status().isOk())).get("data").get(0);
    }

    @Test
    void reasonHeaderIsStoredTrimmedAndReturned() throws Exception {
        var admin = withRole("ADMIN");
        long id = readBody(createCourse(admin, "  Sửa theo yêu cầu #12  ").andExpect(status().isCreated())).get("id").asLong();
        assertEquals("Sửa theo yêu cầu #12", lastLogOf(admin, id).get("reason").asText());
        long blank = readBody(createCourse(admin, "   ").andExpect(status().isCreated())).get("id").asLong();
        assertTrue(lastLogOf(admin, blank).get("reason").isNull());
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String storedReason(String header) throws Exception {
        var admin = withRole("ADMIN");
        long id = readBody(createCourse(admin, header).andExpect(status().isCreated())).get("id").asLong();
        return lastLogOf(admin, id).get("reason").asText();
    }

    @Test
    void encodedReasonIsDecodedAndPlusAndBadPercentAreKept() throws Exception {
        assertEquals("Sửa theo yêu cầu", storedReason(enc("Sửa theo yêu cầu")));
        assertEquals("100%", storedReason("100%"));
        assertEquals("a+b", storedReason("a%2Bb"));
        assertEquals("a+b", storedReason("a+b"));
    }

    @Test
    void reasonLimitAppliesAfterDecoding() throws Exception {
        assertEquals(500, storedReason(enc("ế".repeat(500))).length());
        createCourse(withRole("ADMIN"), enc("ế".repeat(501))).andExpect(status().isBadRequest());
    }

    @Test
    void roleSnapshotHasNoMemberCount() throws Exception {
        var admin = withRole("ADMIN");
        String code = "R" + UUID.randomUUID().toString().replaceAll("[^A-Za-z]", "").toUpperCase();
        long id = readBody(call(MockMvcRequestBuilders.post("/api/admin/roles"), admin,
                Map.of("code", code, "name", "x", "permissions", java.util.List.of())).andExpect(status().isCreated()))
                .get("id").asLong();
        JsonNode after = readBody(call(get(AUDIT).param("targetType", "ROLE").param("targetId", String.valueOf(id)), admin, null)
                .andExpect(status().isOk())).get("data").get(0).get("after");
        assertEquals(code, after.get("code").asText());
        assertFalse(after.has("memberCount"));
    }

    @Test
    void missingReasonHeaderStoresNull() throws Exception {
        var admin = withRole("ADMIN");
        long id = readBody(createCourse(admin, null).andExpect(status().isCreated())).get("id").asLong();
        assertTrue(lastLogOf(admin, id).get("reason").isNull());
    }

    @Test
    void reasonOver500CharsIsRejectedAndChangeRolledBack() throws Exception {
        var admin = withRole("ADMIN");
        int before = count("select count(*) from courses");
        createCourse(admin, "x".repeat(500)).andExpect(status().isCreated());
        createCourse(admin, "x".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("validation_failed"));
        assertEquals(before + 1, count("select count(*) from courses"));
    }
}
