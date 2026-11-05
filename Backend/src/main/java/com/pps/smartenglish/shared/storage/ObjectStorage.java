package com.pps.smartenglish.shared.storage;

import java.time.Duration;

/** Kho media riêng tư (NFR-SEC-02): client chỉ nhận URL ký có hạn, không bao giờ nhận key thô. */
public interface ObjectStorage {

    /** URL GET ký sẵn, trỏ về địa chỉ công khai của kho để trình duyệt mở được. */
    String presignGet(String key, Duration ttl);
}
