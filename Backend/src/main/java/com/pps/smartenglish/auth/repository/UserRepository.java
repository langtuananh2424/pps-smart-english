package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Điều kiện email_verified_at IS NULL giữ nguyên thời điểm xác thực đầu tiên. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.emailVerifiedAt = :now where u.id = :id and u.emailVerifiedAt is null")
    int markVerified(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Học viên đăng ký trong [start, end) theo ngày của múi giờ zone: [ngày, số đăng ký, số đã xác thực email].
     * Gom ngày bằng AT TIME ZONE ngay trong SQL; truy vấn con để GROUP BY theo cột đã tính, không lặp biểu thức
     * (mỗi tham số :zone là một tham số khác nhau, PostgreSQL sẽ không coi hai biểu thức là một).
     */
    @Query(nativeQuery = true, value = """
            SELECT d, COUNT(*), COUNT(verified_at) FROM (
                SELECT CAST(created_at AT TIME ZONE CAST(:zone AS varchar(64)) AS date) AS d, email_verified_at AS verified_at
                FROM users WHERE role = 'STUDENT' AND created_at >= :start AND created_at < :end) t
            GROUP BY d ORDER BY d
            """)
    List<Object[]> registrationsByDay(@Param("zone") String zone, @Param("start") Instant start, @Param("end") Instant end);
}
