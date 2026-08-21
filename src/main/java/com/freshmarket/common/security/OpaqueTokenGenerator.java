package com.freshmarket.common.security;

import java.security.SecureRandom;
import java.util.Base64;

/*
 * 리프레시 토큰은 JWT 가 아니라 불투명 문자열이다.
 * 클레임을 담을 이유가 없고(서버가 해시로 조회할 뿐이다), 파싱 가능한 형식은 위조 표면만 늘린다.
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private OpaqueTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}