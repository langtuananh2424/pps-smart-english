package com.pps.smartenglish.media.repository;

import com.pps.smartenglish.media.domain.MediaAsset;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** File chưa xử lý xong và đã quá hạn tải lên trước cutoff. */
    @Query("select a from MediaAsset a where a.status = 'PENDING' and a.uploadExpiresAt < :cutoff order by a.id")
    List<MediaAsset> findStalePending(Instant cutoff, Pageable page);
}
