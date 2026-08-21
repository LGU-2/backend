package com.freshmarket.admin.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.freshmarket.admin.domain.entity.AdminRole;
import org.junit.jupiter.api.Test;

/*
 * 로그인 경로의 record 세 개는 각각 비밀번호, 액세스 토큰, 리프레시 토큰을 담는다.
 * 이 객체들이 로그(요청 바인딩 실패, 예외 스택, 디버그 로그)에 그대로 찍히면
 * record 기본 toString() 이 모든 필드를 나열해 평문이 그대로 남는다 (SEC-4-02).
 * toString() 오버라이딩이 실수로 지워지거나 필드가 추가되며 마스킹이 빠지는 것을 이 테스트가 잡는다.
 */
class AdminLoginDtoMaskingTest {

    @Test
    void 요청의_toString은_비밀번호를_가린다() {
        AdminLoginRequest request = new AdminLoginRequest("admin.kim", "raw-password-1234");

        String result = request.toString();

        assertThat(result).contains("admin.kim").doesNotContain("raw-password-1234");
    }

    @Test
    void 응답의_toString은_액세스_토큰을_가린다() {
        AdminLoginResponse response = new AdminLoginResponse(
                "eyJhbGciOiJIUzI1NiJ9.this-is-a-fake-jwt-access-token",
                "Bearer",
                1800L,
                new AdminLoginResponse.AdminSummary("admin.kim", "김관리", AdminRole.ADMIN));

        String result = response.toString();

        assertThat(result).contains("Bearer").doesNotContain("eyJhbGciOiJIUzI1NiJ9.this-is-a-fake-jwt-access-token");
    }

    @Test
    void 결과의_toString은_리프레시_토큰을_가린다() {
        AdminLoginResponse response = new AdminLoginResponse(
                "access-token-value",
                "Bearer",
                1800L,
                new AdminLoginResponse.AdminSummary("admin.kim", "김관리", AdminRole.ADMIN));
        AdminLoginResult result = new AdminLoginResult(response, "raw-refresh-token-value", 86400L);

        String output = result.toString();

        // 중첩된 response 의 accessToken 도 함께 가려져야 한다 (response.toString() 에 위임)
        assertThat(output).doesNotContain("raw-refresh-token-value").doesNotContain("access-token-value");
    }
}