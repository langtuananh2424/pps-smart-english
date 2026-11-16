package com.pps.smartenglish.suggestion.service;

import com.pps.smartenglish.placement.domain.PlacementAttempt;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.Basis;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Band của lượt kiểm tra đầu vào đã nộp gần nhất. */
@Component
@Order(1)
class PlacementBandSource implements BandSource {

    private final PlacementAttemptRepository attempts;

    PlacementBandSource(PlacementAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public Optional<Reading> read(long userId) {
        return attempts.findFirstByUserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescIdDesc(userId)
                .map(PlacementAttempt::getOverallBand)
                .map(band -> new Reading(Basis.PLACEMENT, band));
    }
}
