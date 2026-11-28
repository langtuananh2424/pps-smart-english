package com.pps.smartenglish.lessoncontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pps.smartenglish.lessoncontent.SubtitleParser.Cue;
import com.pps.smartenglish.lessoncontent.SubtitleParser.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubtitleParserTest {

    private static List<Cue> parse(String s) {
        return SubtitleParser.parse(s, 5000);
    }

    @Test
    void vttWithHeaderNoteCueIdSettingsTagsAndHours() {
        var cues = parse("WEBVTT - title\n\nNOTE a comment\nsecond line\n\nintro\n01:02:03.500 --> 01:02:05.000 align:start\n"
                + "<v Bob>Hello <b>world</b>\nsecond &amp; line\n\n00:05.000 --> 00:06.25\nShort\n");
        assertEquals(List.of(new Cue(5_000, 6_250, "Short"), new Cue(3_723_500, 3_725_000, "Hello world second & line")), cues); // đã sắp theo giờ
    }

    @Test
    void srtWithCrlfAndCommaMillis() {
        var cues = parse("1\r\n00:00:01,000 --> 00:00:02,500\r\nHi\r\nthere\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nYo\r\n");
        assertEquals(List.of(new Cue(1_000, 2_500, "Hi there"), new Cue(3_000, 4_000, "Yo")), cues);
    }

    @Test
    void bomIsIgnored() {
        assertEquals(1, parse((char) 0xFEFF + "WEBVTT\n\n00:00.000 --> 00:01.000\nHi\n").size());
    }

    @Test
    void brokenFilesAreRejected() {
        assertThrows(ParseException.class, () -> parse("just some text"));
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\n"));
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\n00:05.000 --> 00:05.000\nZero\n"));
        assertThrows(ParseException.class, () -> parse("WEBVTT\n\n00:05.000 --> 00:01.000\nBackwards\n"));
        assertThrows(ParseException.class, () -> SubtitleParser.parse("WEBVTT\n\n00:00.000 --> 00:01.000\na\n\n00:01.000 --> 00:02.000\nb\n", 1));
    }

    @Test
    void toVttRoundTripsEscapedText() {
        var cues = List.of(new Cue(1_000, 2_000, "a < b & c > d &amp; e"), new Cue(3_661_001, 3_662_000, "x"));
        assertEquals(cues, parse(SubtitleParser.toVtt(cues)));
    }
}
