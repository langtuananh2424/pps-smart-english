package com.pps.smartenglish.profile;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProfileTest extends ApiTestBase {

    private org.springframework.test.web.servlet.ResultActions call(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private static Map<String, Object> goal(String type, String value) {
        return Map.of("targetType", type, "targetValue", value);
    }

    @Test
    void meRequiresTokenAndErrorHasEnvelopeWithRequestId() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("unauthenticated"))
                .andExpect(jsonPath("$.error.requestId", notNullValue()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer rac")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAndPatchMe() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        send(get("/api/me"), a.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(a.email()))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.displayName").value("Học viên"))
                .andExpect(jsonPath("$.selfLevel").doesNotExist())
                .andExpect(jsonPath("$.timezone").doesNotExist());

        call(patch("/api/me"), a, Map.of("displayName", "  Tên mới ", "selfLevel", "TRUNG_CAP")).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Tên mới"))
                .andExpect(jsonPath("$.selfLevel").value("TRUNG_CAP"));
        call(patch("/api/me"), a, Map.of("selfLevel", "KHA")).andExpect(jsonPath("$.displayName").value("Tên mới"));
    }

    @Test
    void patchRejectsBadValuesAndIgnoresProtectedFields() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        call(patch("/api/me"), a, Map.of("displayName", "   ")).andExpect(status().isBadRequest());
        call(patch("/api/me"), a, Map.of("displayName", "x".repeat(101))).andExpect(status().isBadRequest());
        call(patch("/api/me"), a, Map.of("selfLevel", "SIEU_GIOI")).andExpect(status().isBadRequest());
        call(patch("/api/me"), a, Map.of("xp", 999, "timezone", "Asia/Tokyo")).andExpect(status().isOk());
        send(get("/api/me/stats"), a.accessToken()).andExpect(jsonPath("$.xp").value(0));
        send(get("/api/me"), a.accessToken()).andExpect(jsonPath("$.timezone").doesNotExist());
    }

    @Test
    void statsStartAtZero() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        send(get("/api/me/stats"), a.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(0)).andExpect(jsonPath("$.streakCurrent").value(0))
                .andExpect(jsonPath("$.streakBest").value(0)).andExpect(jsonPath("$.lastActiveDate").doesNotExist());
    }

    @Test
    void putGoalsReplacesEverythingAndIsPerUser() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        Account b = register(newEmail(), "mat-khau-1");
        call(put("/api/me/goals"), b, Map.of("goals", List.of(goal("EXAM", "IELTS")))).andExpect(status().isOk());

        call(put("/api/me/goals"), a, Map.of("goals", List.of(goal("EXAM", "IELTS 6.5"),
                Map.of("targetType", "SKILL", "targetValue", "Speaking", "courseId", 7, "targetDate", "2026-12-31"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].courseId").value(7)).andExpect(jsonPath("$[1].targetDate").value("2026-12-31"));

        call(put("/api/me/goals"), a, Map.of("goals", List.of(goal("JOB", "Giao tiếp")))).andExpect(jsonPath("$", hasSize(1)));
        send(get("/api/me/goals"), a.accessToken()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetType").value("JOB"));
        send(get("/api/me/goals"), b.accessToken()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetValue").value("IELTS"));
        call(put("/api/me/goals"), a, Map.of("goals", List.of())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void putGoalsValidates() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        List<Object> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(goal("T", "v" + i));
        }
        call(put("/api/me/goals"), a, Map.of("goals", tooMany)).andExpect(status().isBadRequest());
        call(put("/api/me/goals"), a, Map.of("goals", List.of(Map.of("targetValue", "x")))).andExpect(status().isBadRequest());
        call(put("/api/me/goals"), a, Map.of("goals", List.of(goal("T", "x".repeat(101))))).andExpect(status().isBadRequest());
        call(put("/api/me/goals"), a, Map.of()).andExpect(status().isBadRequest());
    }
}
