package com.pps.smartenglish.exercise.bulk;

/** Lỗi của một dòng nhập (thiếu cột, sai cú pháp ô); field là tên cột để người nhập biết sửa ở đâu. */
public class RowException extends RuntimeException {

    private final String field;

    public RowException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
