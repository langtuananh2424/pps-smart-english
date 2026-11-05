package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Dữ liệu chấm của một câu: answer_key và id các lựa chọn đúng (trắc nghiệm lấy từ question_choices). */
public record GradingInput(JsonNode answerKey, Set<Long> correctChoiceIds) {}
