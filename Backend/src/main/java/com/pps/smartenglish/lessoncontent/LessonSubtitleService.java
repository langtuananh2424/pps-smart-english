package com.pps.smartenglish.lessoncontent;

import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.lessoncontent.LessonContentDtos.SubtitleInfo;
import com.pps.smartenglish.lessoncontent.SubtitleParser.Cue;
import com.pps.smartenglish.lessoncontent.SubtitleParser.ParseException;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** D-175: phụ đề EN/VI của bài. CMS tải VTT/SRT lên (kiểm, chuẩn hóa, lưu CSDL); học viên đọc lại thành cue. */
@Service
public class LessonSubtitleService {

    private static final Set<String> LANGS = Set.of("en", "vi");

    private final LessonSubtitleRepository subtitles;
    private final LessonRepository lessons;
    private final LessonContentProperties props;
    private final AuditRecorder audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    LessonSubtitleService(LessonSubtitleRepository subtitles, LessonRepository lessons, LessonContentProperties props,
                          AuditRecorder audit, Clock clock, TransactionTemplate tx) {
        this.tx = tx;
        this.subtitles = subtitles;
        this.lessons = lessons;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    public SubtitleInfo put(long lessonId, String lang, MultipartFile file) {
        requireLesson(lessonId);
        requireLang(lang);
        if (file.isEmpty()) {
            throw ApiException.validation("file", "File rỗng");
        }
        if (file.getSize() > props.subtitle().maxBytes()) {
            throw ApiException.validation("file", "File quá " + props.subtitle().maxBytes() / 1024 + " KB");
        }
        List<Cue> cues;
        try {
            cues = SubtitleParser.parse(utf8(file.getBytes()), props.subtitle().maxCues());
        } catch (ParseException e) {
            throw ApiException.validation("file", e.getMessage());
        } catch (IOException e) {
            throw ApiException.validation("file", "Không đọc được file");
        }
        String vtt = SubtitleParser.toVtt(cues);
        try {
            return tx.execute(st -> upsert(lessonId, lang, vtt, cues.size()));
        } catch (DataIntegrityViolationException e) {
            // Hai PUT đầu tiên cùng lúc: bên kia đã chèn, giao dịch này hỏng nên thử lại ở giao dịch mới (lần này là cập nhật).
            return tx.execute(st -> upsert(lessonId, lang, vtt, cues.size()));
        }
    }

    private SubtitleInfo upsert(long lessonId, String lang, String vtt, int cueCount) {
        var row = subtitles.findByLessonIdAndLang(lessonId, lang).orElseGet(() -> new LessonSubtitle(lessonId, lang));
        row.set(vtt, cueCount, Instant.now(clock));
        subtitles.saveAndFlush(row);
        var info = new SubtitleInfo(lessonId, lang, row.getCueCount(), row.getUpdatedAt());
        audit.record("UPDATE", "LESSON_SUBTITLE", lessonId + "/" + lang, null, info);
        return info;
    }

    /** Idempotent. */
    @Transactional
    public void delete(long lessonId, String lang) {
        requireLesson(lessonId);
        requireLang(lang);
        if (subtitles.deleteByLessonIdAndLang(lessonId, lang) > 0) {
            audit.record("DELETE", "LESSON_SUBTITLE", lessonId + "/" + lang, null, null);
        }
    }

    /** Một truy vấn cho nhiều bài (cây CMS). */
    @Transactional(readOnly = true)
    public Map<Long, List<SubtitleInfo>> summaries(Collection<Long> lessonIds) {
        return lessonIds.isEmpty() ? Map.of()
                : subtitles.summaries(lessonIds).stream().collect(Collectors.groupingBy(SubtitleInfo::lessonId));
    }

    /** Phía học viên: một truy vấn, thiếu ngôn ngữ nào thì mảng rỗng. Chỉ gọi sau khi qua cổng quyền xem. */
    @Transactional(readOnly = true)
    public Map<String, List<Cue>> cues(long lessonId) {
        Map<String, List<Cue>> out = new HashMap<>();
        LANGS.forEach(l -> out.put(l, List.of()));
        subtitles.findByLessonId(lessonId).forEach(s -> out.put(s.getLang(), SubtitleParser.parse(s.getContent(), Integer.MAX_VALUE)));
        return out;
    }

    private void requireLesson(long id) {
        if (!lessons.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học");
        }
    }

    private static void requireLang(String lang) {
        if (!LANGS.contains(lang)) {
            throw ApiException.validation("lang", "Chỉ nhận en hoặc vi");
        }
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw ApiException.validation("file", "File phải mã hóa UTF-8");
        }
    }
}
