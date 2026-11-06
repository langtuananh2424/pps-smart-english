package com.pps.smartenglish.catalog.domain;

/** Trạng thái hiển thị của nội dung. Một phần tử chỉ tới được học viên khi nó và mọi cấp cha đều PUBLISHED. */
public final class ContentStatus {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    private ContentStatus() {}
}
