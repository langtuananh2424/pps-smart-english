package com.pps.smartenglish.lessoncontent;

import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.CreateGlossary;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.GlossaryItem;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.GlossaryTerm;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.PatchGlossary;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-175: glossary của bài do Biên tập viên nhập; học viên chạm cụm từ thì mở popup giải nghĩa. */
@Service
public class LessonGlossaryService {

    private static final String DUPLICATE = "glossary_duplicate";

    private final GlossaryRepository glossary;
    private final LessonRepository lessons;
    private final LessonContentProperties props;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    LessonGlossaryService(GlossaryRepository glossary, LessonRepository lessons, LessonContentProperties props, CmsWrites writes,
                          AuditRecorder audit) {
        this.glossary = glossary;
        this.lessons = lessons;
        this.props = props;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GlossaryItem> list(long lessonId) {
        requireLesson(lessonId);
        return glossary.findByLessonIdOrderByIdAsc(lessonId).stream().map(LessonGlossaryService::item).toList();
    }

    @Transactional
    public GlossaryItem create(long lessonId, CreateGlossary req) {
        requireLesson(lessonId);
        String phrase = req.phrase().strip();
        if (glossary.existsByLessonIdAndPhraseKey(lessonId, GlossaryEntry.key(phrase))) {
            throw duplicate();
        }
        // shortcut: đếm rồi chèn nên request đồng thời có thể vượt trần 1-2 mục, khóa theo bài nếu cần chính xác.
        if (glossary.countByLessonId(lessonId) >= props.glossary().maxItems()) {
            throw new ApiException(HttpStatus.CONFLICT, "glossary_full", "Bài đã đủ " + props.glossary().maxItems() + " mục");
        }
        var e = new GlossaryEntry(lessonId);
        e.setPhrase(phrase);
        e.setPartOfSpeech(blankToNull(req.partOfSpeech()));
        e.setMeaningVi(req.meaningVi().strip());
        e.setExample(blankToNull(req.example()));
        e.setSource(req.source());
        var after = item(writes.save(glossary, e, DUPLICATE, "Cụm từ đã có trong bài"));
        audit.record("CREATE", "LESSON_GLOSSARY", after.id(), null, after);
        return after;
    }

    @Transactional
    public GlossaryItem patch(long id, PatchGlossary req) {
        var e = find(id);
        var before = item(e);
        if (req.phrase() != null) {
            String phrase = req.phrase().strip();
            if (glossary.existsByLessonIdAndPhraseKeyAndIdNot(e.getLessonId(), GlossaryEntry.key(phrase), id)) {
                throw duplicate();
            }
            e.setPhrase(phrase);
        }
        if (req.partOfSpeech() != null) {
            e.setPartOfSpeech(blankToNull(req.partOfSpeech()));
        }
        if (req.meaningVi() != null) {
            e.setMeaningVi(req.meaningVi().strip());
        }
        if (req.example() != null) {
            e.setExample(blankToNull(req.example()));
        }
        if (req.source() != null) {
            e.setSource(req.source());
        }
        var after = item(writes.save(glossary, e, DUPLICATE, "Cụm từ đã có trong bài"));
        audit.record("UPDATE", "LESSON_GLOSSARY", id, before, after);
        return after;
    }

    @Transactional
    public void delete(long id) {
        var e = find(id);
        audit.record("DELETE", "LESSON_GLOSSARY", id, item(e), null);
        glossary.delete(e);
    }

    /** Phía học viên: một truy vấn theo bài. Chỉ gọi sau khi qua cổng quyền xem. */
    @Transactional(readOnly = true)
    public List<GlossaryTerm> terms(long lessonId) {
        return glossary.findByLessonIdOrderByIdAsc(lessonId).stream()
                .map(e -> new GlossaryTerm(e.getPhrase(), e.getPartOfSpeech(), e.getMeaningVi(), e.getExample(), e.getSource())).toList();
    }

    private GlossaryEntry find(long id) {
        return glossary.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "glossary_not_found", "Không tìm thấy mục glossary"));
    }

    private void requireLesson(long id) {
        if (!lessons.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học");
        }
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, DUPLICATE, "Cụm từ đã có trong bài");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static GlossaryItem item(GlossaryEntry e) {
        return new GlossaryItem(e.getId(), e.getPhrase(), e.getPartOfSpeech(), e.getMeaningVi(), e.getExample(), e.getSource());
    }
}
