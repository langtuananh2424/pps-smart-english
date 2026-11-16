package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.dto.AdminOrderView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn đọc cho trang quản trị đơn (FR-ADM-03). JOIN users/user_profiles của auth chỉ để lấy email và tên người mua:
 * cùng ngoại lệ chỉ-đọc như OrderRepository.conversionCounts. JdbcTemplate để không phụ thuộc kiểu thời gian của Hibernate.
 */
@Repository
public class AdminOrderRepository {

    /** Một dòng CSV: mỗi order_items là một dòng. */
    public record ExportRow(long itemId, String orderCode, OffsetDateTime paidAt, String email, String displayName,
                            String productName, String productType, long priceVnd, String provider, String providerRef) {}

    private final JdbcTemplate jdbc;

    AdminOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lọc rỗng (null) thì bỏ qua; emailLike đã escape bằng '\' và có sẵn %. Lấy size+1 dòng để biết còn trang sau. */
    public List<AdminOrderView> search(long beforeId, String status, Instant start, Instant end, String emailLike, int limit) {
        var sql = new StringBuilder("""
                SELECT o.id, o.code, o.status, o.total_vnd, o.paid_at, o.created_at, u.email, p.display_name, o.provider
                FROM orders o JOIN users u ON u.id = o.user_id LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE o.id < ?
                """);
        List<Object> args = new ArrayList<>(List.of(beforeId));
        if (status != null) {
            sql.append(" AND o.status = ?");
            args.add(status);
        }
        if (start != null) {
            sql.append(" AND o.created_at >= ?");
            args.add(utc(start));
        }
        if (end != null) {
            sql.append(" AND o.created_at < ?");
            args.add(utc(end));
        }
        if (emailLike != null) {
            sql.append(" AND LOWER(u.email) LIKE ? ESCAPE '!'");
            args.add(emailLike);
        }
        sql.append(" ORDER BY o.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, n) -> new AdminOrderView(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), instant(rs.getObject(5, OffsetDateTime.class)), instant(rs.getObject(6, OffsetDateTime.class)),
                rs.getString(7), rs.getString(8), rs.getString(9)), args.toArray());
    }

    /** Keyset theo order_items.id tăng dần: mỗi lần một lô nhỏ, không giữ kết nối hay con trỏ trong lúc ghi ra client. */
    public List<ExportRow> paidItemsAfter(long afterItemId, Instant start, Instant end, int limit) {
        return jdbc.query("""
                SELECT i.id, o.code, o.paid_at, u.email, p.display_name, pr.name, pr.type, i.price_vnd, o.provider, pt.provider_ref
                FROM order_items i
                JOIN orders o ON o.id = i.order_id
                JOIN products pr ON pr.id = i.product_id
                JOIN users u ON u.id = o.user_id
                LEFT JOIN user_profiles p ON p.user_id = u.id
                LEFT JOIN payment_transactions pt ON pt.order_id = o.id AND pt.status = 'APPLIED'
                WHERE o.status = 'PAID' AND o.paid_at >= ? AND o.paid_at < ? AND i.id > ?
                ORDER BY i.id LIMIT ?
                """, (rs, n) -> new ExportRow(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                rs.getString(10)), utc(start), utc(end), afterItemId, limit);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
