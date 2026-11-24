package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.ImportBatch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    /** Mới nhất trước; lịch sử nhập của một bộ. */
    List<ImportBatch> findTop50ByBankIdOrderByIdDesc(Long bankId);

    /** Khóa dòng lô để hai lần hoàn tác đồng thời chạy tuần tự (lần sau thấy đã hoàn tác). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ImportBatch b where b.id = :id")
    Optional<ImportBatch> lockById(@Param("id") Long id);
}
