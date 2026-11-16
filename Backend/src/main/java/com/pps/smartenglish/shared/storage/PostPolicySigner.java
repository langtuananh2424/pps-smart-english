package com.pps.smartenglish.shared.storage;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ký SigV4 cho POST policy (SDK v2 không có). Chuỗi cần ký chính là policy đã base64.
 * Chọn tự ký thay vì thêm thư viện MinIO: chỉ ~15 dòng, không thêm phụ thuộc, test được bằng vector công khai của AWS.
 */
final class PostPolicySigner {

    private PostPolicySigner() {}

    /** Chữ ký hex: HMAC chuỗi kDate / kRegion / kService / kSigning rồi ký stringToSign. */
    static String signature(String secret, String date, String region, String service, String stringToSign) {
        byte[] key = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        return HexFormat.of().formatHex(hmac(key, stringToSign));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
