package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.PermissionOverride;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionOverrideRepository extends JpaRepository<PermissionOverride, Long> {

    Optional<PermissionOverride> findByUserIdAndPermissionId(Long userId, Long permissionId);

    List<PermissionOverride> findByUserIdOrderByIdAsc(Long userId);
}
