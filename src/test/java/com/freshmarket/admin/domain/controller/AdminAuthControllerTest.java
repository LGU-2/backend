package com.freshmarket.admin.domain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.freshmarket.admin.domain.dto.AdminLoginRequest;
import com.freshmarket.admin.domain.dto.AdminLoginResponse;
import com.freshmarket.admin.domain.dto.AdminLoginResult;
import com.freshmarket.admin.domain.entity.AdminRole;
import com.freshmarket.admin.domain.service.AdminAuthService;
import com.freshmarket.common.auth.AuthCookieFactory;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import com.freshmarket.common.response.ResponseEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAuthControllerTest {

    private static final String REFRESH_TOKEN_COOKIE_PATH = "/v1/admin/auth/";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final long ACCESS_TOKEN_VALIDITY_SECONDS = 1800L;
    private static final long REFRESH_TOKEN_VALIDITY_SECONDS = 86400L;

    private final AdminAuthService adminAuthService = mock(AdminAuthService.class);

    @Test
    void 로그인에_성공하면_액세스_토큰은_본문으로_리프레시_토큰은_HttpOnly_쿠키로_내려간다() {
        // given
        AuthCookieFactory authCookieFactory = new AuthCookieFactory(mock(JwtTokenProvider.class));
        ReflectionTestUtils.setField(authCookieFactory, "secure", true);
        AdminAuthController controller = new AdminAuthController(adminAuthService, authCookieFactory);

        AdminLoginRequest request = new AdminLoginRequest("admin.kim", "Freahman!2026");
        AdminLoginResponse loginResponse = new AdminLoginResponse(
                ACCESS_TOKEN,
                "Bearer",
                ACCESS_TOKEN_VALIDITY_SECONDS,
                new AdminLoginResponse.AdminSummary("admin.kim", "김관리", AdminRole.ADMIN)
        );

        when(adminAuthService.login(request)).thenReturn(new AdminLoginResult(
                loginResponse,
                REFRESH_TOKEN,
                REFRESH_TOKEN_VALIDITY_SECONDS
        ));

        // when
        ResponseEntity<ResponseEnvelope<AdminLoginResponse>> response = controller.login(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(loginResponse);
        assertThat(response.getBody().data().accessToken()).isEqualTo(ACCESS_TOKEN);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .isNotNull()
                .contains("refreshToken=" + REFRESH_TOKEN)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=" + REFRESH_TOKEN_COOKIE_PATH)
                .doesNotContain(ACCESS_TOKEN);
    }
}