package com.pps.smartenglish.exercise.content;

import com.pps.smartenglish.media.dto.MediaDtos.MediaFile;
import com.pps.smartenglish.media.service.MediaService;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * D-133: HTML từ TipTap luôn được server làm sạch trước khi lưu; ảnh chỉ mang data-asset-id, URL ký sinh lúc đọc.
 * Danh sách thẻ là hợp đồng với trình soạn trên web: thêm thẻ ở đây thì phải thêm ở extension TipTap.
 */
@Component
public class RichText {

    /** html đã sạch; text suy ra để lưu ở cột chữ thường; assetIds theo thứ tự xuất hiện, không trùng. */
    public record Sanitized(String html, String text, List<Long> assetIds) {}

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "sub", "sup", "ul", "ol", "li", "blockquote", "h3", "h4",
                    "table", "thead", "tbody", "tr", "th", "td", "img")
            .addAttributes("th", "colspan", "rowspan").addAttributes("td", "colspan", "rowspan")
            .addAttributes("img", "data-asset-id");

    private static final Document.OutputSettings COMPACT = new Document.OutputSettings().prettyPrint(false);

    private final MediaService media;

    RichText(MediaService media) {
        this.media = media;
    }

    public Sanitized sanitize(String html) {
        Document doc = Jsoup.parseBodyFragment(Jsoup.clean(html, "", SAFELIST, COMPACT));
        Set<Long> ids = new LinkedHashSet<>();
        for (Element img : doc.body().select("img")) {
            Long id = assetId(img);
            if (id == null) {
                img.remove();
            } else {
                ids.add(id);
            }
        }
        doc.outputSettings(COMPACT);
        return new Sanitized(doc.body().html(), doc.body().text().strip(), List.copyOf(ids));
    }

    /**
     * Sanitize rồi bắt buộc có chữ: cột chữ thường không được trống và không vượt maxChars (giới hạn của cột đó).
     * field là đường dẫn lỗi trả về cho người soạn.
     */
    public Sanitized sanitizeRequiringText(String field, String html, int maxChars) {
        Sanitized s = sanitize(html);
        if (s.text().isEmpty()) {
            throw ApiException.validation(field, "Không được để trống");
        }
        if (s.text().length() > maxChars) {
            throw ApiException.validation(field, "Tối đa " + maxChars + " ký tự chữ");
        }
        return s;
    }

    /** Mọi ảnh trong HTML phải là ảnh READY (400 media_not_ready). Một lần gọi cho cả request. */
    public void requireReady(Collection<Long> assetIds) {
        if (!assetIds.isEmpty()) {
            media.requireImages(assetIds);
        }
    }

    /**
     * Dựng HTML để trả ra: thêm src ký sẵn, width, height cho từng ảnh; ảnh chưa READY thì bỏ. Một lần gọi MediaService cho
     * toàn bộ chuỗi đưa vào. Khóa kết quả là chính chuỗi đã lưu; null và chuỗi trống bị bỏ qua.
     */
    public Map<String, String> render(Collection<String> stored) {
        Map<String, Document> docs = new HashMap<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (String html : stored) {
            if (html != null && !html.isBlank() && !docs.containsKey(html)) {
                Document d = Jsoup.parseBodyFragment(html);
                d.outputSettings(COMPACT);
                d.body().select("img").forEach(i -> {
                    Long id = assetId(i);
                    if (id != null) {
                        ids.add(id);
                    }
                });
                docs.put(html, d);
            }
        }
        Map<Long, MediaFile> files = ids.isEmpty() ? Map.of() : media.files(ids);
        Map<String, String> out = new HashMap<>();
        docs.forEach((html, d) -> {
            for (Element img : d.body().select("img")) {
                Long id = assetId(img);
                MediaFile f = id == null ? null : files.get(id);
                if (f == null) {
                    img.remove();
                } else {
                    img.attr("src", f.url()).attr("width", String.valueOf(f.width())).attr("height", String.valueOf(f.height()));
                }
            }
            out.put(html, d.body().html());
        });
        return out;
    }

    private static Long assetId(Element img) {
        String raw = img.attr("data-asset-id");
        if (!raw.matches("[0-9]{1,18}")) {
            return null;
        }
        long id = Long.parseLong(raw);
        return id > 0 ? id : null;
    }
}
