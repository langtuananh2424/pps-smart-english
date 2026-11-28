package com.pps.smartenglish.lessoncontent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pps.smartenglish.support.ApiTestBase;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** P0 (dữ liệu lớn): nạp file JSONL nhiều dòng (sinh dần, không có sẵn trong RAM), có dòng hỏng và dòng ngôn ngữ khác. */
class D175DictionaryLargeImportTest extends ApiTestBase {

    @Autowired DictionaryLoader loader;

    @Test
    void d175ImportsFiftyThousandLinesWithBadAndForeignRows() {
        int n = 50_000;
        InputStream in = new InputStream() {
            private int line;
            private byte[] buf = new byte[0];
            private int pos;

            @Override
            public int read() {
                if (pos == buf.length) {
                    if (line == n) {
                        return -1;
                    }
                    String s = line % 100 == 0 ? "{not json\n"
                            : line % 100 == 1 ? "{\"word\":\"fr" + line + "\",\"lang_code\":\"fr\",\"senses\":[{\"glosses\":[\"x\"]}]}\n"
                            : "{\"word\":\"big" + line + "\",\"lang_code\":\"en\",\"senses\":[{\"glosses\":[\"nghĩa\"]}]}\n";
                    buf = s.getBytes(StandardCharsets.UTF_8);
                    pos = 0;
                    line++;
                }
                return buf[pos++] & 0xff;
            }
        };
        var r = loader.load(in, "vi");
        assertEquals(n, r.read());
        assertEquals(n - 2 * (n / 100), r.written());
        assertEquals(n - 2 * (n / 100), count("select count(*) from dictionary_entries"));
        jdbc.update("delete from dictionary_entries");
    }
}
