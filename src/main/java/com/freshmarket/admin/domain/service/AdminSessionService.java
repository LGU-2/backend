package com.freshmarket.admin.domain.service;

import com.freshmarket.admin.domain.dto.AdminSessionCreateRequest;
import com.freshmarket.admin.domain.dto.AdminSessionResponse;
import com.freshmarket.admin.domain.entity.Admin;
import com.freshmarket.admin.domain.exception.AdminErrorCode;
import com.freshmarket.admin.domain.exception.AdminException;
import com.freshmarket.admin.domain.repository.AdminRepository;
import com.freshmarket.common.security.JwtTokenProvider;
import com.freshmarket.common.security.OpaqueTokenGenerator;
import com.freshmarket.common.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 관리자 로그인만 다룬다. 로그아웃, 토큰 재발급, 비밀번호 변경은 별도 PR 이다.
 *
 * 5회 실패 시 30분 잠금은 이번 범위에서 뺐다 (admin 테이블에 fail_count, locked_until 컬럼이 없다).
 * 그래서 계정 상태는 활성/비활성 둘만 본다. (SEC-6-01, SEC-6-02 는 리뷰 요청 사항에 기록했다.)
 */
@Service
@Transactional
public class AdminSessionService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ADMIN = "ADMIN";

    // 실제 계정과 무관한 값이다. 계정이 없을 때도 이 해시로 BCrypt 를 돌려 응답 시간을 맞춘다 (SEC-6-04)
    private static final String DUMMY_PASSWORD_SOURCE = "dummy-password-for-constant-time-comparison";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;
    private final String dummyPasswordHash;

    public AdminSessionService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            Clock clock,
            @Value("${app.jwt.admin.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${app.jwt.admin.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.clock = clock;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
        // 같은 인코더로 미리 만들어 둬야 진짜 비밀번호 검증과 연산 비용(코스트 팩터)이 완전히 같다
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_SOURCE);
    }

    public AdminSessionResponse create(AdminSessionCreateRequest request) {
        Optional<Admin> found = adminRepository.findByLoginId(request.loginId());

        /*
         * 계정이 없어도 항상 BCrypt 를 돌린다 (SEC-6-04).
         * "계정 상태 확인 후 비밀번호 검증"이라는 원래 순서를 그대로 두면, 계정이 없을 때
         * BCrypt 자체를 건너뛰게 되어 있는 것과 없는 것의 응답 시간이 갈린다 — 그 시간 차이가
         * 그 자체로 아이디 존재 여부를 흘리는 타이밍 사이드채널이 된다.
         *
         * found 가 비어 있으면 아래 단락 값과 무관하게 항상 LOGIN_FAILED 로 던진다
         * (단락 평가로 그렇게 되어 있다). dummyPasswordHash 비교는 오직 시간을 맞추기 위한 것이다.
         */
        String hashToCompare = found.map(Admin::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);

        if (found.isEmpty() || !passwordMatches) {
            throw new AdminException(AdminErrorCode.LOGIN_FAILED);
        }

        /*
         * 비활성 계정도 로그인 단계에서는 LOGIN_FAILED 로 통일한다 (SEC-6-04).
         * 존재 여부나 계정 상태에 따라 응답 코드/메시지가 달라지면 인증 응답만으로
         * 계정의 존재 또는 상태를 추측할 수 있기 때문이다.
         */
        Admin admin = found.get();
        if (!admin.isActive()) {
            throw new AdminException(AdminErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.createToken(
                String.valueOf(admin.getId()),
                Map.of(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ADMIN, CLAIM_ROLE, admin.getRole().name()),
                Duration.ofSeconds(accessTokenValiditySeconds));

        String rawRefreshToken = OpaqueTokenGenerator.generate();
        admin.issueRefreshToken(
                TokenHasher.sha256Hex(rawRefreshToken),
                LocalDateTime.now(clock).plusSeconds(refreshTokenValiditySeconds));

        return new AdminSessionResponse(
                accessToken,
                TOKEN_TYPE,
                accessTokenValiditySeconds,
                rawRefreshToken,
                new AdminSessionResponse.AdminSummary(
                        admin.getLoginId(), admin.getName(), admin.getRole()));
    }
}