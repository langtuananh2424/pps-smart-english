package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.exercise.config.LayoutProperties;
import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.LayoutKind;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * D-154, D-157: kiểm và làm sạch question_groups.layout theo kind (whitelist trường, giới hạn kích thước từ LayoutProperties).
 * Trả bản mới chỉ gồm trường hợp lệ: chữ thuần qua PlainText, HTML qua RichText như passageHtml, ảnh phải READY.
 * Việc kiểm chéo với câu hỏi (slot, option) nằm ở GroupLayoutCheck.
 */
@Component
public class LayoutValidator {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final int LABEL_MAX = 500;
    private static final int HTML_MAX = 50_000;

    private final RichText rich;
    private final LayoutProperties lim;

    LayoutValidator(RichText rich, LayoutProperties lim) {
        this.rich = rich;
        this.lim = lim;
    }

    public ObjectNode validate(JsonNode in) {
        if (in == null || !in.isObject()) {
            throw JsonRules.bad("layout", "Phải là một object");
        }
        if (in.toString().length() > lim.maxChars()) {
            throw JsonRules.bad("layout", "Tối đa " + lim.maxChars() + " ký tự");
        }
        LayoutKind kind = kindOf(in);
        JsonRules.object(in, "layout", allowed(kind));
        ObjectNode out = NODES.objectNode().put("kind", kind.name());
        Set<Long> assets = new LinkedHashSet<>();
        title(in, out);
        switch (kind) {
            case OPTIONS -> {
                out.set("options", idTexts(in, "options", 2, lim.maxOptions()));
                reuse(in, out);
            }
            case TEXT -> out.put("html", html(in, "html", "layout", assets));
            case TEXT_BANK -> {
                out.put("html", html(in, "html", "layout", assets));
                out.set("words", idTexts(in, "words", 2, lim.maxOptions()));
                reuse(in, out);
            }
            case NOTE -> note(in, out, assets);
            case TABLE -> table(in, out);
            case FLOW -> out.set("steps", texts(in.get("steps"), "layout.steps", 1, lim.maxRows(), false));
            case FORM -> form(in, out);
            case DIAGRAM -> {
                out.put("assetId", assetId(in, assets));
                out.set("points", points(in, "points", "slot", 1, lim.maxPoints()));
            }
            case MAP -> {
                out.put("assetId", assetId(in, assets));
                out.set("options", points(in, "options", "id", 2, lim.maxOptions()));
            }
        }
        rich.requireReady(assets);
        LayoutIndex idx = LayoutIndex.of(out);
        if (kind.slotBased() && idx.slots().isEmpty()) {
            throw JsonRules.bad("layout", "Cần ít nhất một chỗ trống {slot}");
        }
        if (new HashSet<>(idx.slots()).size() != idx.slots().size()) {
            throw JsonRules.bad("layout", "Có chỗ trống bị lặp");
        }
        return out;
    }

    private static LayoutKind kindOf(JsonNode in) {
        try {
            return LayoutKind.valueOf(JsonRules.text(in, "kind", "layout"));
        } catch (IllegalArgumentException e) {
            throw JsonRules.bad("layout.kind", "Kiểu layout không hợp lệ");
        }
    }

    /** "image" là trường server thêm khi trả layout (URL ảnh ký sẵn); bị bỏ khi lưu để CMS gửi lại nguyên bản đã đọc. */
    private static Set<String> allowed(LayoutKind kind) {
        return switch (kind) {
            case OPTIONS -> Set.of("kind", "title", "options", "reuse");
            case TEXT -> Set.of("kind", "title", "html");
            case TEXT_BANK -> Set.of("kind", "title", "html", "words", "reuse");
            case NOTE -> Set.of("kind", "title", "lines");
            case TABLE -> Set.of("kind", "title", "header", "rows");
            case FLOW -> Set.of("kind", "title", "steps");
            case FORM -> Set.of("kind", "title", "rows");
            case DIAGRAM -> Set.of("kind", "title", "assetId", "points", "image");
            case MAP -> Set.of("kind", "title", "assetId", "options", "image");
        };
    }

    private static void title(JsonNode in, ObjectNode out) {
        if (in.has("title")) {
            out.put("title", PlainText.required("layout.title", JsonRules.text(in, "title", "layout", 200), 200));
        }
    }

    private static void reuse(JsonNode in, ObjectNode out) {
        if (in.has("reuse") && !in.get("reuse").isBoolean()) {
            throw JsonRules.bad("layout.reuse", "Phải là true hoặc false");
        }
        out.put("reuse", in.path("reuse").asBoolean(false));
    }

    private String html(JsonNode obj, String name, String field, Set<Long> assets) {
        var s = rich.sanitizeRequiringText(field + "." + name, JsonRules.text(obj, name, field, HTML_MAX), HTML_MAX);
        assets.addAll(s.assetIds());
        return s.html();
    }

    /** Mảng chuỗi (bước, ô bảng, tiêu đề cột) có số phần tử trong [min, max]; allowEmpty cho ô bảng. */
    private static ArrayNode texts(JsonNode arr, String field, int min, int max, boolean allowEmpty) {
        if (arr == null || !arr.isArray() || arr.size() < min || arr.size() > max) {
            throw JsonRules.bad(field, "Phải là mảng từ " + min + " đến " + max + " phần tử");
        }
        ArrayNode out = NODES.arrayNode();
        for (int i = 0; i < arr.size(); i++) {
            String at = field + "[" + i + "]";
            if (!arr.get(i).isTextual()) {
                throw JsonRules.bad(at, "Phải là chuỗi");
            }
            out.add(allowEmpty ? PlainText.clean(at, arr.get(i).asText(), LABEL_MAX) : PlainText.required(at, arr.get(i).asText(), LABEL_MAX));
        }
        return out;
    }

