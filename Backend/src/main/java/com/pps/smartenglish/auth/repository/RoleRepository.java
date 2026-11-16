package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findAllByOrderByIdAsc();

    boolean existsByCode(String code);
}
