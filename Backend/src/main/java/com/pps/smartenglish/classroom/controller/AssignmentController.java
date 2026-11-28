package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.AssignmentDtos.AiSubmissionDetail;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignmentView;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.CreateAssignment;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.GradeRequest;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.GradeView;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.MyResult;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.ProgressRow;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.UpdateAssignment;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignableTree;
import com.pps.smartenglish.classroom.service.AssignableService;
import com.pps.smartenglish.classroom.service.AssignmentProgressService;
import com.pps.smartenglish.classroom.service.AssignmentService;
import com.pps.smartenglish.classroom.service.AssignmentSubmissionReader;
import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-ASG-01..04: giao bài, làm bài được giao, theo dõi và chấm. Quyền theo lớp kiểm trong service qua ClassAccess (NFR-SEC-05). */
@RestController
class AssignmentController {

    private static final String BASE = "/api/classes/{classId}/assignments";
    private static final String CLASS_NF = "class_not_found";
    private static final String CLASS_NF_MSG = "Không tìm thấy lớp";
    private static final String ASG_NF = "assignment_not_found";
    private static final String ASG_NF_MSG = "Không tìm thấy bài được giao";

    private final AssignmentService assignments;
    private final AssignmentProgressService progress;
    private final AssignmentSubmissionReader submissions;
    private final AssignableService assignable;

    AssignmentController(AssignmentService assignments, AssignmentProgressService progress, AssignmentSubmissionReader submissions,
                         AssignableService assignable) {
        this.assignable = assignable;
        this.assignments = assignments;
        this.progress = progress;
        this.submissions = submissions;
    }

    private static long cls(String raw) {
        return PathId.parse(raw, CLASS_NF, CLASS_NF_MSG);
    }

    private static long asg(String raw) {
        return PathId.parse(raw, ASG_NF, ASG_NF_MSG);
    }

    /** FR-ASG-01: bộ và câu Viết/Nói giao được, theo unit của khóa của lớp. */
    @GetMapping("/api/classes/{classId}/assignable")
    AssignableTree assignable(Authentication auth, @PathVariable String classId) {
        return assignable.tree(CurrentUser.id(auth), cls(classId));
    }

    @PostMapping(BASE)
    @ResponseStatus(HttpStatus.CREATED)
    AssignmentView create(Authentication auth, @PathVariable String classId, @Valid @RequestBody CreateAssignment req) {
        return assignments.create(CurrentUser.id(auth), cls(classId), req);
    }

    /** D-155: đổi chế độ Luyện tập/Thi của bài giao (mode null = theo bộ). */
    @PatchMapping(BASE + "/{id}")
    AssignmentView updateMode(Authentication auth, @PathVariable String classId, @PathVariable String id, @RequestBody UpdateAssignment req) {
        return assignments.updateMode(CurrentUser.id(auth), cls(classId), asg(id), req);
    }

    @GetMapping(BASE)
    List<AssignmentView> list(Authentication auth, @PathVariable String classId) {
        return assignments.list(CurrentUser.id(auth), cls(classId));
    }

    @GetMapping("/api/me/assignments")
    List<AssignmentView> mine(Authentication auth) {
        return assignments.mine(CurrentUser.id(auth));
    }

    @GetMapping("/api/me/assignments/{id}")
    AssignmentView mineOne(Authentication auth, @PathVariable String id) {
        return assignments.mineOne(CurrentUser.id(auth), asg(id));
    }

    @PostMapping(BASE + "/{id}/cancel")
    AssignmentView cancel(Authentication auth, @PathVariable String classId, @PathVariable String id) {
        return assignments.cancel(CurrentUser.id(auth), cls(classId), asg(id));
    }

    @PostMapping(BASE + "/{id}/start")
    AttemptStarted start(Authentication auth, @PathVariable String classId, @PathVariable String id) {
        return assignments.start(CurrentUser.id(auth), cls(classId), asg(id));
    }

    @GetMapping(BASE + "/{id}/mine")
    MyResult myResult(Authentication auth, @PathVariable String classId, @PathVariable String id) {
        return progress.mine(CurrentUser.id(auth), cls(classId), asg(id));
    }

    @GetMapping(BASE + "/{id}/progress")
    List<ProgressRow> progress(Authentication auth, @PathVariable String classId, @PathVariable String id) {
        return progress.progress(CurrentUser.id(auth), cls(classId), asg(id));
    }

    /** D-141: giáo viên mở bài nộp Viết/Nói; submissionId = attemptId của bảng tiến độ. */
    @GetMapping(BASE + "/{id}/submissions/{submissionId}")
    AiSubmissionDetail submission(Authentication auth, @PathVariable String classId, @PathVariable String id, @PathVariable String submissionId) {
        return submissions.read(CurrentUser.id(auth), cls(classId), asg(id),
                PathId.parse(submissionId, "submission_not_found", "Không tìm thấy bài nộp của bài này"));
    }

    @PutMapping(BASE + "/{id}/attempts/{attemptId}/grade")
    @ResponseStatus(HttpStatus.CREATED)
    GradeView grade(Authentication auth, @PathVariable String classId, @PathVariable String id, @PathVariable String attemptId,
                    @Valid @RequestBody GradeRequest req) {
        return progress.grade(CurrentUser.id(auth), cls(classId), asg(id),
                PathId.parse(attemptId, "attempt_not_found", "Không tìm thấy lượt làm bài"), req);
    }

    @GetMapping(BASE + "/{id}/attempts/{attemptId}/grades")
    List<GradeView> history(Authentication auth, @PathVariable String classId, @PathVariable String id, @PathVariable String attemptId) {
        return progress.history(CurrentUser.id(auth), cls(classId), asg(id),
                PathId.parse(attemptId, "attempt_not_found", "Không tìm thấy lượt làm bài"));
    }
}
