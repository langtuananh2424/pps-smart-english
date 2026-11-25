package com.pps.smartenglish.ai.speaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** D-145: app.speaking.band-max ngoài (0, 10] thì bind cấu hình lỗi, app không khởi động. */
class SpeakingBandMaxConfigTest {

    private static SpeakingProperties bind(String bandMax) {
        var src = new MapConfigurationPropertySource(Map.of("app.speaking.band-max", bandMax));
        return new Binder(src).bindOrCreate("app.speaking", SpeakingProperties.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.01", "NaN", "Infinity"})
    void outOfRangeBandMaxFailsBinding(String v) {
        assertThrows(BindException.class, () -> bind(v));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "9", "10"})
    void inRangeBandMaxBinds(String v) {
        assertEquals(Double.parseDouble(v), bind(v).bandMax());
    }

    @Test
    void defaultIsNine() {
        assertEquals(9.0, new Binder(new MapConfigurationPropertySource(Map.of())).bindOrCreate("app.speaking", SpeakingProperties.class).bandMax());
    }
}
