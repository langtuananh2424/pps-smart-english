package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.SpeakingGrade;
import com.pps.smartenglish.ai.provider.AiCall;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiGradingProvider;
import com.pps.smartenglish.ai.provider.AiUsage;
import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.ai.service.Prompts;
import com.pps.smartenglish.ai.speaking.EngineModels.Audio;
import com.pps.smartenglish.ai.speaking.EngineModels.Transcript;
import com.pps.smartenglish.ai.speaking.SpeakingScorer.Fluency;
import com.pps.smartenglish.ai.worker.AiJobHandler;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * FR-AI-03 / D-48: engine chép lời → chặn bản ghi không rõ (hoàn lượt) → engine chấm phát âm → code tính trôi chảy →
 * Haiku chấm từ vựng và ngữ pháp → band tổng. Chạy NGOÀI transaction; chỉ effects chạy trong transaction hoàn tất job.
 */
@Component
class SpeakingGradeHandler implements AiJobHandler {

    private static final int LLM_MAX_TOKENS = 1500;
    private static final int MAX_SUGGESTIONS = 5;

    private final SpeakingEngineClient engine;
    private final AiGradingProvider provider;
    private final Prompts prompts;
    private final SpeakingScorer scorer;
    private final ObjectStorage storage;
    private final SpeakingSubmissionRepository submissions;
    private final AiQuotaService quota;
    private final ObjectMapper mapper;
    private final Clock clock;

    SpeakingGradeHandler(SpeakingEngineClient engine, AiGradingProvider provider, Prompts prompts, SpeakingScorer scorer,
                         ObjectStorage storage, SpeakingSubmissionRepository submissions, AiQuotaService quota,
                         ObjectMapper mapper, Clock clock) {
        this.engine = engine;
        this.provider = provider;
        this.prompts = prompts;
        this.scorer = scorer;
        this.storage = storage;
        this.submissions = submissions;
        this.quota = quota;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public AiJobKind kind() {
        return AiJobKind.SPEAKING_GRADE;
    }

    @Override
    public Completion execute(AiJob job) throws AiCallException {
        JsonNode in = job.getInput();
        long submissionId = in.path("submissionId").asLong();
        double duration = in.path("durationSec").asDouble();
        Audio audio = new Audio(() -> storage.open(in.path("objectKey").asText()), in.path("sizeBytes").asLong());

        Transcript transcript;
        try {
            transcript = engine.transcribe(audio);
        } catch (SpeakingEngineException e) {
            throw engineFailure(e);
        }
        List<String> spoken = SpeakingScorer.normalize(transcript.words());
        var unclear = transcript.words().isEmpty() ? java.util.Optional.of("too_few_words") : scorer.unclearReason(transcript, duration, spoken);
        AiUsage engineUsage = new AiUsage("speaking-engine", "whisper", 0, 0, 0, 0, duration);
        if (unclear.isPresent()) {
            var result = SpeakingResult.unclear(unclear.get());
            // D-48: không chấm thì không trừ lượt, hoàn trong cùng transaction hoàn tất job
            return new Completion(result, engineUsage, j -> {
                finish(submissionId, result);
                quota.refund(j);
            });
        }

        var pron = scorer.pronunciation(analyze(audio, spoken));
        Fluency fluency = scorer.fluency(transcript.words());
        var llm = grade(in, transcript, fluency);
        var grade = llm.data();
        double lexical = SpeakingScorer.clampBand(grade.lexicalBand());
        double grammar = SpeakingScorer.clampBand(grade.grammarBand());
        double overall = SpeakingScorer.overall(pron.band(), fluency.band(), lexical, grammar);
        var suggestions = grade.suggestions() == null ? List.<SpeakingGrade.Suggestion>of()
                : grade.suggestions().stream().limit(MAX_SUGGESTIONS).toList();
        var result = new SpeakingResult(SpeakingSubmission.GRADED, null, transcript.text(),
                new SpeakingResult.Bands(pron.band(), fluency.band(), lexical, grammar), overall, fluency, pron.meanScore(),
                pron.wrongWords(), grade.feedback(), suggestions);
        return new Completion(result, llm.usage().withAudio(duration), j -> finish(submissionId, result));
    }

    private EngineModels.Analysis analyze(Audio audio, List<String> spoken) throws AiCallException {
        try {
            return engine.analyze(audio, spoken);
        } catch (SpeakingEngineException e) {
            throw engineFailure(e);
        }
    }

    private com.pps.smartenglish.ai.provider.AiResult<SpeakingGrade> grade(JsonNode in, Transcript t, Fluency f) throws AiCallException {
        StringBuilder cue = new StringBuilder();
        in.path("cueCard").forEach(c -> cue.append("- ").append(c.asText()).append('\n'));
        String user = "Part: " + in.path("part").asInt() + "\n"
                + "Đề: " + in.path("prompt").asText() + "\n"
                + (cue.length() > 0 ? "Gợi ý của đề (cue card):\n" + cue : "")
                + String.format(Locale.ROOT, "Chỉ số trôi chảy (tham khảo): %.0f từ/phút, %d khoảng lặng trên 1 giây, nói %.0f%% thời gian\n",
                        f.wordsPerMinute(), f.longPauses(), f.speechRatio() * 100)
                + "Bản chép lời:\n===\n" + t.text() + "\n===\n";
        return provider.complete(new AiCall<>(prompts.speakingRubric(), user, SpeakingGrade.class, LLM_MAX_TOKENS));
    }

    private void finish(long submissionId, SpeakingResult result) {
        submissions.finish(submissionId, result.status(), mapper.valueToTree(result), Instant.now(clock));
    }

    private static AiCallException engineFailure(SpeakingEngineException e) {
        return new AiCallException(e.isRetryable(), e.getMessage(), null, e);
    }
}
