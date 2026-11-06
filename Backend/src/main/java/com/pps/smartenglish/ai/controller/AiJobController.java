package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.dto.JobView;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.ai.stream.JobStreamService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Đọc kết quả job: một lần (poll) hoặc luồng SSE. Chỉ chủ job. */
@RestController
@RequestMapping("/api/ai/jobs")
class AiJobController {

    private final AiJobService jobs;
    private final JobStreamService streams;

    AiJobController(AiJobService jobs, JobStreamService streams) {
        this.jobs = jobs;
        this.streams = streams;
    }

    @GetMapping("/{id}")
    JobView get(Authentication auth, @PathVariable String id) {
        return jobs.view(CurrentUser.id(auth), jobId(id));
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(Authentication auth, @PathVariable String id) {
        return streams.open(CurrentUser.id(auth), jobId(id));
    }

    private static Long jobId(String raw) {
        return PathId.parse(raw, "job_not_found", "Không tìm thấy job");
    }
}
