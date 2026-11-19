package com.pps.smartenglish.catalog.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topics")
public class Topic extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: topic không chuyển level. */
    @Column(name = "level_id", updatable = false)
    private Long levelId;

    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_free_trial")
    private boolean freeTrial;

    @Enumerated(EnumType.STRING)
    private TopicSkill skill;

    private String status = ContentStatus.DRAFT;

    /** D-118: bộ câu hỏi mode EXAM của topic này làm kiểm tra chương; làm lại tối đa chapterTestRetakes lần. */
    @Column(name = "chapter_test_bank_id")
    private Long chapterTestBankId;

    @Column(name = "chapter_test_retakes", nullable = false)
    private int chapterTestRetakes;

    protected Topic() {}

    public Topic(Long levelId, String title, int sortOrder, boolean freeTrial) {
        this.levelId = levelId;
        this.title = title;
        this.sortOrder = sortOrder;
        this.freeTrial = freeTrial;
    }

    public Long getId() { return id; }
    public Long getLevelId() { return levelId; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
    public boolean isFreeTrial() { return freeTrial; }
    public TopicSkill getSkill() { return skill; }
    public String getStatus() { return status; }
    public Long getChapterTestBankId() { return chapterTestBankId; }
    public int getChapterTestRetakes() { return chapterTestRetakes; }

    public void setTitle(String title) { this.title = title; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setFreeTrial(boolean freeTrial) { this.freeTrial = freeTrial; }
    public void setSkill(TopicSkill skill) { this.skill = skill; }
    public void setStatus(String status) { this.status = status; }
    public void setChapterTestBankId(Long chapterTestBankId) { this.chapterTestBankId = chapterTestBankId; }
    public void setChapterTestRetakes(int chapterTestRetakes) { this.chapterTestRetakes = chapterTestRetakes; }
}
