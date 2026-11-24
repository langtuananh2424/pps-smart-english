package com.pps.smartenglish.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.ai.dto.SpeakingGrade;
import com.pps.smartenglish.ai.speaking.EngineModels.WordScore;
import com.pps.smartenglish.ai.speaking.FakeSpeakingEngineClient;
import com.pps.smartenglish.ai.speaking.SpeakingCleanupJob;
import com.pps.smartenglish.ai.speaking.SpeakingEngineException;
import com.pps.smartenglish.support.FakeStorageConfig.FakeStorage;
import com.pps.smartenglish.support.MutableClockConfig;
import com.pps.smartenglish.support.MutableClockConfig.MutableClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** FR-AI-03, D-27, D-43, D-47, D-48: xin quyền tải lên, xác nhận, chấm, hoàn lượt khi không rõ, dọn dẹp. */
@Import(MutableClockConfig.class)
class SpeakingFlowTest extends AiTestSupport {

    @Autowired FakeStorage storage;
    @Autowired FakeSpeakingEngineClient engine;
    @Autowired SpeakingCleanupJob cleanup;
    @Autowired MutableClock clock;

    @BeforeEach
    void setUp() {
        defaultWeights(); // chạy sau unitWeights() của lớp cha: SPEAKING_PART = 2
        engine.reset();
        storage.objects.clear();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** WAV PCM 16-bit: seconds * byteRate byte dữ liệu (0), byteRate 32000 = mono 16 kHz. */
    private static byte[] wav(double seconds, int byteRate) {
        int data = (int) (seconds * byteRate);
        var b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(byteRate / 2).putInt(byteRate).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        return b.array();
    }

    private static byte[] wav(double seconds) {
        return wav(seconds, 32000);
    }

    private long speakingQuestion(long bank, int part, Integer maxSeconds) {
        String payload = "{\"part\":" + part + ",\"prompt\":\"Describe a trip.\"" + (part == 2 ? ",\"cueCard\":[\"where\",\"when\"]" : "")
                + (maxSeconds == null ? "" : ",\"maxSeconds\":" + maxSeconds) + "}";
        return question(bank, "SPEAKING", 1, "Describe a trip.", payload, "{}");
    }

    private long courseOf(long bankId) {
        return jdbc.queryForObject("select c.id from question_banks b join topics t on t.id = b.topic_id "
                + "join course_levels l on l.id = t.level_id join courses c on c.id = l.course_id where b.id = ?", Long.class, bankId);
    }

    private record Permit(long id, String key) {}

    private Permit permit(Account u, long questionId) throws Exception {
        guard.resetRateLimit(u.id());
        var r = postJson("/api/speaking/submissions", u, Map.of("questionId", questionId)).andExpect(status().isCreated());
        long id = idOf(r, "submissionId");
        return new Permit(id, jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, id));
    }

    private org.springframework.test.web.servlet.ResultActions complete(Account u, long id) throws Exception {
        guard.resetRateLimit(u.id());
        return postJson("/api/speaking/submissions/" + id + "/complete", u, Map.of());
    }

    private String subStatus(long id) {
        return jdbc.queryForObject("select status from speaking_submissions where id = ?", String.class, id);
    }

    private long uploaded(Account u, long q, double seconds) throws Exception {
        var p = permit(u, q);
        storage.put(p.key, wav(seconds));
        complete(u, p.id).andExpect(status().isAccepted());
        return p.id;
    }

    @Test
    void questionEndpointReturnsPromptAndUsesSameGate() throws Exception {
        var u = student(2);
        long q = speakingQuestion(bank(true), 2, null);
        getJson("/api/speaking/questions/" + q, u).andExpect(status().isOk()).andExpect(jsonPath("$.part").value(2))
                .andExpect(jsonPath("$.prompt").value("Describe a trip.")).andExpect(jsonPath("$.cueCard[0]").value("where"))
                .andExpect(jsonPath("$.maxSeconds").value(180));
        getJson("/api/speaking/questions/" + speakingQuestion(bank(false), 1, null), u).andExpect(status().isForbidden());
        getJson("/api/speaking/questions/999999", u).andExpect(status().isNotFound());
    }

    @Test
    void archivedQuestionIsNotOpenedForNewSubmissions() throws Exception {
        var u = student(2);
        long q = speakingQuestion(bank(true), 1, null);
        jdbc.update("update questions set archived_at = CURRENT_TIMESTAMP where id = ?", q);
        postJson("/api/speaking/submissions", u, Map.of("questionId", q)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("question_archived"));
        getJson("/api/speaking/questions/" + q, u).andExpect(status().isBadRequest());
    }

