package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.ProgressDtos.CreateUnlock;
import com.pps.smartenglish.classroom.dto.ProgressDtos.ProgressBoard;
import com.pps.smartenglish.classroom.dto.ProgressDtos.UnlockView;
import com.pps.smartenglish.classroom.service.ClassProgressService;
import com.pps.smartenglish.classroom.service.EarlyUnlockService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-CLS-05, FR-CLS-06: bảng tiến độ lớp và mở sớm unit; chỉ nhân sự của lớp hoặc giáo vụ (ClassAccess, NFR-SEC-05). */
@RestController
class ClassProgressController {

    private static final String CLASS_NF = "class_not_found";
    private static final String CLASS_NF_MSG = "Không tìm thấy lớp";

    private final ClassProgressService progress;
    private final EarlyUnlockService unlocks;

    ClassProgressController(ClassProgressService progress, EarlyUnlockService unlocks) {
        this.progress = progress;
        this.unlocks = unlocks;
    }

    @GetMapping("/api/classes/{classId}/progress")
    ProgressBoard board(Authentication auth, @PathVariable String classId) {
        return progress.board(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG));
    }

    @GetMapping("/api/classes/{classId}/unlocks")
    List<UnlockView> unlocks(Authentication auth, @PathVariable String classId) {
        return unlocks.list(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG));
    }

    @PostMapping("/api/classes/{classId}/unlocks")
    @ResponseStatus(HttpStatus.CREATED)
    List<UnlockView> unlock(Authentication auth, @PathVariable String classId, @Valid @RequestBody CreateUnlock req) {
        return unlocks.create(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG), req);
    }

    @DeleteMapping("/api/classes/{classId}/unlocks/{unlockId}")
    UnlockView revoke(Authentication auth, @PathVariable String classId, @PathVariable String unlockId) {
        return unlocks.revoke(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(unlockId, "unlock_not_found", "Không tìm thấy lần mở sớm"));
    }
}
