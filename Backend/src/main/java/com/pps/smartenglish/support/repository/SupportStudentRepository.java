package com.pps.smartenglish.support.repository;

import com.pps.smartenglish.auth.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Tra cứu cho nhân viên hỗ trợ (FR-SUP-03). Truy vấn đọc nhiều module nên là native, giống truy vấn báo cáo;
 * chỉ trả cột cần hiển thị, không bao giờ trả password_hash.
 */
public interface SupportStudentRepository extends Repository<User, Long> {

    /** [id, email, tên hiển thị, trạng thái, email_verified_at]; chỉ tài khoản chỉ có role STUDENT (nhân sự không hiện ra); q khớp email, tên hoặc mã đơn. */
    @Query(nativeQuery = true, value = """
            SELECT u.id, u.email, p.display_name, u.status, u.email_verified_at
            FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id
            WHERE u.id IN (SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = 'STUDENT')
              AND NOT EXISTS (SELECT 1 FROM user_roles ur2 JOIN roles r2 ON r2.id = ur2.role_id WHERE ur2.user_id = u.id AND r2.code <> 'STUDENT')
              AND (LOWER(u.email) LIKE :like ESCAPE '\\' OR LOWER(p.display_name) LIKE :like ESCAPE '\\'
                   OR u.id IN (SELECT o.user_id FROM orders o WHERE o.code = :code))
            ORDER BY u.id DESC LIMIT :max
            """)
    List<Object[]> search(@Param("like") String like, @Param("code") String code, @Param("max") int max);

    /** [id, email, tên hiển thị, trạng thái, email_verified_at, created_at] của một học viên. */
    @Query(nativeQuery = true, value = """
            SELECT u.id, u.email, p.display_name, u.status, u.email_verified_at, u.created_at
            FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id
            WHERE u.id = :id AND u.id IN (SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = 'STUDENT')
              AND NOT EXISTS (SELECT 1 FROM user_roles ur2 JOIN roles r2 ON r2.id = ur2.role_id WHERE ur2.user_id = u.id AND r2.code <> 'STUDENT')
            """)
    List<Object[]> findStudent(@Param("id") long id);

    /** [mã khóa, tên khóa, trạng thái, bắt đầu, hết hạn]. */
    @Query(nativeQuery = true, value = """
            SELECT c.id, c.title, e.status, e.starts_at, e.expires_at
            FROM user_course_enrollments e JOIN courses c ON c.id = e.course_id
            WHERE e.user_id = :id ORDER BY e.id
            """)
    List<Object[]> enrollments(@Param("id") long id);

    /** [mã đơn, trạng thái, tổng tiền, thanh toán lúc, tạo lúc], mới nhất trước. */
    @Query(nativeQuery = true, value = """
            SELECT o.code, o.status, o.total_vnd, o.paid_at, o.created_at
            FROM orders o WHERE o.user_id = :id ORDER BY o.id DESC LIMIT :max
            """)
    List<Object[]> recentOrders(@Param("id") long id, @Param("max") int max);
}
