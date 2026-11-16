package com.pps.smartenglish.ai.speaking;

import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

/** Kiểu dữ liệu qua lại với pps-speaking-engine (docs/speaking-engine-local.md). */
public final class EngineModels {

    private EngineModels() {}

    /** Nguồn audio mở lại được cho mỗi lần gọi (mỗi request đọc một luồng mới); size để đặt Content-Length. */
    public record Audio(Supplier<InputStream> open, long size) {}

    public record Word(String word, double start, double end, double probability) {}

    public record Transcript(String text, List<Word> words) {}

    /** Điểm một từ theo thứ tự từ đã gửi; score theo thang của engine. */
    public record WordScore(String word, double score, List<String> issues, String expectedIpa, String heardIpa) {}

    public record Analysis(List<WordScore> words, Double gopt) {}
}
