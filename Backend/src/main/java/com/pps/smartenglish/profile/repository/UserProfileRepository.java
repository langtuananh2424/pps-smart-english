package com.pps.smartenglish.profile.repository;

import com.pps.smartenglish.profile.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {}
