package com.pps.smartenglish.media.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pps.smartenglish.media.config.MediaProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** D-134: ảnh đổi sang WebP, thu nhỏ khi quá cạnh dài, không phóng to, từ chối file không phải ảnh. Dùng cwebp thật của Scrimage. */
class ImageConverterTest {

    private static MediaProperties props(long maxPixels, long targetBytes) {
        return new MediaProperties(new MediaProperties.Image(10_485_760, 1600, 80, 70, targetBytes, maxPixels), new MediaProperties.Video(104_857_600), new MediaProperties.LessonVideo(314_572_800), 20,
                Duration.ofMinutes(120), Duration.ofMinutes(15), Duration.ofHours(24), false);
    }

    private static ImageConverter converter(long maxPixels, long targetBytes) {
        return new ImageConverter(props(maxPixels, targetBytes));
    }

    private static byte[] encode(int w, int h, String format, boolean noisy) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLUE);
        g.drawString("IELTS", 20, 20);
        g.dispose();
        if (noisy) { // giống ảnh chụp: dải màu cộng nhiễu nhẹ, lossless rất nặng còn lossy nhỏ hơn nhiều
            var r = new Random(1);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int c = Math.min(255, (x * 255 / w + r.nextInt(24)));
                    img.setRGB(x, y, new Color(c, (y * 255 / h + r.nextInt(24)) % 256, 255 - c).getRGB());
                }
            }
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static void assertWebp(byte[] b) {
        assertArrayEquals("RIFF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOfRange(b, 0, 4));
        assertArrayEquals("WEBP".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOfRange(b, 8, 12));
    }

    @Test
    void largeImageIsShrunkToTheLongEdgeLimitKeepingTheRatio() throws Exception {
        var out = converter(40_000_000, 307_200).convert(encode(3200, 2000, "png", false));
        assertWebp(out.webp());
        assertEquals(1600, out.width());
        assertEquals(1000, out.height());
    }

    @Test
    void smallImageIsNotEnlarged() throws Exception {
        var out = converter(40_000_000, 307_200).convert(encode(400, 300, "jpg", false));
        assertWebp(out.webp());
        assertEquals(400, out.width());
        assertEquals(300, out.height());
    }

    @Test
    void oversizedLosslessResultFallsBackToLossyAndStaysSmaller() throws Exception {
        byte[] noisyPng = encode(1200, 900, "png", true);
        var lossy = converter(40_000_000, 50_000).convert(noisyPng);
        var lossless = converter(40_000_000, Long.MAX_VALUE).convert(noisyPng);
        assertTrue(lossy.webp().length < lossless.webp().length);
    }

    @Test
    void nonImageAndPixelBombAreRejected() throws Exception {
        assertThrows(ImageConverter.InvalidImageException.class,
                () -> converter(40_000_000, 307_200).convert("not an image".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ImageConverter.InvalidImageException.class,
                () -> converter(10_000, 307_200).convert(encode(400, 300, "png", false)));
    }
}
