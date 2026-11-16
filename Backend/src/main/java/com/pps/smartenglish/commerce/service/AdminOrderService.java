package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.dto.AdminOrderView;
import com.pps.smartenglish.commerce.repository.AdminOrderRepository;
import com.pps.smartenglish.commerce.repository.AdminOrderRepository.ExportRow;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-ADM-03: danh sách đơn cho nhân sự và xuất CSV đơn đã thanh toán (D-38). Ngày chia theo giờ Việt Nam, lưu UTC. */
@Service
public class AdminOrderService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    static final int MAX_EXPORT_DAYS = 366;
    private static final int EXPORT_BATCH = 500;
    private static final Set<String> STATUSES = Set.of("PENDING", "PAID", "EXPIRED", "CANCELLED");
    private static final List<String> HEADER = List.of("Mã đơn", "Thời điểm thanh toán", "Email người mua", "Họ tên",
            "Tên sản phẩm", "Loại sản phẩm", "Thành tiền (VND)", "Cổng thanh toán", "Mã giao dịch cổng");

    /** Khoảng đã kiểm tra: [start, end) theo giờ Việt Nam; from/to là ngày người gọi gửi lên. */
    public record ExportRange(LocalDate from, LocalDate to, Instant start, Instant end) {}

    private final AdminOrderRepository orders;
    private final AuditRecorder audit;
    private final ZoneId zone;

    AdminOrderService(AdminOrderRepository orders, AuditRecorder audit,
                      @Value("${app.reports.zone:Asia/Ho_Chi_Minh}") String zone) {
        this.orders = orders;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
    }

    /** Lọc theo ngày tạo đơn (created_at) khi có from/to; email là khớp một phần, không phân biệt hoa thường. */
    @Transactional(readOnly = true)
    public CursorPage<AdminOrderView> search(String status, String from, String to, String email, String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        String st = blankToNull(status);
        if (st != null && !STATUSES.contains(st)) {
            throw ApiException.validation("status", "Trạng thái không hợp lệ");
        }
        LocalDate f = from == null || from.isBlank() ? null : parse("from", from);
        LocalDate t = to == null || to.isBlank() ? null : parse("to", to);
        if (f != null && t != null && t.isBefore(f)) {
            throw ApiException.validation("to", "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        String mail = blankToNull(email);
        var rows = orders.search(Cursors.decode(cursor), st, f == null ? null : f.atStartOfDay(zone).toInstant(),
                t == null ? null : t.plusDays(1).atStartOfDay(zone).toInstant(),
                mail == null ? null : "%" + escapeLike(mail.toLowerCase()) + "%", size + 1);
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(page, more ? Cursors.encode(page.get(size - 1).id()) : null);
    }

    /** Kiểm tra khoảng ngày (400 nếu sai) rồi ghi audit. Gọi trước khi ghi header/body để lỗi vẫn là JSON thường. */
    public ExportRange prepareExport(String from, String to) {
        if (from == null || from.isBlank()) {
            throw ApiException.validation("from", "Bắt buộc, dạng YYYY-MM-DD");
        }
        if (to == null || to.isBlank()) {
            throw ApiException.validation("to", "Bắt buộc, dạng YYYY-MM-DD");
        }
        LocalDate f = parse("from", from);
        LocalDate t = parse("to", to);
        if (t.isBefore(f)) {
            throw ApiException.validation("to", "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (ChronoUnit.DAYS.between(f, t) + 1 > MAX_EXPORT_DAYS) {
            throw ApiException.validation("to", "Khoảng xuất tối đa " + MAX_EXPORT_DAYS + " ngày");
        }
        audit.record("EXPORT", "ORDER", f + ".." + t, null, Map.of("from", f.toString(), "to", t.toString()));
        return new ExportRange(f, t, f.atStartOfDay(zone).toInstant(), t.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** UTF-8 có BOM để Excel đọc được tiếng Việt. Đọc từng lô theo keyset nên bộ nhớ không phụ thuộc số đơn. */
    public void writeCsv(ExportRange range, OutputStream os) throws IOException {
        Writer out = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        out.write('﻿');
        OrderCsv.row(out, HEADER);
        long last = 0;
        List<ExportRow> batch;
        do {
            batch = orders.paidItemsAfter(last, range.start(), range.end(), EXPORT_BATCH);
            for (ExportRow r : batch) {
                OrderCsv.row(out, List.of(nz(r.orderCode()),
                        r.paidAt().toInstant().atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        nz(r.email()), nz(r.displayName()), nz(r.productName()), nz(r.productType()),
                        String.valueOf(r.priceVnd()), nz(r.provider()), nz(r.providerRef())));
                last = r.itemId();
            }
            out.flush();
        } while (batch.size() == EXPORT_BATCH);
    }

    private static LocalDate parse(String field, String v) {
        try {
            return LocalDate.parse(v.strip());
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, "Phải có dạng YYYY-MM-DD");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
