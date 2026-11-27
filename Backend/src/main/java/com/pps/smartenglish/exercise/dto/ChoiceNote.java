package com.pps.smartenglish.exercise.dto;

/** FR-EX-03: giải thích riêng của một phương án; chỉ đi cùng nơi đã lộ đáp án (kết quả đã nộp, câu khóa sai). */
public record ChoiceNote(String label, boolean correct, String explanation) {}
