package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.EmailToken;
import com.pps.smartenglish.auth.domain.EmailToken.Purpose;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    /** Dùng một lần: chỉ một request thắng khi cùng token được gửi song song. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.tokenHash = :hash and t.purpose = :purpose "
            + "and t.usedAt is null and t.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("purpose") Purpose purpose, @Param("now") Instant now);
}
