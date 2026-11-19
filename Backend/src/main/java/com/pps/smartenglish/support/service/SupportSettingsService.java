package com.pps.smartenglish.support.service;

import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.support.domain.SupportHour;
import com.pps.smartenglish.support.domain.SupportSettings;
import com.pps.smartenglish.support.dto.TicketDtos.HourDto;
import com.pps.smartenglish.support.dto.TicketDtos.Info;
import com.pps.smartenglish.support.dto.TicketDtos.SettingsView;
import com.pps.smartenglish.support.dto.TicketDtos.UpdateSettings;
import com.pps.smartenglish.support.repository.SupportHourRepository;
import com.pps.smartenglish.support.repository.SupportSettingsRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BQ-09: giờ làm việc, thời gian phản hồi dự kiến, Zalo và hotline do admin cài, không có số liệu mặc định.
 * Chưa cài giờ làm việc thì coi như luôn trong giờ (FR-SUP-02 chỉ báo ngoài giờ khi có cấu hình).
 */
@Service
public class SupportSettingsService {

    private final SupportSettingsRepository settings;
    private final SupportHourRepository hours;
    private final AuditRecorder audit;
    private final Clock clock;

    SupportSettingsService(SupportSettingsRepository settings, SupportHourRepository hours, AuditRecorder audit, Clock clock) {
        this.settings = settings;
        this.hours = hours;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsView get() {
        var s = current();
        return new SettingsView(s.getTimezone(), s.getEtaMessage(), s.getZaloUrl(), s.getHotline(),
                hours.findAllByOrderByDayOfWeekAscOpenTimeAsc().stream().map(SupportSettingsService::dto).toList());
    }

    /** Thay toàn bộ cấu hình: giờ làm việc cũ bị xóa, khung giờ cùng ngày không được chồng nhau. */
    @Transactional
    public SettingsView update(UpdateSettings req) {
        var before = get();
        List<SupportHour> rows = parseHours(req);
        var s = current();
        s.update(blank(req.timezone()), blank(req.etaMessage()), blank(req.zaloUrl()), blank(req.hotline()));
        settings.save(s);
        hours.deleteAllInBatch();
        hours.saveAll(rows);
        var after = get();
        audit.record("UPDATE", "SUPPORT_SETTINGS", 1, before, after);
        return after;
    }

    /** Thông tin cho học viên; inHours true khi chưa cài giờ làm việc. */
    @Transactional(readOnly = true)
    public Info info() {
        var s = current();
        return new Info(inHours(s), s.getEtaMessage(), s.getZaloUrl(), s.getHotline());
    }

    private boolean inHours(SupportSettings s) {
        var rows = hours.findAllByOrderByDayOfWeekAscOpenTimeAsc();
        if (rows.isEmpty() || s.getTimezone() == null) {
            return true;
        }
        var now = clock.instant().atZone(ZoneId.of(s.getTimezone()));
        LocalTime time = now.toLocalTime();
        int day = now.getDayOfWeek().getValue();
        return rows.stream().anyMatch(h -> h.getDayOfWeek() == day && !time.isBefore(h.getOpenTime()) && time.isBefore(h.getCloseTime()));
    }

    private List<SupportHour> parseHours(UpdateSettings req) {
        List<HourDto> in = req.hours() == null ? List.of() : req.hours();
        if (!in.isEmpty()) {
            try {
                ZoneId.of(req.timezone() == null ? "" : req.timezone());
            } catch (DateTimeException e) {
                throw ApiException.validation("timezone", "Cần múi giờ hợp lệ, ví dụ Asia/Ho_Chi_Minh");
            }
        }
        List<SupportHour> rows = new ArrayList<>();
        for (HourDto h : in) {
            LocalTime open = time(h.open(), "open");
            LocalTime close = time(h.close(), "close");
            if (!open.isBefore(close)) {
                throw ApiException.validation("close", "Giờ đóng phải sau giờ mở");
            }
            rows.add(new SupportHour(h.dayOfWeek(), open, close));
        }
        rows.sort(Comparator.comparingInt(SupportHour::getDayOfWeek).thenComparing(SupportHour::getOpenTime));
        for (int i = 1; i < rows.size(); i++) {
            var a = rows.get(i - 1);
            var b = rows.get(i);
            if (a.getDayOfWeek() == b.getDayOfWeek() && b.getOpenTime().isBefore(a.getCloseTime())) {
                throw ApiException.validation("hours", "Khung giờ cùng ngày không được chồng nhau");
            }
        }
        return rows;
    }

    private static LocalTime time(String raw, String field) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeException e) {
            throw ApiException.validation(field, "Giờ dạng HH:mm");
        }
    }

    private SupportSettings current() {
        return settings.findById(1).orElseThrow(() -> new IllegalStateException("Thiếu dòng support_settings"));
    }

    private static HourDto dto(SupportHour h) {
        return new HourDto(h.getDayOfWeek(), h.getOpenTime().toString(), h.getCloseTime().toString());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
