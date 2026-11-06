package com.pps.smartenglish.ai.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Số liệu máy đo đưa vào prompt chấm viết: model không phải tự đếm (writing-rubric-ielts.md mục 1). */
public final class WordStats {

    /** Chuỗi trùng từ liên tiếp với đề từ độ dài này trở lên là phần chép đề. */
    static final int COPY_RUN = 5;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");

    private WordStats() {}

    public static int count(String text) {
        return words(text).size();
    }

    /** Số từ của bài thuộc ít nhất một chuỗi COPY_RUN từ liên tiếp trùng với đề (không phân biệt hoa thường). */
    public static int copiedFromPrompt(String text, String prompt) {
        List<String> t = words(text);
        List<String> p = words(prompt);
        if (t.size() < COPY_RUN || p.size() < COPY_RUN) {
            return 0;
        }
        Set<String> promptRuns = new HashSet<>();
        for (int i = 0; i + COPY_RUN <= p.size(); i++) {
            promptRuns.add(String.join(" ", p.subList(i, i + COPY_RUN)));
        }
        boolean[] copied = new boolean[t.size()];
        for (int i = 0; i + COPY_RUN <= t.size(); i++) {
            if (promptRuns.contains(String.join(" ", t.subList(i, i + COPY_RUN)))) {
                for (int k = i; k < i + COPY_RUN; k++) {
                    copied[k] = true;
                }
            }
        }
        int n = 0;
        for (boolean c : copied) {
            n += c ? 1 : 0;
        }
        return n;
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        Matcher m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
