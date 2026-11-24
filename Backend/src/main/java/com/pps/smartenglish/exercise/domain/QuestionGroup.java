package com.pps.smartenglish.exercise.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** D-133: bài đọc hoặc phần nghe dùng chung cho nhiều câu của một bộ. passageHtml đã được làm sạch (RichText). */
@Entity
@Table(name = "question_groups")
public class QuestionGroup extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", updatable = false)
    private Long bankId;

    @Column(name = "sort_order")
    private int sortOrder;

    private String title;
    private String instruction;

    @Column(name = "passage_html")
    private String passageHtml;

    /** Audio của part (media_assets kind AUDIO); học viên chỉ nghe qua AudioPlayService. */
    @Column(name = "audio_asset_id")
    private Long audioAssetId;

    /** Script của part (đã làm sạch), dùng cho Nghe ép; không trả cho học viên trừ ScriptService. */
    @Column(name = "transcript_html")
    private String transcriptHtml;

    protected QuestionGroup() {}

    public QuestionGroup(Long bankId, int sortOrder, String title, String instruction, String passageHtml) {
        this.bankId = bankId;
        this.sortOrder = sortOrder;
        this.title = title;
        this.instruction = instruction;
        this.passageHtml = passageHtml;
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getInstruction() { return instruction; }
    public String getPassageHtml() { return passageHtml; }

    public Long getAudioAssetId() { return audioAssetId; }
    public String getTranscriptHtml() { return transcriptHtml; }
    public void setAudioAssetId(Long audioAssetId) { this.audioAssetId = audioAssetId; }
    public void setTranscriptHtml(String transcriptHtml) { this.transcriptHtml = transcriptHtml; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setTitle(String title) { this.title = title; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public void setPassageHtml(String passageHtml) { this.passageHtml = passageHtml; }
}
