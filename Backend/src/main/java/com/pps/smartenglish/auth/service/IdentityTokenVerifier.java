package com.pps.smartenglish.auth.service;

/** Xác minh ID token của một nhà cung cấp đăng nhập ngoài (Google; thêm Apple = thêm một bean). */
public interface IdentityTokenVerifier {

    record ExternalIdentity(String subject, String email, String name) {}

    /** Tên nhà cung cấp, lưu ở user_identities.provider (vd GOOGLE). */
    String provider();

    /** Token sai thì ném 401; nhà cung cấp chưa cấu hình thì ném 503. */
    ExternalIdentity verify(String idToken);
}