    /** Mảng {id, text}: id duy nhất không phân biệt hoa thường (grader so không phân biệt hoa thường). */
    private static ArrayNode idTexts(JsonNode in, String name, int min, int max) {
        JsonNode arr = JsonRules.array(in, name, "layout", min);
        if (arr.size() > max) {
            throw JsonRules.bad("layout." + name, "Tối đa " + max + " phần tử");
        }
        Set<String> seen = new HashSet<>();
        ArrayNode out = NODES.arrayNode();
        for (int i = 0; i < arr.size(); i++) {
            String at = "layout." + name + "[" + i + "]";
            JsonNode o = JsonRules.object(arr.get(i), at, Set.of("id", "text"));
            String id = id(o, "id", at);
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                throw JsonRules.bad(at + ".id", "Bị lặp");
            }
            out.addObject().put("id", id).put("text", PlainText.required(at + ".text", JsonRules.text(o, "text", at, LABEL_MAX), LABEL_MAX));
        }
        return out;
    }

    /** Mảng {keyField, x, y}: điểm trên ảnh, x và y là % 0..100. keyField là slot (sơ đồ) hoặc id (bản đồ, duy nhất). */
    private static ArrayNode points(JsonNode in, String name, String keyField, int min, int max) {
        JsonNode arr = JsonRules.array(in, name, "layout", min);
        if (arr.size() > max) {
            throw JsonRules.bad("layout." + name, "Tối đa " + max + " phần tử");
        }
        Set<String> seen = new HashSet<>();
        ArrayNode out = NODES.arrayNode();
        for (int i = 0; i < arr.size(); i++) {
            String at = "layout." + name + "[" + i + "]";
            JsonNode o = JsonRules.object(arr.get(i), at, Set.of(keyField, "x", "y"));
            String key = id(o, keyField, at);
            if (!seen.add(key.toLowerCase(Locale.ROOT))) {
                throw JsonRules.bad(at + "." + keyField, "Bị lặp");
            }
            out.addObject().put(keyField, key).put("x", percent(o, "x", at)).put("y", percent(o, "y", at));
        }
        return out;
    }

    private static double percent(JsonNode o, String name, String at) {
        JsonNode v = o.get(name);
        if (v == null || !v.isNumber() || v.asDouble() < 0 || v.asDouble() > 100) {
            throw JsonRules.bad(at + "." + name, "Bắt buộc, là số từ 0 đến 100");
        }
        return v.asDouble();
    }

    private static String id(JsonNode o, String name, String at) {
        String v = JsonRules.text(o, name, at, 40);
        if (!LayoutIndex.ID.matcher(v).matches()) {
            throw JsonRules.bad(at + "." + name, "Chỉ gồm chữ, số, gạch ngang, gạch dưới");
        }
        return v;
    }

    private static long assetId(JsonNode in, Set<Long> assets) {
        JsonNode v = in.get("assetId");
        if (v == null || !v.isIntegralNumber() || v.asLong() <= 0) {
            throw JsonRules.bad("layout.assetId", "Bắt buộc, là id ảnh");
        }
        assets.add(v.asLong());
        return v.asLong();
    }

    private void note(JsonNode in, ObjectNode out, Set<Long> assets) {
        JsonNode lines = JsonRules.array(in, "lines", "layout", 1);
        if (lines.size() > lim.maxRows()) {
            throw JsonRules.bad("layout.lines", "Tối đa " + lim.maxRows() + " dòng");
        }
        ArrayNode arr = out.putArray("lines");
        for (int i = 0; i < lines.size(); i++) {
            String at = "layout.lines[" + i + "]";
            arr.addObject().put("html", html(JsonRules.object(lines.get(i), at, Set.of("html")), "html", at, assets));
        }
    }

    private void table(JsonNode in, ObjectNode out) {
        ArrayNode header = texts(in.get("header"), "layout.header", 1, lim.maxCols(), false);
        out.set("header", header);
        JsonNode rows = JsonRules.array(in, "rows", "layout", 1);
        if (rows.size() > lim.maxRows()) {
            throw JsonRules.bad("layout.rows", "Tối đa " + lim.maxRows() + " hàng");
        }
        ArrayNode outRows = out.putArray("rows");
        for (int i = 0; i < rows.size(); i++) {
            outRows.add(texts(rows.get(i), "layout.rows[" + i + "]", header.size(), header.size(), true));
        }
    }

    private void form(JsonNode in, ObjectNode out) {
        JsonNode rows = JsonRules.array(in, "rows", "layout", 1);
        if (rows.size() > lim.maxRows()) {
            throw JsonRules.bad("layout.rows", "Tối đa " + lim.maxRows() + " dòng");
        }
        ArrayNode outRows = out.putArray("rows");
        for (int i = 0; i < rows.size(); i++) {
            String at = "layout.rows[" + i + "]";
            JsonNode r = JsonRules.object(rows.get(i), at, Set.of("label", "slot", "value"));
            if (r.has("slot") && r.has("value")) {
                throw JsonRules.bad(at, "Dòng có chỗ trống thì không có value");
            }
            ObjectNode row = outRows.addObject().put("label", PlainText.required(at + ".label", JsonRules.text(r, "label", at, LABEL_MAX), LABEL_MAX));
            if (r.has("slot")) {
                row.put("slot", id(r, "slot", at));
            }
            if (r.has("value")) {
                row.put("value", PlainText.required(at + ".value", JsonRules.text(r, "value", at, LABEL_MAX), LABEL_MAX));
            }
        }
    }
}
