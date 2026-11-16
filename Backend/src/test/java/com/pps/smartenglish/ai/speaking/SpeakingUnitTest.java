package com.pps.smartenglish.ai.speaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.speaking.EngineModels.Audio;
import com.pps.smartenglish.ai.speaking.EngineModels.Word;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SpeakingUnitTest {

    private static SpeakingProperties props(String url) {
        return new SpeakingProperties("http", url, "secret-token", 2, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofMinutes(10),
                Duration.ofHours(1), 30, false, new SpeakingProperties.Unclear(3, 5, 0.5, 0.5),
                new SpeakingProperties.Bands(1.0, 0.6, 1.0, "0.95=9,0.9=8,0.85=7,0.78=6,0.7=5,0.6=4,0.5=3,0.4=2", "140=8,120=7,100=6,80=5,60=4,40=3,20=2",
                        "1=8,2=7,4=6,6=5,8=4,12=3,16=2", "0.85=8,0.75=7,0.65=6,0.55=5,0.45=4,0.35=3,0.25=2"));
    }

    @Test
    void overallRoundsLikeIelts() {
        assertEquals(7.5, SpeakingScorer.overall(8, 7.5, 7, 6.5)); // 7.25 -> 7.5
        assertEquals(7.0, SpeakingScorer.overall(7, 7, 7, 7.5)); // 7.125 -> 7.0
        assertEquals(7.0, SpeakingScorer.overall(7, 7, 7, 6.5)); // 6.875 -> 7.0
        assertEquals(6.5, SpeakingScorer.overall(6, 6.5, 6.5, 7)); // 6.5 giữ nguyên
        assertEquals(1.0, SpeakingScorer.roundHalf(0.75));
        assertEquals(0.5, SpeakingScorer.roundHalf(0.25));
        assertEquals(9.0, SpeakingScorer.clampBand(11));
        assertEquals(0.0, SpeakingScorer.clampBand(-2));
    }

    @Test
    void bandTablesPickTheFirstMatchingRow() {
        var up = BandTable.atLeast("0.9=8,0.7=5");
        assertEquals(8, up.band(0.95));
        assertEquals(5, up.band(0.7));
        assertEquals(1, up.band(0.1));
        var down = BandTable.atMost("2=7,6=5");
        assertEquals(7, down.band(0));
        assertEquals(5, down.band(6));
        assertEquals(1, down.band(7));
    }

    @Test
    void fluencyCountsPausesOverOneSecond() {
        var scorer = new SpeakingScorer(props(""));
        // 10 từ nói liền 0.3 s rồi một khoảng lặng 2 s ở giữa
        List<Word> words = new java.util.ArrayList<>(FakeSpeakingEngineClient.spoken(5, 0.1, 0.9).words());
        double start = words.get(4).end() + 2.0;
        for (int i = 0; i < 5; i++) {
            words.add(new Word("w", start + i * 0.4, start + i * 0.4 + 0.3, 0.9));
        }
        var f = scorer.fluency(words);
        assertEquals(1, f.longPauses());
        assertTrue(f.speechRatio() > 0 && f.speechRatio() <= 1);
        assertTrue(scorer.unclearReason(new EngineModels.Transcript("", words), 2, List.of("a")).isPresent());
    }

    @Test
    void wavHeaderGivesRealDurationAndRejectsOtherFormats() {
        byte[] ok = wavHeader(1, 16, 32000, 64000);
        assertEquals(2.0, WavHeader.durationSeconds(ok, 64044));
        assertEquals(2.0, WavHeader.durationSeconds(wavHeader(1, 16, 32000, 0), 64044), "độ dài 0 lấy theo dung lượng thật");
        assertThrows(IllegalArgumentException.class, () -> WavHeader.durationSeconds(wavHeader(3, 16, 32000, 64000), 64044));
        assertThrows(IllegalArgumentException.class, () -> WavHeader.durationSeconds(wavHeader(1, 8, 32000, 64000), 64044));
        assertThrows(IllegalArgumentException.class, () -> WavHeader.durationSeconds(new byte[100], 100));
    }

    private static byte[] wavHeader(int format, int bits, int byteRate, int data) {
        var b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) format)
                .putShort((short) 1).putInt(16000).putInt(byteRate).putShort((short) 2).putShort((short) bits).put("data".getBytes()).putInt(data);
        return b.array();
    }

    @Test
    void httpClientSendsBearerTokenAndClassifiesErrors() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var auth = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        server.createContext("/v1/transcribe", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            ex.getRequestBody().readAllBytes();
            reply(ex, 200, "{\"text\":\"hello there\",\"words\":[{\"word\":\"hello\",\"start\":0.1,\"end\":0.5,\"probability\":0.9}]}");
        });
        server.createContext("/v1/analyze", ex -> {
            ex.getRequestBody().readAllBytes();
            reply(ex, 200, "{\"ipa\":{\"words\":[{\"expected_ipa\":\"/a/\",\"score\":0.5,\"issues\":[\"x\"],\"heard_ipa\":\"/b/\"}]},\"gopt\":{\"utterance\":1.5}}");
        });
        server.createContext("/bad", ex -> reply(ex, 400, "{}"));
        server.createContext("/busy", ex -> reply(ex, 503, "{}"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var audio = new Audio(() -> new ByteArrayInputStream(new byte[100]), 100);
            var client = new HttpSpeakingEngineClient(props(base), new ObjectMapper());
            var t = client.transcribe(audio);
            assertEquals("hello there", t.text());
            assertEquals(0.9, t.words().get(0).probability());
            assertEquals("Bearer secret-token", auth.get());
            assertTrue(contentType.get().startsWith("multipart/form-data"));
            var a = client.analyze(audio, List.of("hello"));
            assertEquals("hello", a.words().get(0).word());
            assertEquals(0.5, a.words().get(0).score());
            assertEquals("/b/", a.words().get(0).heardIpa());
            assertEquals(1.5, a.gopt());

            // 400 không retry, 503 retry; engine không phản hồi hoặc chưa cấu hình URL
            var bad = assertThrows(SpeakingEngineException.class, () -> new HttpSpeakingEngineClient(props(base + "/bad"), new ObjectMapper()).transcribe(audio));
            assertFalse(bad.isRetryable());
            var busy = assertThrows(SpeakingEngineException.class, () -> new HttpSpeakingEngineClient(props(base + "/busy"), new ObjectMapper()).transcribe(audio));
            assertTrue(busy.isRetryable());
            assertFalse(assertThrows(SpeakingEngineException.class, () -> new HttpSpeakingEngineClient(props(""), new ObjectMapper()).transcribe(audio)).isRetryable());
        } finally {
            server.stop(0);
        }
        var down = assertThrows(SpeakingEngineException.class, () -> new HttpSpeakingEngineClient(props("http://127.0.0.1:1"), new ObjectMapper())
                .transcribe(new Audio(() -> new ByteArrayInputStream(new byte[1]), 1)));
        assertTrue(down.isRetryable());
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
