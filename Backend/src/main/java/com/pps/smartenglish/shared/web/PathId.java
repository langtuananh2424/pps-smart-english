package com.pps.smartenglish.shared.web;

import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public final class PathId {

    private PathId() {}

    /** Id không phải số cũng là "không tìm thấy", không để lọt thành 500. */
    public static Long parse(String raw, String code, String message) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, code, message);
        }
    }
}
