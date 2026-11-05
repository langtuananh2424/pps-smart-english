package com.pps.smartenglish.shared.error;

import jakarta.servlet.ServletException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        var body = ApiError.of(e.getCode(), e.getMessage(), e.getRetryAfterSeconds() != null, e.getDetails());
        var res = ResponseEntity.status(e.getStatus());
        if (e.getRetryAfterSeconds() != null) {
            res.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return res.body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> details.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "validation_failed", "Dữ liệu không hợp lệ", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "malformed_request", "Nội dung yêu cầu không đọc được", Map.of());
    }

    /** Các lỗi MVC có sẵn mã HTTP (404 không có route, 405, 415...). */
    @ExceptionHandler(ServletException.class)
    ResponseEntity<ApiError> mvc(ServletException ex) {
        if (!(ex instanceof ErrorResponse e)) {
            return unexpected(ex);
        }
        HttpStatusCode s = e.getStatusCode();
        String code = switch (s.value()) {
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 415 -> "unsupported_media_type";
            default -> "bad_request";
        };
        String msg = s.value() == 404 ? "Không tìm thấy" : "Yêu cầu không hợp lệ";
        return ResponseEntity.status(s).body(ApiError.of(code, msg, false, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Lỗi không xử lý được", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Đã có lỗi xảy ra, vui lòng thử lại sau", Map.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatus s, String code, String msg, Map<String, String> details) {
        return ResponseEntity.status(s).body(ApiError.of(code, msg, false, details));
    }
}
