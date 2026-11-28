package com.pps.smartenglish.lessoncontent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "lesson_glossary")
class GlossaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", updatable = false)
    private Long lessonId;

    private String phrase;

    @Column(name = "phrase_key")
    private String phraseKey;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "meaning_vi")
    private String meaningVi;

    private String example;
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected GlossaryEntry() {}

    GlossaryEntry(Long lessonId) {
        this.lessonId = lessonId;
    }

    void setPhrase(String phrase) {
        this.phrase = phrase;
        this.phraseKey = key(phrase);
    }

    /** Khóa so trùng: không phân biệt hoa thường. */
    static String key(String phrase) {
        return phrase.toLowerCase(Locale.ROOT);
    }

    void setPartOfSpeech(String v) { this.partOfSpeech = v; }
    void setMeaningVi(String v) { this.meaningVi = v; }
    void setExample(String v) { this.example = v; }
    void setSource(String v) { this.source = v; }

    Long getId() { return id; }
    Long getLessonId() { return lessonId; }
    String getPhrase() { return phrase; }
    String getPhraseKey() { return phraseKey; }
    String getPartOfSpeech() { return partOfSpeech; }
    String getMeaningVi() { return meaningVi; }
    String getExample() { return example; }
    String getSource() { return source; }
}
