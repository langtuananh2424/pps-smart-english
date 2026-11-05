package com.pps.smartenglish.shared.web;

import java.util.List;

/** Phân trang con trỏ (system-design 6.1); nextCursor null = hết dữ liệu. */
public record CursorPage<T>(List<T> data, String nextCursor) {}
