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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 관리자 로그인만 다룬다. 로그아웃, 토큰 재발급, 비밀번호 변경은 별도 PR 이다.
 *
 * 5회 실패 시 30분 잠금은 이번 범위에서 뺐다 (admin 테이블에 fail_count, locked_until 컬럼이 없다).
 * 그래서 계정 상태는 활성/비활성 둘만 본다.
 */
@Service
@Transactional
public class AdminSessionService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ADMIN = "ADMIN";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public AdminSessionService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.jwt.admin.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${app.jwt.admin.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public AdminSessionResponse create(AdminSessionCreateRequest request) {
        Admin admin = adminRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new AdminException(AdminErrorCode.LOGIN_FAILED));

        /*
         * 상태 확인이 비밀번호 검증보다 먼저다 (요구사항: "계정 상태(비활성, 잠금) 확인 후
         * BCrypt 로 비밀번호 검증"). 관리자는 내부 직원이라 "당신 계정은 비활성 상태다" 를
         * 알려주는 것이 허용된다 — 불특정 다수가 접근하는 회원가입 폼과는 성격이 다르다.
         */
        if (!admin.isActive()) {
            throw new AdminException(AdminErrorCode.ACCOUNT_INACTIVE);
        }

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new AdminException(AdminErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.createToken(
                String.valueOf(admin.getId()),
                Map.of(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ADMIN, CLAIM_ROLE, admin.getRole().name()),
                Duration.ofSeconds(accessTokenValiditySeconds));

        String rawRefreshToken = OpaqueTokenGenerator.generate();
        admin.issueRefreshToken(
                TokenHasher.sha256Hex(rawRefreshToken),
                LocalDateTime.now().plusSeconds(refreshTokenValiditySeconds));

        return new AdminSessionResponse(
                accessToken,
                TOKEN_TYPE,
                accessTokenValiditySeconds,
                rawRefreshToken,
                new AdminSessionResponse.AdminSummary(
                        admin.getLoginId(), admin.getName(), admin.getRole()));
    }
}