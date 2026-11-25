package com.pps.smartenglish.ai.speaking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình app.speaking.* (FR-AI-03). engine: http (engine thật) hoặc fake (test/local).
 * Mọi ngưỡng band là GIÁ TRỊ KHỞI ĐIỂM, chưa hiệu chỉnh với giáo viên; chỉnh bằng cấu hình, không sửa code.
 */
@ConfigurationProperties(prefix = "app.speaking")
public record SpeakingProperties(
        @DefaultValue("http") String engine,
        @DefaultValue("") String engineUrl,
        @DefaultValue("") String engineToken,
        @DefaultValue("2") int maxConcurrent,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("180s") Duration readTimeout,
        @DefaultValue("10m") Duration uploadTtl,
        @DefaultValue("1h") Duration expireAfter,
        @DefaultValue("30") int audioRetentionDays,
        @DefaultValue("true") boolean cleanupEnabled,
        @DefaultValue Unclear unclear,
        @DefaultValue Bands bands,
        @DefaultValue("9.0") double bandMax) {

    /** bandMax là mẫu số quy band thành credit (D-145); sai thì app không khởi động. */
    public SpeakingProperties {
        if (!(bandMax > 0 && bandMax <= 10)) {
            throw new IllegalArgumentException("app.speaking.band-max phải trong (0, 10], hiện là " + bandMax);
        }
    }

    /** Bản ghi không rõ (D-48): chạm bất kỳ ngưỡng nào thì UNCLEAR. */
    public record Unclear(
            @DefaultValue("3") double minSeconds,
            @DefaultValue("5") int minWords,
            @DefaultValue("0.5") double minAvgProbability,
            @DefaultValue("0.5") double minWordsPerSecond) {}

    /**
     * Bảng "ngưỡng=band" cách nhau bởi dấu phẩy. *Min: số đo càng cao càng tốt, lấy dòng đầu có giá trị >= ngưỡng.
     * pausesPerMinMax: càng ít càng tốt, lấy dòng đầu có giá trị <= ngưỡng. Không dòng nào khớp thì band 1.
     * pronScoreMax: thang điểm từ của engine (giả định 0..1; nếu engine trả 0..100 thì đặt 100).
     */
    public record Bands(
            @DefaultValue("1.0") double pronScoreMax,
            @DefaultValue("0.6") double pronWrongBelow,
            @DefaultValue("1.0") double pauseSeconds,
            @DefaultValue("0.95=9,0.9=8,0.85=7,0.78=6,0.7=5,0.6=4,0.5=3,0.4=2") String pronMin,
            @DefaultValue("140=8,120=7,100=6,80=5,60=4,40=3,20=2") String wpmMin,
            @DefaultValue("1=8,2=7,4=6,6=5,8=4,12=3,16=2") String pausesPerMinMax,
            @DefaultValue("0.85=8,0.75=7,0.65=6,0.55=5,0.45=4,0.35=3,0.25=2") String speechRatioMin) {}
}
