package com.pps.smartenglish.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** D-156, D-159 (tester): đọc thời lượng mvhd từ file mp4 tải lên; file hỏng phải trả 400 video_duration_unreadable, không 500. */
class Mp4DurationTest {

    private final FakeStorage storage = new FakeStorage();

    private static byte[] box(String type, byte[] body) {
        return ByteBuffer.allocate(8 + body.length).putInt(8 + body.length).put(type.getBytes(StandardCharsets.ISO_8859_1)).put(body).array();
    }

    private static byte[] ftyp() {
        return box("ftyp", "isom\0\0\0\0isomavc1".getBytes(StandardCharsets.ISO_8859_1));
    }

    /** mvhd v0: version/flags(4) creation(4) modification(4) timescale(4) duration(4) + phần còn lại. */
    private static byte[] mvhdV0(long timescale, long duration) {
        return box("mvhd", ByteBuffer.allocate(100).putInt(0).putInt(0).putInt(0).putInt((int) timescale).putInt((int) duration).array());
    }

    /** mvhd v1: version/flags(4) creation(8) modification(8) timescale(4) duration(8) + phần còn lại. */
    private static byte[] mvhdV1(long timescale, long duration) {
        return box("mvhd", ByteBuffer.allocate(112).putInt(1 << 24).putLong(0).putLong(0).putInt((int) timescale).putLong(duration).array());
    }

    private static byte[] cat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private int millis(byte[] file) {
        storage.put("k", file);
        return Mp4Duration.millis(storage, "k", file.length);
    }

    private void unreadable(byte[] file) {
        var e = assertThrows(ApiException.class, () -> millis(file));
        assertEquals("video_duration_unreadable", e.getCode());
    }

    @Test
    void moovAtStartVersion0() {
        assertEquals(65_000, millis(cat(ftyp(), box("moov", mvhdV0(1000, 65_000)), box("mdat", new byte[64]))));
    }

    @Test
    void moovAfterMdatVersion1() {
        assertEquals(90_000, millis(cat(ftyp(), box("mdat", new byte[4096]), box("moov", cat(box("trak", new byte[16]), mvhdV1(600, 600 * 90))))));
    }

    @Test
    void largeSizeMdatBeforeMoov() {
        byte[] payload = new byte[256];
        byte[] mdat = ByteBuffer.allocate(16 + payload.length).putInt(1).put("mdat".getBytes(StandardCharsets.ISO_8859_1))
                .putLong(16 + payload.length).put(payload).array();
        assertEquals(12_000, millis(cat(ftyp(), mdat, box("moov", mvhdV0(1000, 12_000)))));
    }

    @Test
    void noMoovOrGarbageIsUnreadable() {
        unreadable(cat(ftyp(), box("mdat", new byte[64])));
        unreadable(new byte[] {0, 0, 0, 2, 'm', 'o', 'o', 'v'});
        unreadable(cat(ftyp(), box("moov", mvhdV0(0, 1000)))); // timescale 0
    }

    /** mvhd chỉ có header 8 byte ở cuối moov: không được ném ArrayIndexOutOfBounds (500, file không bị xóa). */
    @Test
    void truncatedMvhdAtEndOfMoovIsUnreadable() {
        unreadable(cat(ftyp(), box("moov", box("mvhd", new byte[0]))));
    }

    /** duration v1 cực lớn: duration * 1000 tràn số thành vài trăm ms thì quiz video mở sau 1 giây xem. */
    @Test
    void hugeVersion1DurationDoesNotOverflowIntoShortVideo() {
        unreadable(cat(ftyp(), box("moov", mvhdV1(1, 18_446_744_073_709_552L))));
    }
}
