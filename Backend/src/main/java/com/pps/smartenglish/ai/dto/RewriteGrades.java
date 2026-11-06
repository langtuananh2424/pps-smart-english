package com.pps.smartenglish.ai.dto;

import java.util.List;

/** Output cấu trúc của rewrite-grading.md: đủ mọi id, đúng thứ tự. */
public record RewriteGrades(List<Item> items) {

    public record Item(long id, boolean correct, String feedback, String correctedAnswer) {}
}
