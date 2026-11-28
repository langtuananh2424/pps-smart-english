package com.pps.smartenglish.lessoncontent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class LessonContentDtos {

    private static final String NOT_BLANK = ".*\\S.*";
    private static final String SOURCE = "MANUAL|WIKTIONARY";

    private LessonContentDtos() {}

    /** lessonId chỉ để gom theo bài, không ra JSON. */
    public record SubtitleInfo(@JsonIgnore Long lessonId, String lang, int cueCount, Instant updatedAt) {}

    public record GlossaryItem(Long id, String phrase, String partOfSpeech, String meaningVi, String example, String source) {}

    /** Phía học viên: không lộ id. */
    public record GlossaryTerm(String phrase, String partOfSpeech, String meaningVi, String example, String source) {}

    public record CreateGlossary(
            @NotBlank @Size(max = 100) String phrase,
            @Size(max = 30) String partOfSpeech,
            @NotBlank @Size(max = 500) String meaningVi,
            @Size(max = 500) String example,
            @NotBlank @Pattern(regexp = SOURCE, message = "MANUAL hoặc WIKTIONARY") String source) {}

    public record PatchGlossary(
            @Size(max = 100) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String phrase,
            @Size(max = 30) String partOfSpeech,
            @Size(max = 500) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String meaningVi,
            @Size(max = 500) String example,
            @Pattern(regexp = SOURCE, message = "MANUAL hoặc WIKTIONARY") String source) {}

    public record DictionarySuggestion(String word, String partOfSpeech, String meaningVi, String example) {}
}