    @Test
    void permitCarriesPolicyAndRefusesLockedBankNoQuotaAndWrongType() throws Exception {
        var u = student(2);
        long bank = bank(true);
        long q = speakingQuestion(bank, 2, null);
        postJson("/api/speaking/submissions", u, Map.of("questionId", q)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("http://fake-storage/bucket")).andExpect(jsonPath("$.contentType").value("audio/wav"))
                .andExpect(jsonPath("$.maxSeconds").value(180)).andExpect(jsonPath("$.fields.key").isNotEmpty())
                .andExpect(jsonPath("$.fields.policy").isNotEmpty()).andExpect(jsonPath("$.fields['x-amz-signature']").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        var post = storage.lastPost;
        assertEquals("audio/wav", post.contentType());
        assertEquals(Duration.ofMinutes(10), post.ttl());
        assertTrue(post.maxBytes() > 180 * 32000 && post.maxBytes() < 12L * 1024 * 1024, "maxBytes " + post.maxBytes());
        assertTrue(post.key().matches("speaking/" + u.id() + "/[0-9a-f-]{36}\\.wav"), post.key());
        assertEquals(2, balance(u)); // xin quyền chưa trừ lượt

        // part 1 mặc định 60 giây (D-43), maxSeconds ghi rõ thì theo đó
        postJson("/api/speaking/submissions", u, Map.of("questionId", speakingQuestion(bank, 1, null)))
                .andExpect(jsonPath("$.maxSeconds").value(60));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", speakingQuestion(bank, 3, 30)))
                .andExpect(jsonPath("$.maxSeconds").value(30));

        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", speakingQuestion(bank(false), 1, null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("bank_locked"));
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", u, Map.of("questionId", writingQuestion(bank))).andExpect(status().isBadRequest());
        guard.resetRateLimit(u.id());
        postJson("/api/speaking/submissions", null, Map.of("questionId", q)).andExpect(status().isUnauthorized());

        var unverified = register(newEmail(), PASSWORD);
        postJson("/api/speaking/submissions", unverified, Map.of("questionId", q)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("email_not_verified"));

        var poor = student(1); // cần 2 lượt cho một part
        addonProduct();
        postJson("/api/speaking/submissions", poor, Map.of("questionId", q)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("quota_exhausted"));
        assertEquals(0, count("select count(*) from speaking_submissions where user_id = ?", poor.id()));
    }

    @Test
    void invalidUploadsAreRejectedWithoutChargingAndOnlyTheOwnerMayConfirm() throws Exception {
        var u = student(5);
        long q = speakingQuestion(bank(true), 1, 10);
        int jobs = count("select count(*) from ai_jobs where user_id = ?", u.id());

        var missing = permit(u, q);
        complete(u, missing.id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("audio_missing"));

        var notWav = permit(u, q);
        storage.put(notWav.key, new byte[5000]);
        complete(u, notWav.id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("audio_invalid"));
        assertFalse(storage.objects.containsKey(notWav.key));

        var tooLong = permit(u, q); // header khai 16 KB/s nên 21 giây vẫn nằm trong trần dung lượng của 10 giây
        storage.put(tooLong.key, wav(21, 16000));
        complete(u, tooLong.id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("audio_too_long"));
        assertFalse(storage.objects.containsKey(tooLong.key));

        var tooShort = permit(u, q);
        storage.put(tooShort.key, wav(0.5));
        complete(u, tooShort.id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("audio_too_short"));

        var tooBig = permit(u, q);
        storage.put(tooBig.key, wav(30));
        complete(u, tooBig.id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("audio_too_large"));

        var good = permit(u, q);
        storage.put(good.key, wav(5));
        var stranger = student(5);
        complete(stranger, good.id).andExpect(status().isNotFound());
        getJson("/api/speaking/submissions/" + good.id, stranger).andExpect(status().isNotFound());

        clock.set(Instant.now().plus(Duration.ofMinutes(11)));
        complete(u, good.id).andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("upload_expired"));

        assertEquals(5, balance(u));
        assertEquals(jobs, count("select count(*) from ai_jobs where user_id = ?", u.id()));
        assertEquals("AWAITING_UPLOAD", subStatus(missing.id));
    }

