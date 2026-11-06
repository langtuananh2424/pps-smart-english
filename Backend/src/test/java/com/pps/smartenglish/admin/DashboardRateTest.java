package com.pps.smartenglish.admin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.admin.config.ReportProperties;
import com.pps.smartenglish.support.ApiTestBase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.TestPropertySource;

/** Có khai báo tỷ giá: chi phí AI được quy đổi VND, làm tròn tới đồng. */
@TestPropertySource(properties = "app.reports.usd-vnd-rate=25000.5")
class DashboardRateTest extends ApiTestBase {

    private long insert(String table, Map<String, Object> cols) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id")
                .usingColumns(cols.keySet().toArray(String[]::new)).executeAndReturnKey(cols).longValue();
    }

    @Test
    void costIsConvertedToVndWithTheConfiguredRate() throws Exception {
        var reg = register(newEmail(), "Password123!");
        jdbc.update("update users set role = 'ADMIN' where id = ?", reg.id());
        var admin = login(reg.email(), "Password123!");
        long job = insert("ai_jobs", Map.of("user_id", reg.id(), "kind", "WRITING_GRADE"));
        var when = OffsetDateTime.of(2007, 5, 10, 3, 0, 0, 0, ZoneOffset.UTC);
        for (String cost : new String[] {"1.000000", "0.500000"}) {
            insert("ai_grading_token_usage", Map.ofEntries(Map.entry("job_id", job), Map.entry("user_id", reg.id()),
                    Map.entry("provider", "fake"), Map.entry("model", "m"), Map.entry("input_tokens", 1), Map.entry("output_tokens", 1),
                    Map.entry("cache_read_tokens", 0), Map.entry("cache_write_tokens", 0), Map.entry("cost_usd", new BigDecimal(cost)),
                    Map.entry("created_at", when)));
        }
        // 1.5 USD * 25000.5 = 37500.75 => làm tròn 37501; chi phí từng loại làm tròn riêng, tổng tính từ tổng USD
        send(get("/api/admin/reports/dashboard?from=2007-05-01&to=2007-05-31"), admin.accessToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiCost.usdVndRate").value(25000.5))
                .andExpect(jsonPath("$.aiCost.totalUsd").value(1.5))
                .andExpect(jsonPath("$.aiCost.totalVnd").value(37501))
                .andExpect(jsonPath("$.aiCost.kinds[0].costVnd").value(37501));
    }

    @Test
    void rateMustBePositiveWhenDeclared() {
        assertThrows(IllegalArgumentException.class, () -> new ReportProperties(ZoneId.of("UTC"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReportProperties(ZoneId.of("UTC"), new BigDecimal("-1")));
        assertDoesNotThrow(() -> new ReportProperties(ZoneId.of("UTC"), null)); // để trống: không quy đổi
        assertDoesNotThrow(() -> new ReportProperties(ZoneId.of("UTC"), new BigDecimal("0.0001")));
        UUID.randomUUID();
    }
}
