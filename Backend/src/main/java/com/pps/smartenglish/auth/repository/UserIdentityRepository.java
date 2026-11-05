package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.UserIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
