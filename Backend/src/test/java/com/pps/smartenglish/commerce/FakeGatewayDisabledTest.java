package com.pps.smartenglish.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Không có cổng thanh toán nào: không có endpoint dev, không tạo được đơn. */
@TestPropertySource(properties = "app.payment.fake-enabled=false")
class FakeGatewayDisabledTest extends ApiTestBase {

    @Test
    void withoutGatewayThereIsNoDevEndpointAndOrdersReturn503() throws Exception {
        var u = register(newEmail(), "Password123!");
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", u.id());

        send(MockMvcRequestBuilders.post("/api/dev/payments/ORD-AAAAAA/pay"), u.accessToken()).andExpect(status().isNotFound());

        var req = MockMvcRequestBuilders.post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(json.writeValueAsString(Map.of("productIds", java.util.List.of(1))));
        send(req, u.accessToken()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("payment_unavailable"));
        assertEquals(0, count("select count(*) from orders where user_id = ?", u.id()));
        assertEquals(0, count("select count(*) from idempotency_keys where user_id = ?", u.id()));

        post("/api/payments/webhook/fake", Map.of("orderCode", "x"), newIp()).andExpect(status().isNotFound());
    }
}
