package com.pps.smartenglish.exercise.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.pps.smartenglish.media.service.MediaService;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tester: các mẫu mXSS/ngữ cảnh lạ mà TipTap hoặc người soạn có thể dán vào; sau khi sanitize rồi render không còn đường chạy script. */
class RichTextMxssTesterTest {

    private final RichText rich = new RichText(mock(MediaService.class));

    @ParameterizedTest
    @ValueSource(strings = {
        "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></noscript>",
        "<svg><style><img src=x onerror=alert(1)></style></svg>",
        "<math><mtext><table><mglyph><style><img src=x onerror=alert(1)>",
        "<p>a</p><style>p{background:url(javascript:alert(1))}</style>",
        "<table><tr><td background=\"javascript:alert(1)\" colspan=\"2\">x</td></tr></table>",
        "<img data-asset-id=\"1\" srcset=\"javascript:alert(1)\" onload=\"alert(1)\">",
        "<p>x<!--><img src=x onerror=alert(1)>--></p>",
        "<form><button formaction=javascript:alert(1)>x</button></form>",
        "<p><object data=\"javascript:alert(1)\"></object><embed src=\"javascript:alert(1)\"></p>",
        "<h3 id=\"x\" class=\"y\" data-foo=\"z\">t</h3>",
    })
    void dangerousMarkupIsNeutralised(String html) {
        var clean = rich.sanitize(html).html();
        var rendered = rich.render(List.of(clean)).getOrDefault(clean, clean);
        var allowed = java.util.Set.of("p", "br", "strong", "b", "em", "i", "u", "s", "sub", "sup", "ul", "ol", "li", "blockquote", "h3",
                "h4", "table", "thead", "tbody", "tr", "th", "td", "img", "body", "html", "head", "#root");
        // phân tích lại như trình duyệt: chỉ còn thẻ trong danh sách cho phép, không còn thuộc tính lạ
        for (var el : org.jsoup.Jsoup.parseBodyFragment(rendered).getAllElements()) {
            assertFalse(!allowed.contains(el.normalName()), "thẻ lạ " + el.normalName() + " trong: " + rendered);
            for (var a : el.attributes()) {
                assertFalse(!List.of("colspan", "rowspan", "data-asset-id", "src", "width", "height").contains(a.getKey().toLowerCase(Locale.ROOT)),
                        "thuộc tính lạ " + a.getKey() + " trong: " + rendered);
                assertFalse(a.getValue().toLowerCase(Locale.ROOT).contains("javascript:"), rendered);
            }
        }
    }
}
