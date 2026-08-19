package com.freshmarket.product.domain.dto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/*
 * 페이지 토큰 인코딩. 커서 값을 클라이언트가 해석할 수 없는 문자열로 감싼다 (API-5-02).
 * 암호화가 아니라 계약을 감추는 것이 목적이다. 뜯어보면 id 가 보이지만
 * 그 사실에 기대는 클라이언트 코드가 생기지 않게 하는 것으로 충분하다.
 */
public final class PageTokens {

    private static final String PREFIX = "p:";

    private PageTokens() {
    }

    // 커서 값을 토큰으로 만든다
    public static String encode(Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + cursorId).getBytes(StandardCharsets.UTF_8));
    }

    // 토큰에서 커서 값을 꺼낸다. 비어 있거나 형식이 어긋나면 첫 페이지로 본다
    public static Long decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                return null;
            }
            return Long.valueOf(decoded.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}