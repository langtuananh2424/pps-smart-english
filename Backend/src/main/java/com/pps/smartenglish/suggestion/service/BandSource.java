package com.pps.smartenglish.suggestion.service;

import com.pps.smartenglish.suggestion.dto.SuggestionDtos.Basis;
import java.math.BigDecimal;
import java.util.Optional;

/** Strategy: một nguồn cho band hiện tại của học viên; nguồn đứng trước (@Order) thắng (D-56). */
interface BandSource {

    record Reading(Basis basis, BigDecimal band) {}

    Optional<Reading> read(long userId);
}
