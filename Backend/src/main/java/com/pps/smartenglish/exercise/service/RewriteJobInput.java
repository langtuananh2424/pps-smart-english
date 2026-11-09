package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.Question;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Dựng input của job REWRITE_GRADE; dùng chung cho lần nộp (SubmissionService) và chấm lại bài cũ (RegradeProcessor). */
@Component
class RewriteJobInput {

    private final ObjectMapper mapper;

    RewriteJobInput(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** needsAi: câu cần AI theo questionId; given: câu trả lời theo questionId (đã có text khác rỗng). */
    ObjectNode build(long attemptId, Map<Long, Question> needsAi, Map<Long, JsonNode> given) {
        ArrayNode items = mapper.createArrayNode();
        needsAi.forEach((id, q) -> {
            var payload = q.getPayload();
            items.add(mapper.createObjectNode()
                    .put("id", id)
                    .put("instruction", q.getPrompt())
                    .put("original", payload == null ? "" : payload.path("sentence").asText(""))
                    .put("required", payload == null ? "" : payload.path("requiredWord").asText(payload.path("requiredElement").asText("")))
                    .put("answer", given.get(id).path("text").asText()));
        });
        var input = mapper.createObjectNode().put("attemptId", attemptId);
        input.set("items", items);
        return input;
    }
}
