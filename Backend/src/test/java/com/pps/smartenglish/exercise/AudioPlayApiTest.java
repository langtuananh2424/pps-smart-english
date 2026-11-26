package com.pps.smartenglish.exercise;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.MutableClockConfig;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** #69, D-135: đếm lượt nghe phía máy chủ, ba chế độ phát, phân quyền theo lượt và theo bộ. */
@Import(MutableClockConfig.class)
class AudioPlayApiTest extends ListeningTestSupport {

    @Autowired MutableClockConfig.MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void examModeCountsPerPartAndBlocksWhenExceeded() throws Exception {
        var w = world("EXAM", 2);
        var u = student(0);
        long a = startAttempt(u, w);

        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(1)).andExpect(jsonPath("$.playLimit").value(2))
                .andExpect(jsonPath("$.remaining").value(1)).andExpect(jsonPath("$.playbackMode").value("EXAM"))
                .andExpect(jsonPath("$.url").value(containsString("http://fake-storage/media/audio/")));
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(0));
        play(u, a, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
        assertEquals(2, count("select count(*) from audio_plays where attempt_id = ? and group_id = ?", a, w.g1()));

        play(u, a, w.g2()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(1)); // mỗi part đếm riêng
        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", java.util.List.of())).andExpect(status().isOk()); // D-147: mỗi bộ một lượt mở
        long other = startAttempt(u, w); // lượt làm mới có lượt nghe mới
        play(u, other, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(1));
    }

    @Test
    void examModeUsesConfiguredDefaultWhenBankHasNoLimit() throws Exception {
        var w = world("EXAM", null); // mặc định cấu hình: 1 lần
        var u = student(0);
        long a = startAttempt(u, w);
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playLimit").value(1));
        play(u, a, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
    }

    @Test
    void examAttemptOnFreeBankFollowsExamRules() throws Exception { // D-151: Mô phỏng thi nghe theo luật thi dù bộ để FREE
        var w = world("FREE", null);
        var u = student(0);
        long a = startAttempt(u, w);
        jdbc.update("update student_exercise_attempts set mode = 'EXAM' where id = ?", a);
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("EXAM")).andExpect(jsonPath("$.playLimit").value(1));
        play(u, a, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
        jdbc.update("update student_exercise_attempts set mode = 'PRACTICE' where id = ?", a); // luyện tập: nghe tự do
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playbackMode").value("FREE"));
    }

    @Test
    void freeModeNeverCountsOrLimits() throws Exception {
        var w = world("FREE", null);
        var u = student(0);
        long a = startAttempt(u, w);
        for (int i = 0; i < 15; i++) { // vượt cả maxPlayLimit: chế độ luyện nghe tự do
            play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").doesNotExist())
                    .andExpect(jsonPath("$.playbackMode").value("FREE"));
        }
        assertEquals(0, count("select count(*) from audio_plays where attempt_id = ?", a));
    }

    @Test
    void lockedModeAllowsReplayOnlyAfterAudioFinishedAndWithinRuleLimit() throws Exception {
        var w = world("LOCKED", null); // không có dòng luật: dùng mặc định cấu hình (3 lần nghe)
        var u = student(0);
        long a = startAttempt(u, w);
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playLimit").value(3)).andExpect(jsonPath("$.remaining").value(2));
        play(u, a, w.g1()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("play_in_progress"));
        assertEquals(1, count("select count(*) from audio_plays where attempt_id = ?", a), "lượt bị từ chối không bị đếm");

        playDone(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true)).andExpect(jsonPath("$.remaining").value(2));
        playDone(u, a, w.g1()).andExpect(status().isOk()); // idempotent
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(2));
        playDone(u, a, w.g1());
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(3)).andExpect(jsonPath("$.remaining").value(0));
        playDone(u, a, w.g1());
        play(u, a, w.g1()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("play_limit_reached"));
    }

    @Test
    void lockedLinkExpiryFreesReplayWhenClientNeverReportsCompletion() throws Exception {
        var w = world("LOCKED", null);
        var u = student(0);
        long a = startAttempt(u, w);
        play(u, a, w.g1()).andExpect(status().isOk());
        clock.set(Instant.now().plusSeconds(180)); // audio 1 giây + margin 2 phút: link cũ đã hết hạn
        play(u, a, w.g1()).andExpect(status().isOk()).andExpect(jsonPath("$.playNo").value(2));
    }

    @Test
    void linkTtlFollowsAudioDuration() throws Exception {
        var w = world("EXAM", 3);
        jdbc.update("update media_assets set duration_ms = 600000 where id = (select audio_asset_id from question_groups where id = ?)", w.g1());
        var u = student(0);
        long a = startAttempt(u, w);
        // 10 phút + margin 2 phút = 720 giây
        play(u, a, w.g1()).andExpect(jsonPath("$.url").value(containsString("ttl=720")));
        play(u, a, w.g2()).andExpect(jsonPath("$.url").value(containsString("ttl=121")));
    }

    @Test
    void bankResponseNeverCarriesAudioKeyOrScript() throws Exception {
        var w = world("EXAM", 2);
        var u = student(0);
        var res = getJson("/api/banks/" + w.bank(), u).andExpect(status().isOk())
                .andExpect(jsonPath("$.playbackMode").value("EXAM")).andExpect(jsonPath("$.playLimit").value(2))
                .andExpect(jsonPath("$.groups[0].hasAudio").value(true)).andExpect(jsonPath("$.groups[0].audioDurationMs").value(1000))
                .andExpect(content().string(not(containsString("SCRIPT-PART")))).andExpect(content().string(not(containsString("media/audio"))))
                .andExpect(content().string(not(containsString("storage")))).andReturn().getResponse().getContentAsString();
        assertFalse(res.contains("transcript"));
    }

    @Test
    void playIsRestrictedToTheOwnerOpenAttemptAndItsOwnBank() throws Exception {
        var w = world("EXAM", 2);
        var other = world("EXAM", 2);
        var u = student(0);
        long a = startAttempt(u, w);

        play(student(0), a, w.g1()).andExpect(status().isNotFound()); // lượt của người khác
        play(u, a, other.g1()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("group_not_found")); // part bộ khác
        play(u, 999999L, w.g1()).andExpect(status().isNotFound());
        play(u, a, 999999L).andExpect(status().isNotFound());
        postJson("/api/attempts/abc/groups/" + w.g1() + "/play", u, Map.of()).andExpect(status().isNotFound());
        playDone(student(0), a, w.g1()).andExpect(status().isNotFound());
        playDone(u, a, w.g1()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("play_not_started"));

        jdbc.update("update question_groups set audio_asset_id = null where id = ?", w.g2());
        play(u, a, w.g2()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("audio_not_found"));

        postJson("/api/attempts/" + a + "/submit", u, Map.of("answers", java.util.List.of())).andExpect(status().isOk());
        play(u, a, w.g1()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("attempt_already_submitted"));
        play(null, a, w.g1()).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentPlayRequestsCannotExceedTheLimit() throws Exception {
        var w = world("EXAM", 1);
        var u = student(0);
        long a = startAttempt(u, w);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        var ok = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                tasks.add(pool.submit(() -> {
                    try {
                        if (play(u, a, w.g1()).andReturn().getResponse().getStatus() == 200) {
                            ok.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
            for (var t : tasks) {
                t.get();
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, ok.get(), "chỉ một request được cấp link");
        assertEquals(1, count("select count(*) from audio_plays where attempt_id = ?", a));
    }

    @Test
    void examBankOfAnotherTopicIsStillGatedByTheNormalBankGate() throws Exception {
        // audio không có đường vòng quanh BankGate: không bắt đầu được lượt thì không có lượt để nghe
        var w = world("LOCKED", null);
        jdbc.update("update question_banks set status = 'DRAFT' where id = ?", w.bank());
        postJson("/api/banks/" + w.bank() + "/attempts", student(0), Map.of()).andExpect(status().isNotFound());
    }
}
