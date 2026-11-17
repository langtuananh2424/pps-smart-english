package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.service.AiGuard;
import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.CompleteResponse;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.QuestionView;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.SubmissionView;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.UploadPermit;
import com.pps.smartenglish.commerce.service.EmailVerification;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * FR-AI-03, D-27: xin quyền tải lên (presigned POST) → client tải thẳng lên MinIO → complete kiểm file thật rồi mới trừ lượt.
 * Không giữ transaction trong lúc gọi kho; phần trừ lượt nằm ở SpeakingEnqueuer.
 */
@Service
public class SpeakingService {

    static final String CONTENT_TYPE = "audio/wav";
    /** WAV mono 16 kHz 16-bit ≈ 32 KB/s; dư 10% và 1 KB header; trần tuyệt đối 12 MB. */
    static final long BYTES_PER_SECOND = 32_000;
    static final long ABSOLUTE_MAX_BYTES = 12L * 1024 * 1024;
    static final double MIN_SECONDS = 1.0;
    static final double DURATION_SLACK_SECONDS = 2.0;
    private static final int HEADER_BYTES = 4096;
    private static final Duration AUDIO_URL_TTL = Duration.ofMinutes(15);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final SpeakingSubmissionRepository submissions;
    private final SpeakingQuestionSource questions;
    private final SpeakingEnqueuer enqueuer;
    private final ObjectStorage storage;
    private final AiQuotaService quota;
    private final AiGuard guard;
    private final EmailVerification emailVerification;
    private final SpeakingProperties props;
    private final Clock clock;

    SpeakingService(SpeakingSubmissionRepository submissions, SpeakingQuestionSource questions, SpeakingEnqueuer enqueuer,
                    ObjectStorage storage, AiQuotaService quota, AiGuard guard, EmailVerification emailVerification, SpeakingProperties props,
                    Clock clock) {
        this.submissions = submissions;
        this.questions = questions;
        this.enqueuer = enqueuer;
        this.storage = storage;
        this.quota = quota;
        this.guard = guard;
        this.emailVerification = emailVerification;
        this.props = props;
        this.clock = clock;
    }

    static long maxBytesFor(int maxSeconds) {
        return Math.min(ABSOLUTE_MAX_BYTES, (long) Math.ceil(maxSeconds * BYTES_PER_SECOND * 1.1) + 1024);
    }

    /** Chưa trừ lượt: chỉ kiểm quyền bank, rate limit và còn đủ lượt. */
    public UploadPermit create(long userId, long questionId) {
        emailVerification.require(userId);
        var q = questions.find(questionId, userId);
        guard.rateLimit(userId);
        quota.requireAvailable(userId, q.courseId(), AiTask.SPEAKING_PART);
        Instant now = Instant.now(clock);
        long maxBytes = maxBytesFor(q.maxSeconds());
        String key = "speaking/" + userId + "/" + UUID.randomUUID() + ".wav";
        var s = submissions.save(new SpeakingSubmission(userId, q.id(), q.courseId(), key, q.maxSeconds(), maxBytes,
                now.plus(props.uploadTtl())));
        var form = storage.presignPost(key, CONTENT_TYPE, maxBytes, props.uploadTtl());
        return new UploadPermit(s.getId(), form.url(), form.fields(), maxBytes, CONTENT_TYPE, q.maxSeconds(), s.getUploadExpiresAt());
    }

    /** Idempotent: đã xác nhận rồi thì trả trạng thái hiện tại, không trừ lượt lần hai. */
    public CompleteResponse complete(long userId, long id) {
        var s = load(userId, id);
        if (!SpeakingSubmission.AWAITING_UPLOAD.equals(s.getStatus())) {
            if (SpeakingSubmission.EXPIRED.equals(s.getStatus())) {
                throw expired();
            }
            return response(s);
        }
        if (!Instant.now(clock).isBefore(s.getUploadExpiresAt())) {
            throw expired();
        }
        var q = questions.find(s.getQuestionId(), userId);
        var audio = inspect(s);
        enqueuer.enqueue(s, q, audio.size(), audio.duration());
        return response(load(userId, id));
    }

    /** Cùng cổng quyền với create: câu của bộ thi khóa hoặc bộ bị khóa không xem được đề (D-82). */
    public QuestionView question(long userId, long questionId) {
        var q = questions.find(questionId, userId);
        return new QuestionView(q.id(), q.part(), q.prompt(), q.cueCard(), q.maxSeconds());
    }

    public SubmissionView view(long userId, long id) {
        return toView(load(userId, id));
    }

    public CursorPage<SubmissionView> list(long userId, Long questionId, String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        long before = Cursors.decode(cursor);
        var page = PageRequest.of(0, size + 1);
        List<SpeakingSubmission> rows = questionId == null
                ? submissions.findByUserIdAndIdLessThanOrderByIdDesc(userId, before, page)
                : submissions.findByUserIdAndQuestionIdAndIdLessThanOrderByIdDesc(userId, questionId, before, page);
        boolean more = rows.size() > size;
        List<SpeakingSubmission> shown = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(shown.stream().map(this::toView).toList(), more ? Cursors.encode(shown.get(size - 1).getId()) : null);
    }

    private record Inspected(long size, double duration) {}

    /** HEAD + đọc header WAV. File hỏng thì xóa object (học viên tải lại được bằng cùng policy nếu còn hạn). */
    private Inspected inspect(SpeakingSubmission s) {
        var meta = storage.head(s.getObjectKey())
                .orElseThrow(() -> bad("audio_missing", "Chưa thấy file âm thanh được tải lên"));
        if (meta.size() > s.getMaxBytes()) {
            throw reject(s, "audio_too_large", "File âm thanh vượt dung lượng cho phép");
        }
        double duration;
        try {
            duration = WavHeader.durationSeconds(storage.readHead(s.getObjectKey(), HEADER_BYTES), meta.size());
        } catch (IllegalArgumentException e) {
            throw reject(s, "audio_invalid", "File phải là WAV PCM 16-bit");
        }
        if (duration > s.getMaxSeconds() + DURATION_SLACK_SECONDS) {
            throw reject(s, "audio_too_long", "Bản ghi dài quá " + s.getMaxSeconds() + " giây");
        }
        if (duration < MIN_SECONDS) {
            throw reject(s, "audio_too_short", "Bản ghi quá ngắn");
        }
        return new Inspected(meta.size(), duration);
    }

    private ApiException reject(SpeakingSubmission s, String code, String message) {
        storage.delete(s.getObjectKey());
        return bad(code, message);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "upload_expired", "Đã quá hạn tải lên, hãy xin quyền tải lên lại");
    }

    private SpeakingSubmission load(long userId, long id) {
        return submissions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "submission_not_found", "Không tìm thấy lượt nói"));
    }

    private CompleteResponse response(SpeakingSubmission s) {
        return new CompleteResponse(s.getId(), s.getStatus(), s.getJobId(), quota.available(s.getUserId(), s.getCourseId()));
    }

    private SubmissionView toView(SpeakingSubmission s) {
        boolean hasAudio = s.getAudioDeletedAt() == null && s.getSizeBytes() != null
                && !SpeakingSubmission.AWAITING_UPLOAD.equals(s.getStatus()) && !SpeakingSubmission.EXPIRED.equals(s.getStatus());
        return new SubmissionView(s.getId(), s.getQuestionId(), s.getStatus(), s.getResult(),
                hasAudio ? storage.presignGet(s.getObjectKey(), AUDIO_URL_TTL) : null, s.getDurationSec(), s.getCreatedAt());
    }
}
