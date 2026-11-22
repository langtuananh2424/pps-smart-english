package com.pps.smartenglish.media.service;

import com.pps.smartenglish.media.config.MediaProperties;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

/**
 * D-134: ảnh tải lên -> WebP, cạnh dài tối đa maxEdgePx (không phóng to), không EXIF (ghi lại từ điểm ảnh nên metadata
 * rơi mất; hướng xoay EXIF được áp dụng khi đọc). Ảnh PNG/GIF (thường là chữ, biểu đồ) thử lossless trước; vượt targetBytes
 * thì lossy chất lượng quality, vẫn vượt thì fallbackQuality.
 */
@Component
public class ImageConverter {

    public record Converted(byte[] webp, int width, int height) {}

    /** Ảnh không đọc được hoặc quá lớn. */
    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    private final MediaProperties.Image cfg;

    ImageConverter(MediaProperties props) {
        this.cfg = props.image();
    }

    public Converted convert(byte[] source) {
        requireSaneSize(source);
        ImmutableImage image;
        try {
            image = ImmutableImage.loader().fromBytes(source);
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("Không đọc được file ảnh");
        }
        // max() của Scrimage phóng to ảnh nhỏ nên chỉ gọi khi ảnh thật sự vượt giới hạn
        if (Math.max(image.width, image.height) > cfg.maxEdgePx()) {
            image = image.max(cfg.maxEdgePx(), cfg.maxEdgePx());
        }
        byte[] out = null;
        try {
            if (isLossless(source)) {
                out = image.bytes(WebpWriter.DEFAULT.withLossless());
            }
            if (out == null || out.length > cfg.targetBytes()) {
                out = image.bytes(WebpWriter.DEFAULT.withQ(cfg.quality()));
            }
            if (out.length > cfg.targetBytes()) {
                out = image.bytes(WebpWriter.DEFAULT.withQ(cfg.fallbackQuality()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được ảnh WebP", e);
        }
        return new Converted(out, image.width, image.height);
    }

    /** Đọc kích thước từ header trước khi giải mã đầy đủ để chặn ảnh bom nén (vài chục nghìn px mỗi cạnh). */
    private void requireSaneSize(byte[] source) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidImageException("File không phải ảnh JPEG, PNG, GIF hoặc WebP");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > cfg.maxPixels()) {
                    throw new InvalidImageException("Ảnh quá lớn (vượt " + cfg.maxPixels() + " điểm ảnh)");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("Không đọc được file ảnh");
        }
    }

    /** PNG hoặc GIF theo chữ ký file. */
    private static boolean isLossless(byte[] b) {
        boolean png = b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean gif = b.length > 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
        return png || gif;
    }
}
