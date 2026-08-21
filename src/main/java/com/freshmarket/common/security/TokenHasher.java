package com.freshmarket.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
 * 리프레시 토큰 평문은 저장하지 않는다. admin.refresh_token_hash 는 SHA-256 hex(64자)를 담는 CHAR(64) 컬럼이다.
 * DB 가 털려도 평문 토큰이 새지 않도록, 저장 직전에 항상 이 클래스를 거친다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JVM 표준 알고리즘이라 정상 환경에서는 발생하지 않는다
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없다", e);
        }
    }
}