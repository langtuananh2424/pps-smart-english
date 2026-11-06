package com.pps.smartenglish.retention.repository;

import com.pps.smartenglish.retention.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdOrderByIdAsc(Long userId);

    /** Chỉ xóa của chính mình: endpoint của người khác không bị đụng tới. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.userId = :userId")
    int deleteOwned(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    /** Endpoint hết hiệu lực (404/410): xóa theo id, không cần biết chủ. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushSubscription s where s.id = :id")
    int deleteBySubscriptionId(@Param("id") Long id);
}
