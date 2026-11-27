package com.pps.smartenglish.exercise.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.domain.QuestionGroup;
import com.pps.smartenglish.media.service.MediaService;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * D-154, D-157: dựng layout để trả ra: HTML có src ảnh ký sẵn (như bài đọc), sơ đồ/bản đồ có thêm image {url,width,height}.
 * Layout không chứa đáp án nên dùng chung cho học viên và CMS. Mỗi lần gọi một lượt RichText.render và một lượt MediaService.files cho mọi nhóm.
 */
@Component
public class LayoutRenderer {

    private final RichText rich;
    private final MediaService media;

    LayoutRenderer(RichText rich, MediaService media) {
        this.rich = rich;
        this.media = media;
    }

    /** Khóa là id nhóm; nhóm không có layout vắng mặt trong kết quả. */
    public Map<Long, JsonNode> render(Collection<QuestionGroup> groups) {
        Map<Long, ObjectNode> copies = new HashMap<>();
        groups.stream().filter(g -> g.getLayout() != null && g.getLayout().isObject())
                .forEach(g -> copies.put(g.getId(), ((ObjectNode) g.getLayout()).deepCopy()));
        List<ObjectNode> holders = copies.values().stream().flatMap(LayoutRenderer::htmlHolders).toList();
        Map<String, String> html = rich.render(holders.stream().map(h -> h.path("html").asText()).toList());
        holders.forEach(h -> h.put("html", html.getOrDefault(h.path("html").asText(), "")));

        Set<Long> assets = new LinkedHashSet<>();
        copies.values().stream().filter(l -> l.has("assetId")).forEach(l -> assets.add(l.get("assetId").asLong()));
        var files = assets.isEmpty() ? Map.<Long, com.pps.smartenglish.media.dto.MediaDtos.MediaFile>of() : media.files(assets);
        copies.values().stream().filter(l -> l.has("assetId")).forEach(l -> {
            var f = files.get(l.get("assetId").asLong());
            if (f != null) {
                l.putObject("image").put("url", f.url()).put("width", f.width()).put("height", f.height());
            }
        });
        return Map.copyOf(copies);
    }

    /** Các object có trường html: gốc layout (TEXT, TEXT_BANK) hoặc từng dòng ghi chú (NOTE). */
    private static Stream<ObjectNode> htmlHolders(ObjectNode layout) {
        return switch (layout.path("kind").asText()) {
            case "TEXT", "TEXT_BANK" -> Stream.of(layout);
            case "NOTE" -> Stream.of(layout.path("lines")).flatMap(a -> a.isArray() ? java.util.stream.StreamSupport.stream(a.spliterator(), false) : Stream.empty())
                    .filter(JsonNode::isObject).map(n -> (ObjectNode) n);
            default -> Stream.empty();
        };
    }
}
