package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.lessoncontent.DictionaryService;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.CreateGlossary;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.DictionarySuggestion;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.GlossaryItem;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.PatchGlossary;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.SubtitleInfo;
import com.pps.smartenglish.lessoncontent.LessonGlossaryService;
import com.pps.smartenglish.lessoncontent.LessonSubtitleService;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** FR-ADM-01, D-175: phụ đề, glossary của bài và gợi ý từ điển. Quyền cms.content qua /api/admin/cms/** (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/cms")
class CmsLessonContentController {

    private final LessonSubtitleService subtitles;
    private final LessonGlossaryService glossary;
    private final DictionaryService dictionary;

    CmsLessonContentController(LessonSubtitleService subtitles, LessonGlossaryService glossary, DictionaryService dictionary) {
        this.subtitles = subtitles;
        this.glossary = glossary;
        this.dictionary = dictionary;
    }

    @PutMapping(value = "/lessons/{id}/subtitles/{lang}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SubtitleInfo putSubtitle(@PathVariable String id, @PathVariable String lang, @RequestParam("file") MultipartFile file) {
        return subtitles.put(lessonId(id), lang, file);
    }

    @DeleteMapping("/lessons/{id}/subtitles/{lang}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSubtitle(@PathVariable String id, @PathVariable String lang) {
        subtitles.delete(lessonId(id), lang);
    }

    @GetMapping("/lessons/{id}/glossary")
    List<GlossaryItem> listGlossary(@PathVariable String id) {
        return glossary.list(lessonId(id));
    }

    @PostMapping("/lessons/{id}/glossary")
    @ResponseStatus(HttpStatus.CREATED)
    GlossaryItem createGlossary(@PathVariable String id, @Valid @RequestBody CreateGlossary req) {
        return glossary.create(lessonId(id), req);
    }

    @PatchMapping("/glossary/{gid}")
    GlossaryItem patchGlossary(@PathVariable String gid, @Valid @RequestBody PatchGlossary req) {
        return glossary.patch(glossaryId(gid), req);
    }

    @DeleteMapping("/glossary/{gid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGlossary(@PathVariable String gid) {
        glossary.delete(glossaryId(gid));
    }

    @GetMapping("/dictionary")
    List<DictionarySuggestion> suggest(@RequestParam(required = false) String q) {
        return dictionary.suggest(q);
    }

    private static long lessonId(String raw) {
        return PathId.parse(raw, "lesson_not_found", "Không tìm thấy bài học");
    }

    private static long glossaryId(String raw) {
        return PathId.parse(raw, "glossary_not_found", "Không tìm thấy mục glossary");
    }
}
