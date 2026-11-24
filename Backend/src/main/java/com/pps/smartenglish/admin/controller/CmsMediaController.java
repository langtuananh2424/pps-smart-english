package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.listening.AudioMediaService;
import com.pps.smartenglish.exercise.listening.ListeningDtos.AudioComplete;
import com.pps.smartenglish.exercise.listening.ListeningDtos.AudioFile;
import com.pps.smartenglish.media.dto.MediaDtos.MediaFile;
import com.pps.smartenglish.media.dto.MediaDtos.UploadPermit;
import com.pps.smartenglish.media.dto.MediaDtos.UploadRequest;
import com.pps.smartenglish.media.service.MediaService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** D-134: tải ảnh cho câu hỏi. Quyền cms.content qua /api/admin/cms/** (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/cms/media")
class CmsMediaController {

    private final MediaService media;
    private final AudioMediaService audio;

    CmsMediaController(MediaService media, AudioMediaService audio) {
        this.media = media;
        this.audio = audio;
    }

    /** D-135: audio của part. Luồng như ảnh: xin quyền tải lên, tải thẳng lên kho, gọi complete-audio (kèm độ dài nếu biết). */
    @PostMapping("/audio")
    @ResponseStatus(HttpStatus.CREATED)
    UploadPermit createAudioUpload(Authentication auth, @Valid @RequestBody UploadRequest req) {
        return audio.createUpload(CurrentUser.id(auth), req.contentType());
    }

    @PostMapping("/{id}/complete-audio")
    AudioFile completeAudio(Authentication auth, @PathVariable String id, @RequestBody(required = false) AudioComplete req) {
        return audio.complete(CurrentUser.id(auth), PathId.parse(id, "media_not_found", "Không tìm thấy audio"),
                req == null ? null : req.durationMs());
    }

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    UploadPermit createImageUpload(Authentication auth, @Valid @RequestBody UploadRequest req) {
        return media.createImageUpload(CurrentUser.id(auth), req.contentType());
    }

    @PostMapping("/{id}/complete")
    MediaFile complete(Authentication auth, @PathVariable String id) {
        return media.complete(CurrentUser.id(auth), PathId.parse(id, "media_not_found", "Không tìm thấy ảnh"));
    }
}
