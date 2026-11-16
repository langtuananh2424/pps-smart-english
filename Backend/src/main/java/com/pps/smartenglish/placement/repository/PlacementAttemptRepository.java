package com.pps.smartenglish.placement.repository;

import com.pps.smartenglish.placement.domain.PlacementAttempt;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAttemptRepository extends JpaRepository<PlacementAttempt, Long> {

    /** Khóa dòng để nộp/claim/lưu nháp của cùng một lượt chạy tuần tự. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlacementAttempt a where a.id = :id")
    Optional<PlacementAttempt> lockById(@Param("id") Long id);

    /** Khóa dòng user để start/claim song song của cùng một người không vượt số lượt. */
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    List<Long> lockUser(@Param("userId") Long userId);

    Optional<PlacementAttempt> findFirstByUserIdAndSubmittedAtIsNullOrderByIdDesc(Long userId);

    long countByUserIdAndSubmittedAtIsNotNull(Long userId);

    Optional<PlacementAttempt> findFirstByUserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(Long userId);

    /** Lượt chưa nộp mà deadline đã qua trước mốc cutoff (đã trừ ân hạn); lấy theo lô để job không nạp quá nhiều. */
    @Query("select a.id from PlacementAttempt a where a.submittedAt is null and a.deadlineAt < :cutoff order by a.id")
    List<Long> findOverdueIds(@Param("cutoff") Instant cutoff, Pageable page);

    /** Lượt của khách (chưa gắn user) chưa nộp, bắt đầu trước mốc cutoff. */
    @Query("select a.id from PlacementAttempt a where a.userId is null and a.submittedAt is null and a.startedAt < :cutoff order by a.id")
    List<Long> findGuestAbandonedIds(@Param("cutoff") Instant cutoff, Pageable page);

    /** Xóa kèm lại điều kiện: lượt vừa được nộp hoặc claim giữa lúc chọn và xóa thì giữ nguyên. Bảng không có dữ liệu con. */
    @Modifying
    @Query("delete from PlacementAttempt a where a.id in :ids and a.userId is null and a.submittedAt is null")
    int deleteGuestAbandoned(@Param("ids") List<Long> ids);
}
