package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.speaking.EngineModels.Analysis;
import com.pps.smartenglish.ai.speaking.EngineModels.Audio;
import com.pps.smartenglish.ai.speaking.EngineModels.Transcript;
import com.pps.smartenglish.ai.speaking.EngineModels.Word;
import com.pps.smartenglish.ai.speaking.EngineModels.WordScore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Engine giả cho test và chạy local: kết quả tất định, có móc ép output và giả lỗi. */
@Component
@ConditionalOnProperty(prefix = "app.speaking", name = "engine", havingValue = "fake")
public class FakeSpeakingEngineClient implements SpeakingEngineClient {

    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger transcribeCalls = new AtomicInteger();
    private final AtomicInteger analyzeCalls = new AtomicInteger();
    private volatile SpeakingEngineException failure;
    private volatile Transcript transcript;
    private volatile Function<List<String>, Analysis> analysis;

    /** Từ giả toàn chữ cái (bước chuẩn hóa bỏ chữ số): wa, wb, ... */
    public static String word(int i) {
        return "w" + (char) ('a' + i % 26) + (char) ('a' + i / 26 % 26);
    }

    /** n từ, mỗi từ nói 0.3 s rồi nghỉ gap giây, cùng độ tin cậy. */
    public static Transcript spoken(int n, double gap, double probability) {
        List<Word> words = new ArrayList<>();
        double t = 0.2;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < n; i++) {
            words.add(new Word(word(i), t, t + 0.3, probability));
            text.append(i == 0 ? "" : " ").append(word(i));
            t += 0.3 + gap;
        }
        return new Transcript(text.toString(), words);
    }

    /** Các lời gọi kế tiếp (times) ném lỗi này, tính chung cho transcribe và analyze. */
    public void failNext(int times, SpeakingEngineException e) {
        failure = e;
        failures.set(times);
    }

    public void setTranscript(Transcript t) { transcript = t; }

    public void setAnalysis(Function<List<String>, Analysis> f) { analysis = f; }

    public void reset() {
        failures.set(0);
        failure = null;
        transcript = null;
        analysis = null;
        transcribeCalls.set(0);
        analyzeCalls.set(0);
    }

    public int transcribeCalls() { return transcribeCalls.get(); }

    public int analyzeCalls() { return analyzeCalls.get(); }

    @Override
    public Transcript transcribe(Audio audio) throws SpeakingEngineException {
        transcribeCalls.incrementAndGet();
        maybeFail();
        return transcript != null ? transcript : spoken(40, 0.1, 0.9);
    }

    @Override
    public Analysis analyze(Audio audio, List<String> words) throws SpeakingEngineException {
        analyzeCalls.incrementAndGet();
        maybeFail();
        if (analysis != null) {
            return analysis.apply(words);
        }
        return new Analysis(words.stream().map(w -> new WordScore(w, 0.92, List.of(), "/" + w + "/", "/" + w + "/")).toList(), 0.92);
    }

    private void maybeFail() throws SpeakingEngineException {
        if (failures.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw failure;
        }
    }
}