    @Test
    void completeChargesTwoPeriodicFirstThenPoolAndIsIdempotent() throws Exception {
        var u = student(5);
        long bank = bank(true);
        long course = courseOf(bank);
        jdbc.update("update courses set ai_period = 'WEEK', ai_period_quota = 1 where id = ?", course);
        jdbc.update("insert into user_course_enrollments (user_id, course_id, status, starts_at, expires_at) values (?, ?, 'ACTIVE', ?, null)",
                u.id(), course, OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        long q = speakingQuestion(bank, 2, null);

        var p = permit(u, q);
        storage.put(p.key, wav(20));
        var r = complete(u, p.id).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("GRADING"));
        long jobId = idOf(r, "jobId");
        assertEquals(4, balance(u)); // 1 định kỳ + 1 pool
        assertEquals(1, count("select count(*) from ai_quota_ledger where ref_id = ? and reason = 'GRADING' and source = 'PERIODIC' and delta = -1", jobId));
        assertEquals(1, count("select count(*) from ai_quota_ledger where ref_id = ? and reason = 'GRADING' and source = 'POOL' and delta = -1", jobId));
        assertEquals(1, count("select count(*) from ai_jobs where id = ? and kind = 'SPEAKING_GRADE' and charged = true", jobId));
        assertEquals("GRADING", subStatus(p.id));

        // gọi lại: cùng job, không trừ lần hai
        complete(u, p.id).andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value(jobId));
        assertEquals(4, balance(u));
        assertEquals(1, count("select count(*) from ai_jobs where user_id = ?", u.id()));
    }

    @Test
    void workerGradesFourBandsRoundsOverallAndExposesResultOnlyToOwner() throws Exception {
        var u = student(5);
        long q = speakingQuestion(bank(true), 2, null);
        fake.override(SpeakingGrade.class, new SpeakingGrade(7, 6.5, "Tốt.", List.of(new SpeakingGrade.Suggestion("a", "b", "c"))));
        engine.setAnalysis(words -> new com.pps.smartenglish.ai.speaking.EngineModels.Analysis(
                words.stream().map(w -> new WordScore(w, FakeSpeakingEngineClient.word(3).equals(w) ? 0.2 : 0.92, List.of("vowel"), "/e/", "/i/")).toList(), 0.9));
        long id = uploaded(u, q, 20);
        long jobId = jdbc.queryForObject("select job_id from speaking_submissions where id = ?", Long.class, id);

        assertEquals(1, runner.runDueNow());
        assertEquals("GRADED", subStatus(id));
        // 40 từ trong 15.9 s: pron 0.9 (một từ 0.2) -> 8; trôi chảy (8 + 8 + 7) / 3 -> 7.5; (8 + 7.5 + 7 + 6.5) / 4 = 7.25 -> 7.5
        getJson("/api/speaking/submissions/" + id, u).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.result.bands.pronunciation").value(8.0)).andExpect(jsonPath("$.result.bands.fluency").value(7.5))
                .andExpect(jsonPath("$.result.bands.lexical").value(7.0)).andExpect(jsonPath("$.result.bands.grammar").value(6.5))
                .andExpect(jsonPath("$.result.overall").value(7.5)).andExpect(jsonPath("$.result.transcript").isNotEmpty())
                .andExpect(jsonPath("$.result.mispronounced[0].word").value(FakeSpeakingEngineClient.word(3)))
                .andExpect(jsonPath("$.result.mispronounced[0].expectedIpa").value("/e/"))
                .andExpect(jsonPath("$.result.fluency.longPauses").value(0)).andExpect(jsonPath("$.result.suggestions.length()").value(1))
                .andExpect(jsonPath("$.audioUrl").value(org.hamcrest.Matchers.startsWith("http://fake-storage/speaking/")));
        assertTrue(fake.lastUserPrompt().contains("Part: 2") && fake.lastUserPrompt().contains(FakeSpeakingEngineClient.word(0)));
        assertEquals(1, count("select count(*) from ai_grading_token_usage where job_id = ? and audio_seconds = 20", jobId));
        getJson("/api/ai/jobs/" + jobId, u).andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.result.overall").value(7.5));
        assertEquals(3, balance(u));

        getJson("/api/speaking/submissions/" + id, student(1)).andExpect(status().isNotFound());
        getJson("/api/me/speaking?questionId=" + q, u).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id)).andExpect(jsonPath("$.nextCursor").doesNotExist());
        getJson("/api/me/speaking?limit=x", u).andExpect(status().isBadRequest());
    }

    @Test
    void unclearRecordingIsNotGradedAndRefunded() throws Exception {
        var u = student(5);
        long q = speakingQuestion(bank(true), 1, null);
        engine.setTranscript(FakeSpeakingEngineClient.spoken(3, 0.1, 0.9)); // dưới 5 từ
        long id = uploaded(u, q, 20);
        assertEquals(3, balance(u));
        assertEquals(1, runner.runDueNow());
        assertEquals("UNCLEAR", subStatus(id));
        assertEquals(5, balance(u));
        assertEquals(0, fake.calls());
        assertEquals(0, engine.analyzeCalls());
        getJson("/api/speaking/submissions/" + id, u).andExpect(jsonPath("$.result.reason").value("too_few_words"))
                .andExpect(jsonPath("$.result.overall").doesNotExist());
        assertEquals(1, count("select count(*) from ai_quota_ledger where user_id = ? and reason = 'REFUND' and delta = 2", u.id()));

        engine.setTranscript(FakeSpeakingEngineClient.spoken(10, 0.1, 0.3)); // độ tin cậy thấp
        long id2 = uploaded(u, q, 20);
        runner.runDueNow();
        assertEquals("UNCLEAR", subStatus(id2));
        getJson("/api/speaking/submissions/" + id2, u).andExpect(jsonPath("$.result.reason").value("low_confidence"));
        assertEquals(5, balance(u));

        engine.setTranscript(FakeSpeakingEngineClient.spoken(8, 0.1, 0.9)); // 8 từ / 20 s = 0.4 từ/giây
        long id3 = uploaded(u, q, 20);
        runner.runDueNow();
        getJson("/api/speaking/submissions/" + id3, u).andExpect(jsonPath("$.result.reason").value("too_slow"));
        assertEquals(5, balance(u));
    }

    @Test
    void engineServerErrorRetriesThenFailsAndRefunds() throws Exception {
        var u = student(5);
        long id = uploaded(u, speakingQuestion(bank(true), 1, null), 20);
        long jobId = jdbc.queryForObject("select job_id from speaking_submissions where id = ?", Long.class, id);
        engine.failNext(10, new SpeakingEngineException(true, "Engine trả lỗi 503", null));
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(1, runner.runDueNow(), "lượt thử " + attempt);
            jdbc.update("update ai_jobs set run_after = ? where id = ?", OffsetDateTime.parse("2000-01-01T00:00:00Z"), jobId);
        }
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(3, count("select attempts from ai_jobs where id = ?", jobId));
        assertEquals("FAILED", subStatus(id));
        assertEquals(5, balance(u));
    }

    @Test
    void engineClientErrorIsNotRetried() throws Exception {
        var u = student(5);
        long id = uploaded(u, speakingQuestion(bank(true), 1, null), 20);
        long jobId = jdbc.queryForObject("select job_id from speaking_submissions where id = ?", Long.class, id);
        engine.failNext(1, new SpeakingEngineException(false, "Engine trả lỗi 400", null));
        assertEquals(1, runner.runDueNow());
        assertEquals("FAILED", jdbc.queryForObject("select status from ai_jobs where id = ?", String.class, jobId));
        assertEquals(1, count("select attempts from ai_jobs where id = ?", jobId));
        assertEquals("FAILED", subStatus(id));
        assertEquals(5, balance(u));
        assertEquals(1, engine.transcribeCalls());
    }

    @Test
    void cleanupExpiresStaleUploadsAndDeletesOldAudioButKeepsResult() throws Exception {
        var u = student(5);
        long q = speakingQuestion(bank(true), 1, null);

        var stale = permit(u, q);
        storage.put(stale.key, wav(5));
        var fresh = permit(u, q);
        storage.put(fresh.key, wav(5));
        jdbc.update("update speaking_submissions set upload_expires_at = ? where id = ?", OffsetDateTime.now().minusHours(2), stale.id);
        assertEquals(1, cleanup.tick());
        assertEquals("EXPIRED", subStatus(stale.id));
        assertFalse(storage.objects.containsKey(stale.key));
        assertEquals("AWAITING_UPLOAD", subStatus(fresh.id));
        assertTrue(storage.objects.containsKey(fresh.key));
        complete(u, stale.id).andExpect(status().isGone());

        long graded = uploaded(u, q, 20);
        runner.runDueNow();
        String key = jdbc.queryForObject("select object_key from speaking_submissions where id = ?", String.class, graded);
        assertEquals(0, cleanup.tick()); // chưa quá 30 ngày
        assertTrue(storage.objects.containsKey(key));
        jdbc.update("update speaking_submissions set created_at = ? where id = ?", OffsetDateTime.now().minusDays(31), graded);
        assertEquals(1, cleanup.tick());
        assertFalse(storage.objects.containsKey(key));
        assertNotNull(jdbc.queryForObject("select audio_deleted_at from speaking_submissions where id = ?", Object.class, graded));
        getJson("/api/speaking/submissions/" + graded, u).andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.result.overall").exists()).andExpect(jsonPath("$.audioUrl").doesNotExist());
        assertEquals(0, cleanup.tick());
    }
}
