package com.pps.smartenglish.media.service;

import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * D-159: thời lượng mp4 đọc từ atom mvhd trong moov (không tin client). Đi qua các box cấp cao bằng Range GET, mỗi box chỉ đọc 16 byte
 * đầu, nên moov ở đầu hay cuối file đều được; giới hạn số box và dung lượng moov để file hỏng không kéo cả file về.
 */
final class Mp4Duration {

    private static final int MAX_BOXES = 64;
    private static final int MAX_MOOV_BYTES = 16 << 20;
    private static final long MAX_MS = 24L * 3600 * 1000;

    private Mp4Duration() {}

    static int millis(ObjectStorage storage, String key, long fileSize) {
        long off = 0;
        for (int i = 0; i < MAX_BOXES && off + 8 <= fileSize; i++) {
            byte[] h = storage.readRange(key, off, (int) Math.min(16, fileSize - off));
            long size = u32(h, 0);
            int header = 8;
            if (size == 1 && h.length >= 16) {
                size = u64(h, 8);
                header = 16;
            } else if (size == 0) {
                size = fileSize - off;
            }
            if (size < header || off + size > fileSize) {
                break;
            }
            if (type(h, 4).equals("moov")) {
                if (size - header > MAX_MOOV_BYTES) {
                    break;
                }
                return fromMoov(storage.readRange(key, off + header, (int) (size - header)));
            }
            off += size;
        }
        throw unreadable();
    }

    private static int fromMoov(byte[] b) {
        int pos = 0;
        while (pos + 8 <= b.length) {
            long size = u32(b, pos);
            if (size < 8 || pos + size > b.length) {
                break;
            }
            if (type(b, pos + 4).equals("mvhd")) {
                if (pos + 9 > b.length) {
                    break;
                }
                boolean v1 = b[pos + 8] == 1;
                int tsAt = pos + (v1 ? 28 : 20);
                int durAt = pos + (v1 ? 32 : 24);
                if (durAt + (v1 ? 8 : 4) > pos + size || durAt + (v1 ? 8 : 4) > b.length) {
                    break;
                }
                long timescale = u32(b, tsAt);
                long duration = v1 ? u64(b, durAt) : u32(b, durAt);
                // duration u64 có thể >= 2^63 (âm) hoặc làm phép nhân tràn: coi là không đọc được
                if (timescale == 0 || duration < 0 || duration > MAX_MS * timescale / 1000 + timescale) {
                    break;
                }
                long ms = duration * 1000 / timescale;
                if (ms <= 0 || ms > MAX_MS) {
                    break;
                }
                return (int) ms;
            }
            pos += (int) size;
        }
        throw unreadable();
    }

    private static ApiException unreadable() {
        return new ApiException(HttpStatus.BAD_REQUEST, "video_duration_unreadable", "Không đọc được thời lượng video MP4");
    }

    private static String type(byte[] b, int at) {
        return new String(b, at, 4, StandardCharsets.ISO_8859_1);
    }

    private static long u32(byte[] b, int at) {
        return ((b[at] & 0xFFL) << 24) | ((b[at + 1] & 0xFFL) << 16) | ((b[at + 2] & 0xFFL) << 8) | (b[at + 3] & 0xFFL);
    }

    private static long u64(byte[] b, int at) {
        return (u32(b, at) << 32) | u32(b, at + 4);
    }
}
