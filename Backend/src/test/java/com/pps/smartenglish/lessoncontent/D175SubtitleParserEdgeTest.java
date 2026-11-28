package com.pps.smartenglish.lessoncontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pps.smartenglish.lessoncontent.SubtitleParser.Cue;
import com.pps.smartenglish.lessoncontent.SubtitleParser.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ca biên parser phụ đề (D-175). */
class D175SubtitleParserEdgeTest {

    private static List<Cue> parse(String s) {
        return SubtitleParser.parse(s, 5000);
    }

    @Test
    void d175NoTrailingNewlineShortFractionAndBigHours() {
        assertEquals(List.of(new Cue(1_500, 2_250, "a"), new Cue(360_000_000L, 360_001_000L, "b")),
                parse("1\n00:00:01,5 --> 00:00:02,25\na\n\n2\n100:00:00,000 --> 100:00:01,000\nb"));
    }

    @Test
    void d175CueWithOnlyTagsIsDroppedNotCounted() {
        assertEquals(1, parse("WEBVTT\n\n00:00.000 --> 00:01.000\n<i></i>\n\n00:01.000 --> 00:02.000\nok\n").size());
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\n00:00.000 --> 00:01.000\n<i></i>\n"));
    }

    @Test
    void d175IdLineWithoutTimingIsRejected() {
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\nintro\n\n00:00.000 --> 00:01.000\nx\n"));
    }

    @Test
    void d175VietnameseTextAndEntitiesRoundTrip() {
        var cues = parse((char) 0xFEFF + "1\r\n00:00:00,000 --> 00:00:01,000\r\nXin chào &amp; tạm biệt 😀\r\n");
        assertEquals("Xin chào & tạm biệt 😀", cues.get(0).text());
        assertEquals(cues, parse(SubtitleParser.toVtt(cues)));
    }

    /** Giây >= 60 là giờ sai; parser nên từ chối thay vì cộng dồn. */
    @Test
    void d175SecondsAboveFiftyNineAreRejected() {
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\n00:00:75.000 --> 00:00:80.000\nx\n"));
    }
}
