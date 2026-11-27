package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** D-156: video làm đề, tối đa một trong hai (CHECK ở DB): file mp4 (media_assets kind VIDEO) hoặc mã YouTube. */
    @Column(name = "video_asset_id")
    private Long videoAssetId;

    @Column(name = "video_youtube_id")
    private String videoYoutubeId;

    /** D-159: thời lượng video YouTube (giây) do CMS nhập; video file dùng media_assets.duration_ms. */
    @Column(name = "video_duration_sec")
    private Integer videoDurationSec;

    /** Script của part (đã làm sạch), dùng cho Nghe ép; không trả cho học viên trừ ScriptService. */
    @Column(name = "transcript_html")
    private String transcriptHtml;

    /** D-154, D-157: bố cục dùng chung của dạng câu (bảng, ghi chú, sơ đồ, phiếu, danh sách lựa chọn...), đã làm sạch; xem LayoutValidator. */
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode layout;

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

    public JsonNode getLayout() { return layout; }
    public void setLayout(JsonNode layout) { this.layout = layout; }
    public Long getAudioAssetId() { return audioAssetId; }
    public String getTranscriptHtml() { return transcriptHtml; }
    public Long getVideoAssetId() { return videoAssetId; }
    public String getVideoYoutubeId() { return videoYoutubeId; }
    public void setVideoAssetId(Long videoAssetId) { this.videoAssetId = videoAssetId; }
    public void setVideoYoutubeId(String videoYoutubeId) { this.videoYoutubeId = videoYoutubeId; }
    public Integer getVideoDurationSec() { return videoDurationSec; }
    public void setVideoDurationSec(Integer videoDurationSec) { this.videoDurationSec = videoDurationSec; }
    public void setAudioAssetId(Long audioAssetId) { this.audioAssetId = audioAssetId; }
    public void setTranscriptHtml(String transcriptHtml) { this.transcriptHtml = transcriptHtml; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setTitle(String title) { this.title = title; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public void setPassageHtml(String passageHtml) { this.passageHtml = passageHtml; }
}
