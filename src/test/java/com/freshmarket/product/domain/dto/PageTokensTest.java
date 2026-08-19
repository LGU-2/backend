package com.freshmarket.product.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// PageTokens 의 인코딩/디코딩 왕복과 잘못된 입력 처리를 확인한다
class PageTokensTest {

    @Test
    void 커서를_인코딩하고_디코딩하면_원래_값으로_돌아온다() {
        String token = PageTokens.encode(12L);

        assertThat(PageTokens.decode(token)).isEqualTo(12L);
    }

    @Test
    void null_커서는_null_토큰이_된다() {
        assertThat(PageTokens.encode(null)).isNull();
    }

    @Test
    void null_토큰을_디코딩하면_null이다() {
        assertThat(PageTokens.decode(null)).isNull();
    }

    @Test
    void 빈_토큰을_디코딩하면_null이다() {
        assertThat(PageTokens.decode("")).isNull();
    }

    @Test
    void 형식이_어긋난_토큰을_디코딩하면_null이다() {
        assertThat(PageTokens.decode("이건-유효한-Base64가-아니다!!")).isNull();
    }

    @Test
    void 접두사가_다른_토큰을_디코딩하면_null이다() {
        // 같은 Base64 URL 인코딩이지만 우리 접두사("p:")가 아닌 값
        String other = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("x:12".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(PageTokens.decode(other)).isNull();
    }
}