package com.pps.smartenglish.exercise.domain;

/** Giá trị questions.type (FR-EX-01, FR-EX-02). */
public enum QuestionType {
    CONNECTIVE_MCQ,
    CONNECTIVE_MATCH,
    CONNECTIVE_CLOZE,
    CONNECTIVE_REWRITE,
    GRAMMAR_IDENTIFY,
    GRAMMAR_SPOT_FIX,
    GRAMMAR_BUILD,
    GRAMMAR_REWRITE,
    WRITING,
    SPEAKING,
    // Kiểu chấm mới (FR-EX-04, FR-EX-05): dạng hiển thị khác nhau dùng chung các kiểu này.
    CHOICE_MULTI,
    TRUE_FALSE,
    FILL_TEXT,
    WORD_BANK,
    MATCH_OPTIONS,
    // D-154: Luyện tập PPS (Figma 10.11, 10.34, 10.22).
    LETTER_ORDER,
    DICTATION,
    LISTEN_FORM,
    // D-154 mục 4 (Figma 10.23): nghe audio mẫu của nhóm rồi nộp tệp audio, chấm như SPEAKING.
    LISTEN_RECORD,
    // D-157: mỗi dạng IELTS Reading/Listening một kiểu; phần dùng chung nằm ở question_groups.layout.
    IELTS_TFNG,
    IELTS_YNNG,
    IELTS_MATCH_INFO,
    IELTS_MATCH_HEADINGS,
    IELTS_MATCH_FEATURES,
    IELTS_MATCH_ENDINGS,
    IELTS_MATCHING,
    IELTS_SENTENCE,
    IELTS_SUMMARY,
    IELTS_SUMMARY_BANK,
    IELTS_NOTE,
    IELTS_TABLE,
    IELTS_FLOWCHART,
    IELTS_FORM,
    IELTS_DIAGRAM,
    IELTS_MAP,
    IELTS_SHORT,
    // D-159 (Figma 10.32, 11.32): quiz sau video từ nối, nhóm có video.
    VIDEO_CONNECTIVE,
    // D-154 mục 5, D-158 (Figma 10.28–10.31): Video phản xạ, mỗi câu là một mốc 'at' trong video của nhóm, chấm như Nói.
    VIDEO_SHORT,
    VIDEO_CUE,
    VIDEO_DISCUSS,
    VIDEO_PICTURE;

    /** Chấm lại bài cũ chỉ cho dạng có đáp án: WRITING chấm theo phiên riêng, SPEAKING, LISTEN_RECORD và Video phản xạ chưa có. */
    public boolean regradable() {
        return this != WRITING && this != SPEAKING && this != LISTEN_RECORD && !isVideoReflex();
    }

    /** Video phản xạ: câu nói theo mốc video của nhóm (VIDEO_SHORT có thêm bước Viết trước). */
    public boolean isVideoReflex() {
        return this == VIDEO_SHORT || this == VIDEO_CUE || this == VIDEO_DISCUSS || this == VIDEO_PICTURE;
    }

    /** Câu chấm bằng bản ghi Nói gắn lượt (D-145): Nói, nghe rồi nộp bản ghi, Video phản xạ. */
    public boolean isSpeakingLike() {
        return this == SPEAKING || this == LISTEN_RECORD || isVideoReflex();
    }

    /** Bố cục nhóm mà dạng này cần; null = không cần layout. */
    public LayoutKind layoutKind() {
        return switch (this) {
            case IELTS_MATCH_INFO, IELTS_MATCH_HEADINGS, IELTS_MATCH_FEATURES, IELTS_MATCH_ENDINGS, IELTS_MATCHING -> LayoutKind.OPTIONS;
            case IELTS_SUMMARY -> LayoutKind.TEXT;
            case IELTS_SUMMARY_BANK -> LayoutKind.TEXT_BANK;
            case IELTS_NOTE -> LayoutKind.NOTE;
            case IELTS_TABLE -> LayoutKind.TABLE;
            case IELTS_FLOWCHART -> LayoutKind.FLOW;
            case IELTS_FORM, LISTEN_FORM -> LayoutKind.FORM;
            case IELTS_DIAGRAM -> LayoutKind.DIAGRAM;
            case IELTS_MAP -> LayoutKind.MAP;
            default -> null;
        };
    }

    /** Dạng nghe chép/điền phiếu/nộp bản ghi: nhóm bắt buộc có audio. */
    public boolean needsAudio() {
        return this == DICTATION || this == LISTEN_FORM || this == LISTEN_RECORD;
    }

    /** Quiz sau video: nhóm bắt buộc có video (D-156, D-159). */
    public boolean needsVideo() {
        return this == VIDEO_CONNECTIVE;
    }

    /** Nhóm bắt buộc có video: quiz sau video hoặc Video phản xạ. */
    public boolean needsGroupVideo() {
        return needsVideo() || isVideoReflex();
    }

    /** Phụ thuộc nhóm (bài đọc, audio, layout) nên không đưa vào Luyện thêm vì lượt đó không mang nhóm. */
    public boolean groupBound() {
        return name().startsWith("IELTS_") || needsAudio() || needsGroupVideo();
    }

    /** Đáp án nằm ở question_choices (is_correct) thay vì answer_key. */
    public boolean usesChoices() {
        return this == CONNECTIVE_MCQ || this == CHOICE_MULTI || this == VIDEO_CONNECTIVE;
    }
}
