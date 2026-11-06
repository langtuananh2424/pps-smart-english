package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** 8 yếu tố ngữ pháp có sẵn (dữ liệu trong migration V7); CMS chỉ đọc để gắn thẻ, không có CRUD. */
@Entity
@Immutable
@Table(name = "grammar_elements")
public class GrammarElement {

    @Id
    private Long id;

    private String code;
    private String name;

    @Column(name = "color_hex")
    private String colorHex;

    @Column(name = "sort_order")
    private int sortOrder;

    protected GrammarElement() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getColorHex() { return colorHex; }
    public int getSortOrder() { return sortOrder; }
}
