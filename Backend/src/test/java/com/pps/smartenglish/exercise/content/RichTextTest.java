package com.pps.smartenglish.exercise.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pps.smartenglish.media.dto.MediaDtos.MediaFile;
import com.pps.smartenglish.media.service.MediaService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-133: làm sạch HTML của người soạn và dựng lại URL ảnh khi đọc. */
class RichTextTest {

    private final MediaService media = mock(MediaService.class);
    private final RichText rich = new RichText(media);

    @Test
    void scriptsHandlersStylesAndFramesAreRemoved() {
        var out = rich.sanitize("<p onclick=\"x()\" style=\"color:red\">Hi<script>alert(1)</script><iframe src=\"//e\"></iframe></p>"
                + "<b onmouseover=\"x()\">b</b>");
        assertEquals("<p>Hi</p><b>b</b>", out.html());
        assertEquals("Hi b", out.text());
    }

    @Test
    void linksAreDroppedButTheirTextStays() {
        var out = rich.sanitize("<p><a href=\"javascript:alert(1)\">click</a></p>");
        assertEquals("<p>click</p>", out.html());
    }

    @Test
    void imageKeepsOnlyAssetIdAndBadIdsAreRemoved() {
        var out = rich.sanitize("<p><img src=\"http://evil/x.png\" data-asset-id=\"7\" onerror=\"x()\" style=\"a:b\">"
                + "<img data-asset-id=\"abc\"><img data-asset-id=\"-3\"><img data-asset-id=\"0\"><img src=\"http://evil/y.png\">"
                + "<img data-asset-id=\"7\"><img data-asset-id=\"9\"></p>");
        assertEquals(List.of(7L, 9L), out.assetIds());
        assertEquals("<p><img data-asset-id=\"7\"><img data-asset-id=\"7\"><img data-asset-id=\"9\"></p>", out.html());
        assertFalse(out.html().contains("evil"));
    }

    @Test
    void textIsDerivedWithBlocksSeparated() {
        assertEquals("Hello world Two", rich.sanitize("<p>Hello <b>world</b></p><p>Two</p>").text());
    }

    @Test
    void tablesAreKeptWithSpanOnly() {
        var out = rich.sanitize("<table class=\"x\"><thead><tr><th colspan=\"2\" style=\"a:b\">H</th></tr></thead>"
                + "<tbody><tr><td rowspan=\"2\">1</td><td>2</td></tr></tbody></table>");
        assertTrue(out.html().contains("<th colspan=\"2\">H</th>"));
        assertTrue(out.html().contains("<td rowspan=\"2\">1</td>"));
        assertFalse(out.html().contains("class"));
    }

    @Test
    void sanitizingTwiceChangesNothing() {
        var once = rich.sanitize("<p>a &amp; b<img data-asset-id=\"5\"></p><ul><li>x</li></ul>");
        assertEquals(once.html(), rich.sanitize(once.html()).html());
    }

    @Test
    void renderAddsSignedSrcAndSizeAndDropsNotReadyImagesWithOneStorageCall() {
        when(media.files(anyCollection())).thenReturn(Map.of(5L, new MediaFile(5L, "http://s/5.webp", 40, 30)));
        String a = "<p>A<img data-asset-id=\"5\"></p>";
        String b = "<p>B<img data-asset-id=\"6\"></p>";
        var out = rich.render(Arrays.asList(a, b, null, a));
        assertEquals("<p>A<img data-asset-id=\"5\" src=\"http://s/5.webp\" width=\"40\" height=\"30\"></p>", out.get(a));
        assertEquals("<p>B</p>", out.get(b));
        verify(media, times(1)).files(anyCollection());
    }
}
