package com.pps.smartenglish.commerce.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Mã đơn ORD-XXXXXX ngẫu nhiên mật mã học, không đoán được từ mã trước. */
@Component
class OrderCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    String next() {
        StringBuilder sb = new StringBuilder("ORD-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
