package com.pps.smartenglish.support;

import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.shared.mail.EmailSender;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Redis luôn "chết" trong test (kiểm chứng đường fallback bộ nhớ); mailer là mock để lấy token từ link.
 * Mỗi test dùng email và IP riêng vì DB H2 và bộ đếm dùng chung giữa các test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    public record Account(String email, long id, String accessToken, String refreshToken) {}

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @MockitoBean protected StringRedisTemplate redis;
    @MockitoBean protected EmailSender sender;

    @BeforeEach
    void redisDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        when(redis.delete(Mockito.anyString())).thenThrow(new RedisConnectionFailureException("down"));
    }

    protected static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected static String newIp() {
        var r = java.util.concurrent.ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    protected ResultActions post(String url, Object body, String ip, Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).header("X-Real-IP", ip);
        if (cookies.length > 0) {
            req.cookie(cookies);
        }
        return mvc.perform(req);
    }

    protected ResultActions send(MockHttpServletRequestBuilder req, String accessToken) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + accessToken));
    }

    protected Account register(String email, String password) throws Exception {
        var res = post("/api/auth/register", java.util.Map.of("email", email, "password", password, "displayName", "Học viên"),
                newIp()).andReturn().getResponse();
        return account(email, res.getStatus(), res.getContentAsString(), res.getCookie("refresh_token"));
    }

    protected Account login(String email, String password) throws Exception {
        var res = post("/api/auth/login", java.util.Map.of("identifier", email, "password", password), newIp())
                .andReturn().getResponse();
        return account(email, res.getStatus(), res.getContentAsString(), res.getCookie("refresh_token"));
    }

    private Account account(String email, int status, String body, Cookie cookie) throws Exception {
        if (status >= 300) {
            throw new AssertionError("Đăng nhập/đăng ký thất bại " + status + ": " + body);
        }
        JsonNode n = json.readTree(body);
        return new Account(email, n.at("/user/id").asLong(), n.get("accessToken").asText(), cookie.getValue());
    }

    /** Token trong link của thư xác thực gần nhất gửi tới email. */
    protected String verificationTokenFor(String email) {
        return lastToken(email, "/verify-email?token=");
    }

    /** Thư đặt lại gửi bất đồng bộ nên chờ tối đa 2 giây. */
    protected String resetTokenFor(String email) {
        return lastToken(email, "/reset-password?token=");
    }

    private String lastToken(String email, String linkPart) {
        var captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(sender, Mockito.timeout(2000).atLeastOnce())
                .send(Mockito.eq(email), Mockito.anyString(), Mockito.contains(linkPart));
        Mockito.verify(sender, Mockito.atLeastOnce()).send(Mockito.eq(email), Mockito.anyString(), captor.capture());
        String body = captor.getAllValues().stream().filter(t -> t.contains(linkPart)).reduce((a, b) -> b).orElseThrow();
        var m = java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
        m.find();
        return m.group(1);
    }

    protected int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    protected void expireTokens(String table, long userId) {
        jdbc.update("update " + table + " set expires_at = ? where user_id = ?",
                java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z"), userId);
    }
}
