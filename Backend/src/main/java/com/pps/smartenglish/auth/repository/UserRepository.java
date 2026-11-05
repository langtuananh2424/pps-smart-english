package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.User;
import java.time.Instant;
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
}
