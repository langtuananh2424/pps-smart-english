package com.pps.smartenglish.exercise.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.content.PromptHash;
import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một bảng cho mọi dạng câu hỏi. payload được trả nguyên văn cho học viên (BankService) nên CMS chỉ cho phép đúng các trường
 * của từng dạng; answerKey không được đưa vào DTO nào của học viên trước khi nộp.
 */
@Entity
@Table(name = "questions")
public class Question extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không đổi được sau khi tạo: câu hỏi không chuyển bộ. */
    @Column(name = "bank_id", updatable = false)
    private Long bankId;

    private String type;
    private String prompt;

    /** HTML đã làm sạch (D-133); null = chỉ có prompt chữ thường. prompt luôn được server suy ra từ đây. */
    @Column(name = "prompt_html")
    private String promptHtml;

    /** Nhóm dùng chung bài đọc (D-133); null = câu lẻ. */
    @Column(name = "group_id")
    private Long groupId;

    /** Câu hướng dẫn ngắn (D-70), tùy chọn; không có thì web hiện tên dạng câu. */
    private String instruction;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_key")
    private JsonNode answerKey;

    private String explanation;

    @Column(name = "sort_order")
    private int sortOrder;

    /** Tăng khi payload, answerKey hoặc lựa chọn thật sự đổi (trừ WRITING); chấm lại bài cũ bám theo số này. */
    @Column(name = "answer_version")
    private int answerVersion = 1;

    /** Script riêng của câu (FR-EX-10); null = dùng script của nhóm. Không bao giờ vào DTO của học viên trừ ScriptService. */
    @Column(name = "transcript_excerpt")
    private String transcriptExcerpt;

    /** LISTENING/READING/GRAMMAR; chỉ có ở câu của đề kiểm tra đầu vào. */
    private String skill;

    /** Lô nhập hàng loạt tạo ra câu này; null = soạn tay. */
    @Column(name = "import_batch_id", updatable = false)
    private Long importBatchId;

    /** Có giá trị = câu đã bị ẩn khi hoàn tác lô (còn lượt làm); không hiện cho lượt làm mới, lịch sử học viên vẫn giữ. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Dấu vân tay chống trùng (PromptHash); câu cũ null cho tới khi được so trùng lần đầu. */
    @Column(name = "prompt_hash")
    private String promptHash;

    protected Question() {}

    public Question(Long bankId, QuestionType type, String prompt, JsonNode payload, JsonNode answerKey, String explanation,
                    int sortOrder) {
        this.bankId = bankId;
        this.type = type.name();
        this.prompt = prompt;
        this.payload = payload;
        this.answerKey = answerKey;
        this.explanation = explanation;
        this.sortOrder = sortOrder;
        refreshHash();
    }

    public Long getId() { return id; }
    public Long getBankId() { return bankId; }
    public QuestionType getType() { return QuestionType.valueOf(type); }
    public String getPrompt() { return prompt; }
    public String getPromptHtml() { return promptHtml; }
    public Long getGroupId() { return groupId; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public JsonNode getPayload() { return payload; }
    public JsonNode getAnswerKey() { return answerKey; }
    public String getExplanation() { return explanation; }
    public int getSortOrder() { return sortOrder; }
    public int getAnswerVersion() { return answerVersion; }
    public Long getImportBatchId() { return importBatchId; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getPromptHash() { return promptHash; }
    public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
    public void archive(Instant at) { this.archivedAt = at; }
    public String getSkill() { return skill; }
    public String getTranscriptExcerpt() { return transcriptExcerpt; }
    public void setTranscriptExcerpt(String transcriptExcerpt) { this.transcriptExcerpt = transcriptExcerpt; }
    public void setSkill(String skill) { this.skill = skill; }

    public void setType(QuestionType type) { this.type = type.name(); }
    public void setPrompt(String prompt) { this.prompt = prompt; refreshHash(); }
    public void setPromptHtml(String promptHtml) { this.promptHtml = promptHtml; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public void setPayload(JsonNode payload) { this.payload = payload; refreshHash(); }
    public void setAnswerKey(JsonNode answerKey) { this.answerKey = answerKey; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void bumpAnswerVersion() { this.answerVersion++; }

    public void refreshHash() {
        this.promptHash = PromptHash.of(prompt, payload);
    }
}
