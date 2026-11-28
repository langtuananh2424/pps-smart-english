package com.pps.smartenglish.lessoncontent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Chỉ đọc; DictionaryImporter ghi bằng JDBC theo lô. */
@Entity
@Immutable
@Table(name = "dictionary_entries")
class DictionaryEntry {

    @Id
    private Long id;

    private String word;

    @Column(name = "word_key")
    private String wordKey;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "meaning_vi")
    private String meaningVi;

    private String example;

    protected DictionaryEntry() {}

    String getWord() { return word; }
    String getPartOfSpeech() { return partOfSpeech; }
    String getMeaningVi() { return meaningVi; }
    String getExample() { return example; }
}
