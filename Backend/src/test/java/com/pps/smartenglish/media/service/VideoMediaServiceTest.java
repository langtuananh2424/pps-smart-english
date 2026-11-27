package com.pps.smartenglish.media.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** D-156: luật nhận brand MP4 video, từ chối audio M4A. */
class VideoMediaServiceTest {

    private static byte[] ftyp(String brand) {
        byte[] b = new byte[32];
        System.arraycopy(("ftyp" + brand).getBytes(StandardCharsets.ISO_8859_1), 0, b, 4, 8);
        return b;
    }

    @Test
    void acceptsCommonVideoBrands() {
        for (String brand : new String[] {"isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "dash"}) {
            assertTrue(VideoMediaService.isMp4(ftyp(brand)), brand);
        }
    }

    @Test
    void rejectsAudioAndNonMp4() {
        for (String brand : new String[] {"M4A ", "M4B ", "qt  ", "3gp4"}) {
            assertFalse(VideoMediaService.isMp4(ftyp(brand)), brand);
        }
        assertFalse(VideoMediaService.isMp4("not a video at all".getBytes(StandardCharsets.UTF_8)));
        assertFalse(VideoMediaService.isMp4(new byte[4]));
    }
}
