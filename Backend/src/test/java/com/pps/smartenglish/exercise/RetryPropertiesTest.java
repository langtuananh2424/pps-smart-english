package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pps.smartenglish.exercise.config.RetryProperties;
import org.junit.jupiter.api.Test;

class RetryPropertiesTest {

    @Test
    void hintAfterWrongAboveMaxRetriesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RetryProperties(2, 3, 60));
    }

    @Test
    void validCombinationsPass() {
        assertDoesNotThrow(() -> new RetryProperties(2, 2, 60));
        assertDoesNotThrow(() -> new RetryProperties(0, 2, 60));
    }
}
