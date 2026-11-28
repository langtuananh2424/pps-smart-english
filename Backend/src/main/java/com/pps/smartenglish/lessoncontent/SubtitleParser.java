package com.pps.smartenglish.lessoncontent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse VTT và SRT thành cue, chuẩn hóa xuất lại VTT để lưu. Lớp thuần, không phụ thuộc Spring. */
public final class SubtitleParser {

    /** Lỗi định dạng; message đưa thẳng cho biên tập viên. */
    public static final class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    public record Cue(long startMs, long endMs, String text) {}

    private static final String TS = "(?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3}";
    private static final Pattern TIMING = Pattern.compile("^\\s*(" + TS + ")\\s*-->\\s*(" + TS + ")(?:\\s.*)?$");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final int BOM = 0xFEFF;

    private SubtitleParser() {}

    /** Quá maxCues thì dừng sớm. Không có cue nào cũng là lỗi. */
    public static List<Cue> parse(String raw, int maxCues) {
        String s = !raw.isEmpty() && raw.charAt(0) == BOM ? raw.substring(1) : raw;
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<Cue> cues = new ArrayList<>();
        int i = 0;
        if (lines[0].startsWith("WEBVTT")) {
            while (i < lines.length && !lines[i].isBlank()) {
                i++;
            }
        }
        while (i < lines.length) {
            if (lines[i].isBlank()) {
                i++;
                continue;
            }
            int start = i;
            while (i < lines.length && !lines[i].isBlank()) {
                i++;
            }
            block(lines, start, i, cues);
            if (cues.size() > maxCues) {
                throw new ParseException("Quá " + maxCues + " cue");
            }
        }
        if (cues.isEmpty()) {
            throw new ParseException("Không tìm thấy cue nào");
        }
        // Web tìm cue bằng tìm nhị phân nên cần thứ tự theo giờ, dù file không sắp.
        cues.sort(Comparator.comparingLong(Cue::startMs).thenComparingLong(Cue::endMs));
        return cues;
    }

    /** Một khối: [id] dòng giờ, rồi các dòng chữ. NOTE/STYLE/REGION của VTT bỏ qua. */
    private static void block(String[] lines, int from, int to, List<Cue> out) {
        String first = lines[from];
        if (first.startsWith("NOTE") || first.startsWith("STYLE") || first.startsWith("REGION")) {
            return;
        }
        int t = from;
        Matcher m = TIMING.matcher(lines[t]);
        if (!m.matches() && from + 1 < to) {
            m = TIMING.matcher(lines[++t]); // dòng đầu là mã cue
        }
        if (!m.matches()) {
            throw new ParseException("Dòng " + (from + 1) + ": không có dòng thời gian hợp lệ");
        }
        long start = ms(m.group(1));
        long end = ms(m.group(2));
        if (end <= start) {
            throw new ParseException("Dòng " + (t + 1) + ": thời gian kết thúc phải sau thời gian bắt đầu");
        }
        var text = new StringBuilder();
        for (int k = t + 1; k < to; k++) {
            String line = TAG.matcher(lines[k]).replaceAll("").strip();
            if (!line.isEmpty()) {
                text.append(text.isEmpty() ? "" : " ").append(line);
            }
        }
        if (!text.isEmpty()) { // &amp; giải mã cuối cùng để "&amp;lt;" không thành "<"
            out.add(new Cue(start, end, text.toString().replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&nbsp;", " ").replace("&amp;", "&")));
        }
    }

    private static long ms(String ts) {
        String[] p = ts.split("[:.,]");
        int n = p.length; // [h:]mm:ss.fff
        long h = n == 4 ? Long.parseLong(p[0]) : 0;
        if (Long.parseLong(p[n - 3]) >= 60 || Long.parseLong(p[n - 2]) >= 60) {
            throw new ParseException("Thời gian không hợp lệ (phút hoặc giây từ 60 trở lên): " + ts);
        }
        long frac = Long.parseLong((p[n - 1] + "00").substring(0, 3));
        return ((h * 60 + Long.parseLong(p[n - 3])) * 60 + Long.parseLong(p[n - 2])) * 1000 + frac;
    }

    /** VTT chuẩn: mỗi cue một dòng chữ; ký tự đặc biệt thoát lại để parse lại ra đúng text. */
    public static String toVtt(List<Cue> cues) {
        var sb = new StringBuilder("WEBVTT\n\n");
        for (Cue c : cues) {
            sb.append(fmt(c.startMs())).append(" --> ").append(fmt(c.endMs())).append('\n')
                    .append(c.text().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("\n\n");
        }
        return sb.toString();
    }

    private static String fmt(long ms) {
        return String.format("%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
    }
}
