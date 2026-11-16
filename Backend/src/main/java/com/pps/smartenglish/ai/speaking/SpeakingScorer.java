package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.speaking.EngineModels.Analysis;
import com.pps.smartenglish.ai.speaking.EngineModels.Transcript;
import com.pps.smartenglish.ai.speaking.EngineModels.Word;
import com.pps.smartenglish.ai.speaking.EngineModels.WordScore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Phần chấm do code tính (D-48): chặn bản ghi không rõ, độ trôi chảy, phát âm và band tổng. Thuần, không I/O. */
@Component
public class SpeakingScorer {

    private static final int MAX_WORDS = 500;

    public record Fluency(double wordsPerMinute, int longPauses, double pausesPerMinute, double speechRatio, double band) {}

    public record WrongWord(String word, double score, List<String> issues, String expectedIpa, String heardIpa) {}

    public record Pronunciation(double band, double meanScore, List<WrongWord> wrongWords) {}

    private final SpeakingProperties.Unclear unclear;
    private final SpeakingProperties.Bands bands;
    private final BandTable pron;
    private final BandTable wpm;
    private final BandTable pauses;
    private final BandTable ratio;

    SpeakingScorer(SpeakingProperties props) {
        this.unclear = props.unclear();
        this.bands = props.bands();
        this.pron = BandTable.atLeast(bands.pronMin());
        this.wpm = BandTable.atLeast(bands.wpmMin());
        this.pauses = BandTable.atMost(bands.pausesPerMinMax());
        this.ratio = BandTable.atLeast(bands.speechRatioMin());
    }

    /** Từ đã chuẩn hóa để gửi /v1/analyze: thường, bỏ dấu câu (giữ dấu nháy), bỏ từ rỗng, tối đa MAX_WORDS. */
    public static List<String> normalize(List<Word> words) {
        return words.stream().map(w -> w.word().toLowerCase().replaceAll("[^a-z']", "")).filter(w -> !w.isEmpty()).limit(MAX_WORDS).toList();
    }

    /** Lý do không chấm (D-48), empty nếu đủ rõ. */
    public Optional<String> unclearReason(Transcript t, double durationSec, List<String> spoken) {
        if (durationSec < unclear.minSeconds()) {
            return Optional.of("too_short");
        }
        if (spoken.size() < unclear.minWords()) {
            return Optional.of("too_few_words");
        }
        double avg = t.words().stream().mapToDouble(Word::probability).average().orElse(0);
        if (avg < unclear.minAvgProbability()) {
            return Optional.of("low_confidence");
        }
        if (spoken.size() / durationSec < unclear.minWordsPerSecond()) {
            return Optional.of("too_slow");
        }
        return Optional.empty();
    }

    /** Số từ/phút, số khoảng lặng dài, tỷ lệ thời gian nói; band = trung bình ba band thành phần, làm tròn 0.5. words không rỗng. */
    public Fluency fluency(List<Word> words) {
        double span = Math.max(1.0, words.get(words.size() - 1).end() - words.get(0).start());
        double minutes = span / 60;
        double spoken = 0;
        int longPauses = 0;
        for (int i = 0; i < words.size(); i++) {
            spoken += Math.max(0, words.get(i).end() - words.get(i).start());
            if (i > 0 && words.get(i).start() - words.get(i - 1).end() > bands.pauseSeconds()) {
                longPauses++;
            }
        }
        double perMinute = words.size() / minutes;
        double pausesPerMin = longPauses / minutes;
        double speech = Math.min(1.0, spoken / span);
        double band = roundHalf((wpm.band(perMinute) + pauses.band(pausesPerMin) + ratio.band(speech)) / 3);
        return new Fluency(perMinute, longPauses, pausesPerMin, speech, band);
    }

    /** Điểm từ của engine chuẩn hóa về 0..1 theo pronScoreMax; từ dưới pronWrongBelow là từ phát âm sai. */
    public Pronunciation pronunciation(Analysis a) {
        double max = bands.pronScoreMax();
        double sum = 0;
        List<WrongWord> wrong = new ArrayList<>();
        for (WordScore w : a.words()) {
            double s = Math.max(0, Math.min(1, w.score() / max));
            sum += s;
            if (s < bands.pronWrongBelow()) {
                wrong.add(new WrongWord(w.word(), s, w.issues(), w.expectedIpa(), w.heardIpa()));
            }
        }
        double mean = a.words().isEmpty() ? 0 : sum / a.words().size();
        wrong.sort(Comparator.comparingDouble(WrongWord::score));
        return new Pronunciation(pron.band(mean), mean, wrong);
    }

    /** Kẹp 0..9 rồi làm tròn 0.5, dùng cho band do LLM trả. */
    public static double clampBand(double b) {
        return roundHalf(Math.max(0, Math.min(9, b)));
    }

    /** Kiểu IELTS: .25 lên .5, .75 lên 1.0. */
    public static double roundHalf(double v) {
        return Math.floor(v * 2 + 0.5) / 2;
    }

    public static double overall(double... bandValues) {
        double sum = 0;
        for (double b : bandValues) {
            sum += b;
        }
        return roundHalf(sum / bandValues.length);
    }
}
