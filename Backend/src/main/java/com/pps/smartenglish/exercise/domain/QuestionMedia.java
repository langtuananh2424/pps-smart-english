package com.pps.smartenglish.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một ảnh của câu hỏi với cỡ hiển thị riêng (D-134). */
@Entity
@Table(name = "question_media")
public class QuestionMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private Long assetId;

    @Column(nullable = false)
    private String role;

    @Column(name = "display_size", nullable = false)
    private String displaySize;

    @Column(name = "alt_text", nullable = false)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected QuestionMedia() {}

    public QuestionMedia(Long questionId, Long assetId, String role, String displaySize, String altText, int sortOrder) {
        this.questionId = questionId;
        this.assetId = assetId;
        this.role = role;
        this.displaySize = displaySize;
        this.altText = altText;
        this.sortOrder = sortOrder;
    }

    public Long getQuestionId() { return questionId; }
    public Long getAssetId() { return assetId; }
    public String getRole() { return role; }
    public String getDisplaySize() { return displaySize; }
    public String getAltText() { return altText; }
    public int getSortOrder() { return sortOrder; }
}
