package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.service.AttemptModes;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.listening.ListeningDtos.PlayGrant;
import com.pps.smartenglish.exercise.listening.ListeningDtos.PlayState;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.media.domain.MediaAsset;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-135: học viên nghe audio của part qua máy chủ để đếm lượt. Mỗi lần xin link là một lượt (trừ chế độ FREE); vượt giới hạn thì
 * từ chối. Nghe ép chỉ cho lượt kế khi lượt trước đã hết (client báo, hoặc link cũ đã hết hạn). Máy chủ không kiểm soát được việc
 * người cố ý lưu file từ link đã cấp: giới hạn này là chấp nhận có chủ đích của D-135.
 */
@Service
public class AudioPlayService {

    private final StudentAttemptRepository attempts;
    private final QuestionBankRepository banks;
    private final QuestionGroupRepository groups;
    private final AudioPlayRepository plays;
    private final AudioMediaService audio;
    private final ForcedRules rules;
    private final PlaybackPolicies policies;
    private final ListeningProperties props;
    private final CmsWrites writes;
    private final Clock clock;
    private final AttemptModes modes;

    AudioPlayService(StudentAttemptRepository attempts, QuestionBankRepository banks, QuestionGroupRepository groups,
                     AudioPlayRepository plays, AudioMediaService audio, ForcedRules rules, PlaybackPolicies policies,
                     ListeningProperties props, CmsWrites writes, Clock clock, AttemptModes modes) {
        this.attempts = attempts;
        this.banks = banks;
        this.groups = groups;
        this.plays = plays;
        this.audio = audio;
        this.rules = rules;
        this.policies = policies;
        this.props = props;
        this.writes = writes;
        this.clock = clock;
        this.modes = modes;
    }

    @Transactional
    public PlayGrant play(long userId, long attemptId, long groupId) {
        var attempt = openAttempt(userId, attemptId);
        var group = groupOf(attempt, groupId);
        MediaAsset media = audio.ready(group.getAudioAssetId() == null ? -1 : group.getAudioAssetId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "audio_not_found", "Part này không có audio"));
        QuestionBank bank = banks.findById(attempt.getBankId()).orElseThrow(AudioPlayService::attemptNotFound);
        var policy = policyFor(attempt, bank);
        Integer limit = policy.limit(bank, () -> rules.forAttempt(attempt));
        Instant now = Instant.now(clock);
        Duration ttl = ttlFor(media);
        if (limit == null) { // FREE: không đếm
            return new PlayGrant(policy.mode(), null, null, null, audio.signedUrl(media, ttl), now.plus(ttl));
        }
        var last = plays.findFirstByAttemptIdAndGroupIdOrderByPlayNoDesc(attemptId, groupId);
        int used = last.map(AudioPlay::getPlayNo).orElse(0);
        if (used >= limit) {
            throw new ApiException(HttpStatus.FORBIDDEN, "play_limit_reached", "Bạn đã dùng hết " + limit + " lần nghe của part này");
        }
        if (policy.replayNeedsFinish() && last.isPresent() && !last.get().finished(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "play_in_progress", "Chỉ nghe lại được khi audio đã phát hết");
        }
        // unique (lượt, part, playNo): hai request đồng thời không cùng chiếm một lượt
        writes.save(plays, new AudioPlay(attemptId, groupId, used + 1, now, now.plus(ttl)), "play_conflict",
                "Đang có yêu cầu nghe khác của part này, hãy thử lại");
        return new PlayGrant(policy.mode(), used + 1, limit, limit - used - 1, audio.signedUrl(media, ttl), now.plus(ttl));
    }

    /** Số lần nghe mỗi part theo bộ (luật mặc định của bộ nếu Nghe ép); null = không giới hạn. */
    @Transactional(readOnly = true)
    public Integer limitOf(QuestionBank bank) {
        return policies.of(bank.getPlaybackMode()).limit(bank, () -> rules.forBank(bank.getId()));
    }

    /** Client báo audio đã phát hết; idempotent. Chế độ không đếm (FREE) không có lượt nào để báo. */
    @Transactional
    public PlayState complete(long userId, long attemptId, long groupId) {
        var attempt = openAttempt(userId, attemptId);
        groupOf(attempt, groupId);
        var last = plays.findFirstByAttemptIdAndGroupIdOrderByPlayNoDesc(attemptId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "play_not_started", "Part này chưa có lượt nghe nào"));
        last.complete(Instant.now(clock));
        plays.save(last);
        QuestionBank bank = banks.findById(attempt.getBankId()).orElseThrow(AudioPlayService::attemptNotFound);
        Integer limit = policyFor(attempt, bank).limit(bank, () -> rules.forAttempt(attempt));
        return new PlayState(last.getPlayNo(), true, limit, limit == null ? null : Math.max(0, limit - last.getPlayNo()));
    }

    /**
     * Chính sách theo lượt, không theo chế độ hiện tại của bộ: lượt Nghe ép giữ luật đã chụp dù bộ đổi chế độ giữa chừng; lượt thường
     * mà bộ vừa đổi sang LOCKED thì áp như Thi (không áp luật Nghe ép lên lượt không đếm kết quả kiểu ép).
     */
    private PlaybackPolicy policyFor(StudentAttempt attempt, QuestionBank bank) {
        if (attempt.isCountOnly()) {
            return policies.of(QuestionBank.LOCKED);
        }
        String mode = bank.getPlaybackMode();
        // D-151: lượt Mô phỏng thi của bộ IELTS nghe theo luật thi dù bộ để FREE; bộ EXAM khác giữ FREE
        boolean exam = QuestionBank.LOCKED.equals(mode) || (QuestionBank.FREE.equals(mode) && modes.choosable(bank) && QuestionBank.EXAM.equals(modes.of(attempt)));
        return policies.of(exam ? QuestionBank.EXAM : mode);
    }

    /** Hạn link: độ dài audio + margin nếu biết, ngược lại mặc định; không vượt cận trên. */
    private Duration ttlFor(MediaAsset media) {
        Duration ttl = media.getDurationMs() == null ? props.urlTtl()
                : Duration.ofMillis(media.getDurationMs()).plus(props.urlMargin());
        return ttl.compareTo(props.maxUrlTtl()) > 0 ? props.maxUrlTtl() : ttl;
    }

    /** Lượt của chính người gọi, còn đang làm. Lượt người khác coi như không tồn tại (NFR-SEC-05). */
    private StudentAttempt openAttempt(long userId, long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId).orElseThrow(AudioPlayService::attemptNotFound);
        if (a.getSubmittedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
        if (a.expired(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_time_up", "Đã hết giờ làm bài");
        }
        return a;
    }

    /** Part phải thuộc đúng bộ của lượt: part của bộ khác là 404. */
    private QuestionGroup groupOf(StudentAttempt attempt, long groupId) {
        return groups.findById(groupId).filter(g -> attempt.getBankId().equals(g.getBankId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "group_not_found", "Không tìm thấy part"));
    }

    private static ApiException attemptNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }
}
