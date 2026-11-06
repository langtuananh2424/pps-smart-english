package com.pps.smartenglish.ai.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** System prompt nạp nguyên văn từ resources/ai/*.md; sửa prompt = sửa file, không sửa code. */
@Component
public class Prompts {

    private final String writingRubric = load("writing-rubric-ielts.md");
    private final String scaffoldFollowup = load("scaffold-followup.md");
    private final String scaffoldDraft = load("scaffold-draft.md");
    private final String rewriteGrading = load("rewrite-grading.md");

    public String writingRubric() { return writingRubric; }
    public String scaffoldFollowup() { return scaffoldFollowup; }
    public String scaffoldDraft() { return scaffoldDraft; }
    public String rewriteGrading() { return rewriteGrading; }

    private static String load(String name) {
        try {
            return new ClassPathResource("ai/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được prompt " + name, e);
        }
    }
}
