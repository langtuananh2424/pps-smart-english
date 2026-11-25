package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.dto.AdminOrderDetail;
import com.pps.smartenglish.commerce.dto.AdminOrderDetail.Buyer;
import com.pps.smartenglish.commerce.dto.AdminOrderDetail.Item;
import com.pps.smartenglish.commerce.dto.AdminOrderDetail.Transaction;
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

    /** Trạng thái hiệu lực: PENDING quá hạn tính là EXPIRED dù job hủy chưa chạy (cùng ShopOrder.effectiveStatus). */
    private static final String EFFECTIVE = "CASE WHEN o.status = 'PENDING' AND o.expires_at <= ? THEN 'EXPIRED' ELSE o.status END";

    /**
     * Lọc rỗng (null) thì bỏ qua; emailLike đã escape bằng '!' và có sẵn %. Lấy size+1 dòng để biết còn trang sau.
     * status lọc theo trạng thái hiệu lực: PENDING không gồm đơn quá hạn, EXPIRED gồm cả PENDING quá hạn.
     */
    public List<AdminOrderView> search(long beforeId, String status, Instant now, Instant start, Instant end, String emailLike, int limit) {
        var sql = new StringBuilder("SELECT o.id, o.code, " + EFFECTIVE + """
                , o.total_vnd, o.paid_at, o.created_at, u.email, p.display_name, o.provider
                FROM orders o JOIN users u ON u.id = o.user_id LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE o.id < ?
                """);
        List<Object> args = new ArrayList<>(List.of(utc(now), beforeId));
        if (status != null) {
            sql.append(" AND " + EFFECTIVE + " = ?");
            args.add(utc(now));
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

    /** Đầu đơn kèm người mua; items và transactions do service ghép từ hai truy vấn dưới. */
    public java.util.Optional<AdminOrderDetail> findDetail(long id, Instant now) {
        return jdbc.query("SELECT o.id, o.code, " + EFFECTIVE + """
                , o.total_vnd, o.provider, o.created_at, o.expires_at, o.paid_at,
                       u.id, u.email, p.display_name
                FROM orders o JOIN users u ON u.id = o.user_id LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE o.id = ?
                """, (rs, n) -> new AdminOrderDetail(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                rs.getString(5), instant(rs.getObject(6, OffsetDateTime.class)), instant(rs.getObject(7, OffsetDateTime.class)),
                instant(rs.getObject(8, OffsetDateTime.class)), new Buyer(rs.getLong(9), rs.getString(10), rs.getString(11)),
                List.of(), List.of()), utc(now), id).stream().findFirst();
    }

    public List<Item> items(long orderId) {
        return jdbc.query("""
                SELECT pr.name, pr.type, i.price_vnd,
                       (SELECT string_agg(DISTINCT c.title, ', ') FROM product_levels pl
                        JOIN course_levels l ON l.id = pl.level_id JOIN courses c ON c.id = l.course_id
                        WHERE pl.product_id = pr.id)
                FROM order_items i JOIN products pr ON pr.id = i.product_id
                WHERE i.order_id = ? ORDER BY i.id
                """, (rs, n) -> new Item(rs.getString(1), rs.getString(2), rs.getString(4), rs.getLong(3)), orderId);
    }

    /** Không chọn raw_payload. */
    public List<Transaction> transactions(long orderId) {
        return jdbc.query("""
                SELECT received_at, provider, provider_ref, amount_vnd, status
                FROM payment_transactions WHERE order_id = ? ORDER BY received_at, id
                """, (rs, n) -> new Transaction(instant(rs.getObject(1, OffsetDateTime.class)), rs.getString(2),
                rs.getString(3), rs.getLong(4), rs.getString(5)), orderId);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
