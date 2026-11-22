package com.pps.smartenglish.shared.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Kho media riêng tư (NFR-SEC-02): client chỉ nhận URL ký có hạn, không bao giờ nhận key thô. */
public interface ObjectStorage {

    /** Form tải lên trình duyệt: POST multipart tới url, các fields đặt trước field `file`. */
    record PostForm(String url, Map<String, String> fields) {}

    record ObjectMeta(long size, String contentType) {}

    /** URL GET ký sẵn, trỏ về địa chỉ công khai của kho để trình duyệt mở được. */
    String presignGet(String key, Duration ttl);

    /** Presigned POST policy (D-27): key cố định, Content-Type cố định, dung lượng 1..maxBytes, hết hạn sau ttl. */
    PostForm presignPost(String key, String contentType, long maxBytes, Duration ttl);

    /** Siêu dữ liệu object thật trên kho; empty nếu chưa có. */
    Optional<ObjectMeta> head(String key);

    /** Đọc tối đa maxBytes byte đầu (để kiểm header file). */
    byte[] readHead(String key, int maxBytes);

    /** Luồng đọc object; nơi gọi phải đóng. */
    InputStream open(String key);

    /** Ghi object (ghi đè nếu có). Dùng cho file do máy chủ tự sinh, như ảnh đã xử lý. */
    void put(String key, byte[] data, String contentType);

    /** Xóa object; không có thì bỏ qua. */
    void delete(String key);
}
