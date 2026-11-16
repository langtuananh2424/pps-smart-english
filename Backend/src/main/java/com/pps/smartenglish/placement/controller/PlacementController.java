package com.pps.smartenglish.placement.controller;

import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.placement.dto.PlacementDtos.MyPlacement;
import com.pps.smartenglish.placement.dto.PlacementDtos.Progress;
import com.pps.smartenglish.placement.dto.PlacementDtos.Recommendation;
import com.pps.smartenglish.placement.dto.PlacementDtos.Result;
import com.pps.smartenglish.placement.dto.PlacementDtos.Started;
import com.pps.smartenglish.placement.dto.PlacementDtos.Submitted;
import com.pps.smartenglish.placement.service.PlacementResultService;
import com.pps.smartenglish.placement.service.PlacementStartService;
import com.pps.smartenglish.placement.service.PlacementSubmitService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.ClientIp;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-TEST-01/02, FR-GOAL-03. start, draft, submit mở cho khách (SecurityConfig): quyền được kiểm trong service bằng
 * user hoặc header X-Placement-Token. claim và /api/me/* cần đăng nhập.
 */
@RestController
class PlacementController {

    private static final String TOKEN = "X-Placement-Token";

    private final PlacementStartService start;
    private final PlacementSubmitService submits;
    private final PlacementResultService results;

    PlacementController(PlacementStartService start, PlacementSubmitService submits, PlacementResultService results) {
        this.start = start;
        this.submits = submits;
        this.results = results;
    }

    @PostMapping("/api/placement/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    Started start(Authentication auth, HttpServletRequest req) {
        return start.start(CurrentUser.idOrNull(auth), ClientIp.of(req));
    }

    @PutMapping("/api/placement/attempts/{id}/draft")
    Progress draft(@PathVariable String id, Authentication auth, @RequestHeader(value = TOKEN, required = false) String token,
                   @Valid @RequestBody SubmitRequest body) {
        return submits.saveDraft(attemptId(id), CurrentUser.idOrNull(auth), token, body);
    }

    /** Không gửi body = nộp theo nháp. */
    @PostMapping("/api/placement/attempts/{id}/submit")
    Submitted submit(@PathVariable String id, Authentication auth, @RequestHeader(value = TOKEN, required = false) String token,
                     @Valid @RequestBody(required = false) SubmitRequest body) {
        return submits.submit(attemptId(id), CurrentUser.idOrNull(auth), token, body);
    }

    @PostMapping("/api/placement/attempts/{id}/claim")
    Result claim(@PathVariable String id, Authentication auth, @RequestHeader(value = TOKEN, required = false) String token) {
        Long userId = CurrentUser.idOrNull(auth);
        if (userId == null) {
            throw ApiException.unauthenticated();
        }
        return submits.claim(attemptId(id), userId, token);
    }

    @GetMapping("/api/me/placement")
    MyPlacement mine(Authentication auth) {
        return results.mine(CurrentUser.id(auth));
    }

    @GetMapping("/api/me/recommendation")
    Recommendation recommendation(Authentication auth) {
        return results.recommendation(CurrentUser.id(auth));
    }

    private static long attemptId(String raw) {
        return PathId.parse(raw, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }
}
