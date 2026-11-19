package com.pps.smartenglish.support;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** FR-SUP-02: ticket và chat với nhân viên, giờ làm việc theo cấu hình của admin (BQ-09). */
@Import(MutableClockConfig.class)
class TicketApiTest extends ApiTestBase {

    private static final String PASSWORD = "Password123!";

    @Autowired MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private Account staff(String role) throws Exception {
        var u = register(newEmail(), PASSWORD);
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where code = ?", u.id(), role);
        return login(u.email(), PASSWORD);
    }

    private ResultActions postJson(String url, Account a, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private ResultActions putJson(String url, Account a, Object body) throws Exception {
        return send(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private long createTicket(Account s) throws Exception {
        String body = postJson("/api/support/tickets", s, Map.of("subject", "Không vào được bài giảng", "message", "Em bị lỗi ạ"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/ticket/id").asLong();
    }

    @Test
    void studentCreatesOneActiveTicketAndSeesMessages() throws Exception {
        var s = register(newEmail(), PASSWORD);

        postJson("/api/support/tickets", s, Map.of("subject", " ", "message", "x")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/support/tickets")).andExpect(status().isUnauthorized());

        long id = createTicket(s);
        postJson("/api/support/tickets", s, Map.of("subject", "Hỏi thêm", "message", "y")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ticket_active")).andExpect(jsonPath("$.error.details.ticketId").value(id));

        send(get("/api/support/tickets/" + id), s.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("OPEN")).andExpect(jsonPath("$.ticket.assigned").value(false))
                .andExpect(jsonPath("$.messages", hasSize(1))).andExpect(jsonPath("$.messages[0].fromStaff").value(false));
        send(get("/api/support/tickets"), s.accessToken()).andExpect(jsonPath("$", hasSize(1)));

        // người khác không thấy ticket
        var other = register(newEmail(), PASSWORD);
        send(get("/api/support/tickets/" + id), other.accessToken()).andExpect(status().isNotFound());
        postJson("/api/support/tickets/" + id + "/messages", other, Map.of("body", "chen vào")).andExpect(status().isNotFound());
    }

    @Test
    void staffClaimsRepliesAndStudentIsNotified() throws Exception {
        var s = register(newEmail(), PASSWORD);
        var a = staff("SUPPORT");
        var b = staff("SUPPORT");
        long id = createTicket(s);

        send(get("/api/admin/support/tickets").param("status", "OPEN"), a.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id)).andExpect(jsonPath("$.data[0].studentId").value(s.id()));

        postJson("/api/admin/support/tickets/" + id + "/claim", a, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.assignedTo").value(a.id()));
        postJson("/api/admin/support/tickets/" + id + "/claim", a, Map.of()).andExpect(status().isOk()); // idempotent
        postJson("/api/admin/support/tickets/" + id + "/claim", b, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ticket_taken"));
        postJson("/api/admin/support/tickets/" + id + "/messages", b, Map.of("body", "Em xin phép")).andExpect(status().isConflict());

        postJson("/api/admin/support/tickets/" + id + "/messages", a, Map.of("body", "Chào em, anh hỗ trợ đây"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fromStaff").value(true));
        assertEquals(1, count("select count(*) from notifications where user_id = ? and type = 'TICKET_REPLY'", s.id()));
        // nội dung tin nhắn không lọt vào thông báo
        assertEquals(0, count("select count(*) from notifications where user_id = ? and payload like '%anh hỗ trợ%'", s.id()));

        // học viên thấy tin của nhân viên, hỏi lại theo after chỉ nhận tin mới
        long firstId = json.readTree(send(get("/api/support/tickets/" + id), s.accessToken()).andReturn().getResponse().getContentAsString())
                .at("/messages/0/id").asLong();
        send(get("/api/support/tickets/" + id).param("after", String.valueOf(firstId)), s.accessToken()).andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].fromStaff").value(true)).andExpect(jsonPath("$.ticket.assigned").value(true));
        postJson("/api/support/tickets/" + id + "/messages", s, Map.of("body", "Dạ em cảm ơn")).andExpect(status().isCreated());
        send(get("/api/admin/support/tickets/" + id), a.accessToken()).andExpect(jsonPath("$.messages", hasSize(3)));
    }

    @Test
    void closeAndReopenFlow() throws Exception {
        var s = register(newEmail(), PASSWORD);
        var a = staff("SUPPORT");
        long id = createTicket(s);
        postJson("/api/admin/support/tickets/" + id + "/claim", a, Map.of()).andExpect(status().isOk());

        postJson("/api/support/tickets/" + id + "/close", s, Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        postJson("/api/support/tickets/" + id + "/close", s, Map.of()).andExpect(status().isOk()); // idempotent
        postJson("/api/admin/support/tickets/" + id + "/messages", a, Map.of("body", "trễ")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ticket_closed"));

        // đóng rồi thì được tạo ticket mới; nhắn tiếp vào ticket cũ thì mở lại và bỏ người nhận
        postJson("/api/support/tickets/" + id + "/messages", s, Map.of("body", "Em vẫn bị lỗi")).andExpect(status().isCreated());
        send(get("/api/support/tickets/" + id), s.accessToken()).andExpect(jsonPath("$.ticket.status").value("OPEN"))
                .andExpect(jsonPath("$.ticket.assigned").value(false));
        postJson("/api/admin/support/tickets/" + id + "/close", a, Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'SUPPORT_TICKET' and target_id = ? and action = 'TICKET_CLAIM'", String.valueOf(id)));
        assertEquals(1, count("select count(*) from audit_logs where target_type = 'SUPPORT_TICKET' and target_id = ? and action = 'TICKET_CLOSE'", String.valueOf(id)));
    }

    @Test
    void ticketPermissionsAndStatusFilter() throws Exception {
        var s = register(newEmail(), PASSWORD);
        send(get("/api/admin/support/tickets"), s.accessToken()).andExpect(status().isForbidden());
        send(get("/api/admin/support/tickets"), staff("TEACHER").accessToken()).andExpect(status().isForbidden()); // chỉ có student.view
        send(get("/api/admin/support/tickets"), staff("ACCOUNTANT").accessToken()).andExpect(status().isForbidden());
        var support = staff("SUPPORT");
        send(get("/api/admin/support/tickets").param("status", "WRONG"), support.accessToken()).andExpect(status().isBadRequest());
        send(get("/api/admin/support/tickets/999999"), support.accessToken()).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/support/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void workingHoursAreConfiguredByAdminAndDecideInHours() throws Exception {
        var s = register(newEmail(), PASSWORD);
        var support = staff("SUPPORT");
        var admin = staff("ADMIN");
        clock.set(Instant.parse("2026-11-18T05:00:00Z")); // thứ Tư 12:00 giờ Việt Nam (token đã cấp trước khi chỉnh đồng hồ)

        // chưa cài giờ: luôn trong giờ
        send(get("/api/support/info"), s.accessToken()).andExpect(jsonPath("$.inHours").value(true));

        send(get("/api/admin/support/settings"), support.accessToken()).andExpect(status().isOk());
        putJson("/api/admin/support/settings", support, Map.of("hours", List.of())).andExpect(status().isForbidden());

        var wed = Map.of("timezone", "Asia/Ho_Chi_Minh", "etaMessage", "Trong 1 ngày làm việc", "zaloUrl", "https://zalo.me/x", "hotline", "1900",
                "hours", List.of(Map.of("dayOfWeek", 3, "open", "08:00", "close", "17:00")));
        putJson("/api/admin/support/settings", admin, wed).andExpect(status().isOk()).andExpect(jsonPath("$.hours", hasSize(1)));
        send(get("/api/support/info"), s.accessToken()).andExpect(jsonPath("$.inHours").value(true))
                .andExpect(jsonPath("$.hotline").value("1900"));

        var late = Map.of("timezone", "Asia/Ho_Chi_Minh", "etaMessage", "Trong 1 ngày làm việc", "hotline", "1900",
                "hours", List.of(Map.of("dayOfWeek", 3, "open", "13:00", "close", "17:00")));
        putJson("/api/admin/support/settings", admin, late).andExpect(status().isOk());
        send(get("/api/support/info"), s.accessToken()).andExpect(jsonPath("$.inHours").value(false))
                .andExpect(jsonPath("$.etaMessage").value("Trong 1 ngày làm việc"));
        // ngoài giờ vẫn tạo được ticket, kèm thông tin để giao diện báo
        postJson("/api/support/tickets", s, Map.of("subject", "Hỏi", "message", "ngoài giờ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.info.inHours").value(false));

        // validate cấu hình
        putJson("/api/admin/support/settings", admin, Map.of("timezone", "Khong/Hop_Le", "hours", List.of(Map.of("dayOfWeek", 1, "open", "08:00", "close", "09:00"))))
                .andExpect(status().isBadRequest());
        putJson("/api/admin/support/settings", admin, Map.of("timezone", "Asia/Ho_Chi_Minh", "hours", List.of(Map.of("dayOfWeek", 1, "open", "10:00", "close", "09:00"))))
                .andExpect(status().isBadRequest());
        putJson("/api/admin/support/settings", admin, Map.of("timezone", "Asia/Ho_Chi_Minh", "hours", List.of(
                Map.of("dayOfWeek", 1, "open", "08:00", "close", "12:00"), Map.of("dayOfWeek", 1, "open", "11:00", "close", "17:00"))))
                .andExpect(status().isBadRequest());
        putJson("/api/admin/support/settings", admin, Map.of("hours", List.of(Map.of("dayOfWeek", 8, "open", "08:00", "close", "09:00"))))
                .andExpect(status().isBadRequest());
        // xóa giờ làm việc: về luôn trong giờ
        putJson("/api/admin/support/settings", admin, Map.of("hours", List.of())).andExpect(status().isOk());
        send(get("/api/support/info"), s.accessToken()).andExpect(jsonPath("$.inHours").value(true));
    }
}
