package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.CreateGroup;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.GroupView;
import com.pps.smartenglish.exercise.dto.QuestionGroupDtos.PatchGroup;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-133 (FR-ADM-02): nhóm câu hỏi dùng chung bài đọc. Chạy cho ADMIN và EDITOR. Nhóm còn câu hỏi thì không xóa được. */
@Service
public class CmsGroupService {

    private static final String AUDIT_TYPE = "QUESTION_GROUP";

    private final QuestionGroupRepository groups;
    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final RichText rich;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    CmsGroupService(QuestionGroupRepository groups, QuestionBankRepository banks, QuestionRepository questions, RichText rich,
                    CmsWrites writes, AuditRecorder audit) {
        this.groups = groups;
        this.banks = banks;
        this.questions = questions;
        this.rich = rich;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public GroupView create(long bankId, CreateGroup req) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        var group = new QuestionGroup(bankId, req.sortOrder() == null ? 0 : req.sortOrder(), blankToNull(req.title()),
                blankToNull(req.instruction()), passage(req.passageHtml()));
        var after = view(writes.save(groups, group, "conflict", "Không lưu được nhóm câu hỏi"));
        audit.record("CREATE", AUDIT_TYPE, after.id(), null, after);
        return after;
    }

    @Transactional
    public GroupView patch(long id, PatchGroup req) {
        QuestionGroup g = find(id);
        var before = view(g);
        if (req.title() != null) {
            g.setTitle(blankToNull(req.title()));
        }
        if (req.instruction() != null) {
            g.setInstruction(blankToNull(req.instruction()));
        }
        if (req.passageHtml() != null) {
            g.setPassageHtml(passage(req.passageHtml()));
        }
        if (req.sortOrder() != null) {
            g.setSortOrder(req.sortOrder());
        }
        var after = view(writes.save(groups, g, "conflict", "Không lưu được nhóm câu hỏi"));
        audit.record("UPDATE", AUDIT_TYPE, id, before, after);
        return after;
    }

    /** Còn câu hỏi trong nhóm thì in_use; khóa ngoại là chốt cuối cho trường hợp tranh chấp. */
    @Transactional
    public void delete(long id) {
        QuestionGroup g = find(id);
        if (questions.existsByGroupId(id)) {
            throw CmsWrites.inUse();
        }
        audit.record("DELETE", AUDIT_TYPE, id, view(g), null);
        writes.delete(groups, g);
    }

    /** Nhóm của một bộ theo sortOrder; một lần dựng HTML cho cả danh sách. */
    @Transactional(readOnly = true)
    public List<GroupView> ofBank(long bankId) {
        return views(groups.findByBankIdOrderBySortOrderAscIdAsc(bankId));
    }

    /** Rỗng sau khi làm sạch (không chữ, không ảnh) = không có bài đọc. */
    private String passage(String html) {
        if (html == null) {
            return null;
        }
        var s = rich.sanitize(html);
        rich.requireReady(s.assetIds());
        return s.text().isEmpty() && s.assetIds().isEmpty() ? null : s.html();
    }

    private GroupView view(QuestionGroup g) {
        return views(List.of(g)).get(0);
    }

    private List<GroupView> views(List<QuestionGroup> rows) {
        Map<String, String> html = rich.render(rows.stream().map(QuestionGroup::getPassageHtml).toList());
        return rows.stream().map(g -> new GroupView(g.getId(), g.getBankId(), g.getSortOrder(), g.getTitle(), g.getInstruction(),
                g.getPassageHtml() == null ? null : html.get(g.getPassageHtml()))).toList();
    }

    private QuestionGroup find(long id) {
        return groups.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "group_not_found", "Không tìm thấy nhóm câu hỏi"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
