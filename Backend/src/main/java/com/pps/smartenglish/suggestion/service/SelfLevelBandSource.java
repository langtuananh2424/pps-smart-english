package com.pps.smartenglish.suggestion.service;

import com.pps.smartenglish.profile.domain.UserProfile;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.suggestion.config.SuggestionProperties;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.Basis;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Chưa làm kiểm tra đầu vào: quy đổi trình độ tự đánh giá của hồ sơ sang band theo cấu hình. */
@Component
@Order(2)
class SelfLevelBandSource implements BandSource {

    private final UserProfileRepository profiles;
    private final SuggestionProperties props;

    SelfLevelBandSource(UserProfileRepository profiles, SuggestionProperties props) {
        this.profiles = profiles;
        this.props = props;
    }

    @Override
    public Optional<Reading> read(long userId) {
        return profiles.findById(userId).map(UserProfile::getSelfLevel)
                .map(level -> new Reading(Basis.SELF_LEVEL, props.bandOf(level)));
    }
}
