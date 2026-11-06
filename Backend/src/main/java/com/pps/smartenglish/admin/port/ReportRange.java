package com.pps.smartenglish.admin.port;

import java.time.Instant;
import java.time.ZoneId;

/** Kỳ báo cáo nửa mở [start, end) đã đổi sang Instant; zone dùng để gom theo ngày. */
public record ReportRange(Instant start, Instant end, ZoneId zone) {}
