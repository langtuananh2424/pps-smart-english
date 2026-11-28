package com.pps.smartenglish.lessoncontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** P0 (dữ liệu lớn): DictionaryLoader ghi theo lô trong lúc còn đang đọc, không gom cả file rồi mới ghi. */
class D175DictionaryStreamingTest {

    /** Sinh dòng JSONL khi được đọc; ở mỗi mốc 1000 dòng ghi lại số lần batchUpdate đã gọi. */
    static final class LazyJsonl extends InputStream {
        private final int lines;
        private final JdbcTemplate jdbc;
        final List<Long> flushesAtCheckpoint = new ArrayList<>();
        private int next;
        private byte[] buf = new byte[0];
        private int pos;

        LazyJsonl(int lines, JdbcTemplate jdbc) {
            this.lines = lines;
            this.jdbc = jdbc;
        }

        @Override
        public int read() {
            if (pos == buf.length) {
                if (next == lines) {
                    return -1;
                }
                if (next > 0 && next % 1000 == 0) {
                    flushesAtCheckpoint.add(mockingDetails(jdbc).getInvocations().stream()
                            .filter(i -> i.getMethod().getName().equals("batchUpdate")).count());
                }
                buf = ("{\"word\":\"w" + next + "\",\"lang_code\":\"en\",\"pos\":\"noun\",\"senses\":[{\"glosses\":[\"nghĩa " + next
                        + "\"]}]}\n").getBytes(StandardCharsets.UTF_8);
                pos = 0;
                next++;
            }
            return buf[pos++] & 0xff;
        }
    }

    @Test
    void d175FlushesEveryThousandRowsWhileStillReading() {
        var jdbc = mock(JdbcTemplate.class);
        var loader = new DictionaryLoader(jdbc, new TransactionTemplate(mock(PlatformTransactionManager.class)), new ObjectMapper());
        var in = new LazyJsonl(5_500, jdbc);
        var r = loader.load(in, "vi");
        assertEquals(5_500, r.read());
        assertEquals(5_500, r.written());
        assertEquals(5, in.flushesAtCheckpoint.size());
        // Khi bắt đầu sinh dòng thứ k*1000+1, ít nhất k-1 lô đã được ghi: bộ đệm không vượt quá một lô.
        for (int k = 1; k <= 5; k++) {
            assertTrue(in.flushesAtCheckpoint.get(k - 1) >= k - 1, "mốc " + k + ": " + in.flushesAtCheckpoint);
        }
    }
}
