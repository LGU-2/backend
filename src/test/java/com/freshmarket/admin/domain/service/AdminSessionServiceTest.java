package com.freshmarket.admin.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.freshmarket.admin.domain.dto.AdminSessionCreateRequest;
import com.freshmarket.admin.domain.dto.AdminSessionResponse;
import com.freshmarket.admin.domain.entity.Admin;
import com.freshmarket.admin.domain.entity.AdminFixture;
import com.freshmarket.admin.domain.entity.AdminRole;
import com.freshmarket.admin.domain.exception.AdminErrorCode;
import com.freshmarket.admin.domain.exception.AdminException;
import com.freshmarket.admin.domain.repository.AdminRepository;
import com.freshmarket.common.security.JwtTokenProvider;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/*
 * AdminRepository 만 mock 이다 (들어오는 데이터를 제공하는 의존성, UT-4-01).
 * PasswordEncoder 와 JwtTokenProvider 는 순수 로직이라 실제 구현을 그대로 쓴다.
 * mock 으로 대체하면 "비밀번호가 실제로 검증되는가", "토큰이 실제로 만들어지는가" 를
 * 이 테스트가 더 이상 보장하지 못한다 (UT-1-01 회귀 방어).
 */
class AdminSessionServiceTest {

    private static final String RAW_PASSWORD = "Freahman!2026";
    private static final String TEST_JWT_SECRET =
            "test-only-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256";

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_JWT_SECRET);

    private final AdminSessionService adminSessionService = new AdminSessionService(
            adminRepository,
            passwordEncoder,
            jwtTokenProvider,
            Clock.systemDefaultZone(),
            1800L,
            86400L
    );

    @Test
    void 아이디와_비밀번호가_일치하면_토큰을_발급한다() {
        // given
        Admin admin = AdminFixture.active(
                "admin.kim",
                passwordEncoder.encode(RAW_PASSWORD),
                AdminRole.ADMIN
        );

        when(adminRepository.findByLoginId("admin.kim")).thenReturn(Optional.of(admin));

        AdminSessionCreateRequest request = new AdminSessionCreateRequest("admin.kim", RAW_PASSWORD);

        // when
        AdminSessionResponse response = adminSessionService.create(request);

        // then
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800L);
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.admin().loginId()).isEqualTo("admin.kim");
        assertThat(response.admin().role()).isEqualTo(AdminRole.ADMIN);

        // 로그인 성공 시 리프레시 토큰이 엔티티에도 반영되어야
        // 다음 로그인에서 이전 토큰이 무효가 된다.
        assertThat(admin.getRefreshTokenHash()).isNotNull();
        assertThat(admin.getRefreshTokenExpiresAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_아이디면_로그인에_실패한다() {
        // given
        when(adminRepository.findByLoginId("nobody")).thenReturn(Optional.empty());

        AdminSessionCreateRequest request = new AdminSessionCreateRequest("nobody", RAW_PASSWORD);

        // when, then
        // 이 경로에서도 내부적으로 더미 해시로 BCrypt 를 돌린다 (SEC-6-04).
        // 예외 없이 LOGIN_FAILED 로 끝나는 것 자체가 더미 해시가
        // 유효한 BCrypt 형식이라는 회귀 방어다.
        assertThatThrownBy(() -> adminSessionService.create(request))
                .isInstanceOf(AdminException.class)
                .extracting(e -> ((AdminException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.LOGIN_FAILED);
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() {
        // given
        Admin admin = AdminFixture.active(
                "admin.kim",
                passwordEncoder.encode(RAW_PASSWORD),
                AdminRole.ADMIN
        );

        when(adminRepository.findByLoginId("admin.kim"))
                .thenReturn(Optional.of(admin));

        AdminSessionCreateRequest request =
                new AdminSessionCreateRequest("admin.kim", "wrong-password");

        // when, then
        assertThatThrownBy(() -> adminSessionService.create(request))
                .isInstanceOf(AdminException.class)
                .extracting(e -> ((AdminException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.LOGIN_FAILED);
    }

    @Test
    void 비활성_계정이고_비밀번호가_맞으면_비활성화_사실을_알려준다() {
        // given
        Admin admin = AdminFixture.inactive(
                "admin.kim",
                passwordEncoder.encode(RAW_PASSWORD),
                AdminRole.ADMIN
        );

        when(adminRepository.findByLoginId("admin.kim"))
                .thenReturn(Optional.of(admin));

        AdminSessionCreateRequest request =
                new AdminSessionCreateRequest("admin.kim", RAW_PASSWORD);

        // when, then
        assertThatThrownBy(() -> adminSessionService.create(request))
                .isInstanceOf(AdminException.class)
                .extracting(e -> ((AdminException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ACCOUNT_INACTIVE);
    }

    /*
     * 순서를 바꾼 이유(SEC-6-04)를 잠그는 테스트다.
     * 예전 순서(상태 확인이 먼저)였다면 비밀번호를 몰라도
     * 계정이 비활성이라는 사실이 드러났다.
     *
     * 지금은 비밀번호가 맞아야만 ACCOUNT_INACTIVE 에 도달하므로,
     * 틀린 비밀번호로는 계정 상태를 알아낼 수 없다.
     */
    @Test
    void 비활성_계정이어도_비밀번호가_틀리면_계정_상태를_알려주지_않는다() {
        // given
        Admin admin = AdminFixture.inactive(
                "admin.kim",
                passwordEncoder.encode(RAW_PASSWORD),
                AdminRole.ADMIN
        );

        when(adminRepository.findByLoginId("admin.kim"))
                .thenReturn(Optional.of(admin));

        AdminSessionCreateRequest request =
                new AdminSessionCreateRequest("admin.kim", "wrong-password");

        // when, then
        assertThatThrownBy(() -> adminSessionService.create(request))
                .isInstanceOf(AdminException.class)
                .extracting(e -> ((AdminException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.LOGIN_FAILED);
    }
}