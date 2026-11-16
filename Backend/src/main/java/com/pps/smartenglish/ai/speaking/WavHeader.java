package com.pps.smartenglish.ai.speaking;

import java.nio.charset.StandardCharsets;

/** Đọc header WAV PCM 16-bit để lấy thời lượng thật, không tin vào Content-Type do client khai. */
final class WavHeader {

    private WavHeader() {}

    /** head: vài KB đầu file; totalSize: dung lượng object. Sai định dạng thì IllegalArgumentException. */
    static double durationSeconds(byte[] head, long totalSize) {
        if (head.length < 12 || !tag(head, 0, "RIFF") || !tag(head, 8, "WAVE")) {
            throw new IllegalArgumentException("Không phải RIFF/WAVE");
        }
        long byteRate = 0;
        int pos = 12;
        while (pos + 8 <= head.length) {
            long size = u32(head, pos + 4);
            int body = pos + 8;
            if (tag(head, pos, "fmt ")) {
                if (size < 16 || body + 16 > head.length || u16(head, body) != 1 || u16(head, body + 14) != 16) {
                    throw new IllegalArgumentException("Chỉ nhận WAV PCM 16-bit");
                }
                byteRate = u32(head, body + 8);
            } else if (tag(head, pos, "data")) {
                if (byteRate <= 0) {
                    throw new IllegalArgumentException("Thiếu chunk fmt trước data");
                }
                long available = totalSize - body;
                // 0 hoặc 0xFFFFFFFF là độ dài chưa ghi của bản ghi dạng luồng: lấy theo dung lượng thật
                long dataLen = size == 0 || size > available ? available : size;
                return dataLen / (double) byteRate;
            }
            if (size > head.length) {
                break;
            }
            pos = (int) (body + size + (size & 1));
        }
        throw new IllegalArgumentException("Không tìm thấy chunk data");
    }

    private static boolean tag(byte[] b, int at, String t) {
        return at + 4 <= b.length && new String(b, at, 4, StandardCharsets.US_ASCII).equals(t);
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
    }
}
