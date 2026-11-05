package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegisterLoginTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    @Test
    void registerCreatesUserProfileAndQuotaAndNormalizesEmail() throws Exception {
        String email = newEmail();
        var res = post("/api/auth/register", Map.of("email", "  " + email.toUpperCase() + " ", "password", PASSWORD,
                "displayName", "An"), newIp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.displayName").value("An"))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=2592000")))
                .andReturn();

        long id = json.readTree(res.getResponse().getContentAsString()).at("/user/id").asLong();
        assertThat(count("select count(*) from users where email = ?", email)).isEqualTo(1);
        assertThat(count("select count(*) from user_profiles where user_id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from user_ai_quota where user_id = ? and balance = 0", id)).isEqualTo(1);
        assertThat(count("select count(*) from email_tokens where user_id = ? and purpose = 'VERIFY'", id)).isEqualTo(1);
        assertThat(count("select count(*) from refresh_tokens where user_id = ?", id)).isEqualTo(1);
        assertThat(verificationTokenFor(email)).isNotBlank();
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        post("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "B"), newIp())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("conflict"));
    }

    @Test
    void invalidInputGivesValidationEnvelopeWithRequestId() throws Exception {
        post("/api/auth/register", Map.of("email", "khong-phai-email", "password", "ngan", "displayName", " "), newIp())
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.requestId", notNullValue()))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.error.details.email", notNullValue()))
                .andExpect(jsonPath("$.error.details.password", notNullValue()))
                .andExpect(jsonPath("$.error.details.displayName", notNullValue()));
    }

    @Test
    void passwordOver72BytesIsRejected() throws Exception {
        // 40 ký tự "ệ" = 120 byte UTF-8 nhưng chỉ 40 ký tự
        post("/api/auth/register", Map.of("email", newEmail(), "password", "ệ".repeat(40), "displayName", "A"), newIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.password", notNullValue()));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType("application/json").content("{khong-phai-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("malformed_request"));
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndCaseInsensitiveEmail() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        post("/api/auth/login", Map.of("identifier", email.toUpperCase(), "password", PASSWORD), newIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameGenericError() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        post("/api/auth/login", Map.of("identifier", email, "password", "sai-mat-khau"), newIp())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("unauthenticated"));
        post("/api/auth/login", Map.of("identifier", newEmail(), "password", PASSWORD), newIp())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void lockedAccountIsForbiddenOnlyAfterCorrectPassword() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        jdbc.update("update users set status = 'LOCKED' where email = ?", email);
        post("/api/auth/login", Map.of("identifier", email, "password", "sai-mat-khau"), newIp())
                .andExpect(status().isUnauthorized());
        post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), newIp())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void googleOnlyAccountCannotLoginWithPassword() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        jdbc.update("update users set password_hash = null where email = ?", email);
        post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), newIp())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fiveFailuresBlockEvenCorrectPasswordUntilWindowEndsAndAnotherIpIsNotAffected() throws Exception {
        // Redis đang "chết" nên đây cũng là kiểm chứng fallback theo IP trong bộ nhớ.
        String email = newEmail();
        register(email, PASSWORD);
        String ip = newIp();
        for (int i = 0; i < 5; i++) {
            post("/api/auth/login", Map.of("identifier", email, "password", "sai-" + i), ip).andExpect(status().isUnauthorized());
        }
        post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("rate_limited"))
                .andExpect(jsonPath("$.error.retryable").value(true));
        post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), newIp()).andExpect(status().isOk());
    }

    @Test
    void loginIsCappedPerIp() throws Exception {
        // Đăng nhập đúng xóa bộ đếm theo (email, IP) nhưng không xóa trần theo IP; Redis "chết" nên
        // bộ đếm sai theo IP trong bộ nhớ dùng chung mọi email, vì vậy dùng một tài khoản đúng để chạm trần 25.
        String email = newEmail();
        register(email, PASSWORD);
        String ip = newIp();
        for (int i = 0; i < 25; i++) {
            post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), ip).andExpect(status().isOk());
        }
        post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), ip)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void registerIsCappedPerIp() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 15; i++) {
            post("/api/auth/register", Map.of("email", newEmail(), "password", PASSWORD, "displayName", "A"), ip)
                    .andExpect(status().isCreated());
        }
        post("/api/auth/register", Map.of("email", newEmail(), "password", PASSWORD, "displayName", "A"), ip)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("rate_limited"));
        post("/api/auth/register", Map.of("email", newEmail(), "password", PASSWORD, "displayName", "A"), newIp())
                .andExpect(status().isCreated());
    }

    @Test
    void parallelWrongPasswordsCannotExceedFiveAttempts() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        String ip = newIp();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return post("/api/auth/login", Map.of("identifier", email, "password", "sai-mat-khau"), ip)
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int unauthorized = 0;
        int limited = 0;
        for (var f : results) {
            int s = f.get();
            if (s == 401) unauthorized++;
            else if (s == 429) limited++;
        }
        pool.shutdown();
        assertThat(unauthorized).isEqualTo(5);
        assertThat(limited).isEqualTo(5);
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        String ip = newIp();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                post("/api/auth/login", Map.of("identifier", email, "password", "sai"), ip).andExpect(status().isUnauthorized());
            }
            post("/api/auth/login", Map.of("identifier", email, "password", PASSWORD), ip).andExpect(status().isOk());
        }
    }
}
