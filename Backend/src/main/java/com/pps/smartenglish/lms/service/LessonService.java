package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.LessonResponse;
import com.pps.smartenglish.lms.dto.LessonResponse.VideoInfo;
import com.pps.smartenglish.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonService {

    /** NFR-SEC-02: URL ký sống đúng 120 phút. */
    static final Duration URL_TTL = Duration.ofMinutes(120);

    private final LessonGate gate;
    private final ObjectStorage storage;
    private final Clock clock;

    LessonService(LessonGate gate, ObjectStorage storage, Clock clock) {
        this.gate = gate;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LessonResponse get(Long lessonId, Long userId) {
        var l = gate.require(lessonId, userId);
        return new LessonResponse(l.id(), l.title(), l.type(), l.durationSec(), video(l.videoSource(), l.videoRef()));
    }

    private VideoInfo video(String source, String ref) {
        if (source == null || ref == null || ref.isBlank()) {
            return null;
        }
        if ("YOUTUBE".equals(source)) {
            return new VideoInfo(source, null, ref, null);
        }
        Instant expiresAt = Instant.now(clock).plus(URL_TTL);
        return new VideoInfo(source, storage.presignGet(ref, URL_TTL), null, expiresAt);
    }
}
