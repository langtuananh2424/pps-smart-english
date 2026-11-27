package com.pps.smartenglish.exercise.domain;

/** D-154, D-157: kiểu bố cục dùng chung của nhóm câu (question_groups.layout.kind). slotBased: có chỗ trống {slot} để câu điền. */
public enum LayoutKind {
    OPTIONS(false), TEXT(true), TEXT_BANK(true), NOTE(true), TABLE(true), FLOW(true), FORM(true), DIAGRAM(true), MAP(false);

    private final boolean slotBased;

    LayoutKind(boolean slotBased) {
        this.slotBased = slotBased;
    }

    public boolean slotBased() {
        return slotBased;
    }
}
