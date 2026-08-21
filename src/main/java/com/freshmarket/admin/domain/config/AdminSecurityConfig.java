package com.freshmarket.admin.domain.config;

import static org.springframework.http.HttpMethod.POST;

import com.freshmarket.common.auth.ApiSecurityDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/*
 * admin 도메인이 자기 경로의 인가를 소유한다 (member/product SecurityConfig와 같은 구조).
 *
 * securityMatcher를 "/v1/admin/**"이 아니라 "/v1/admin/auth/**"로 좁힌 이유:
 * ProductSecurityConfig 주석에 있듯 "/v1/admin/**" 전체를 한 도메인이 갖지 않는다
 * (예: AdminCategoryController는 "/v1/admin/categories"를 쓰지만 product 도메인 소속이다).
 * 이 체인은 admin 도메인이 실제로 소유한 로그인/인증 경로만 잡는다.
 *
 * CSRF: 공통 기본값(ApiSecurityDefaults)은 CSRF를 꺼둔다 — 회원 쪽은 아직 정하지
 * 못한 상태라서다(docs/api/auth.md "정하지 못한 것" 절).
 *
 * admin 로그인은 그 결정 이전에 별도 리뷰를 거쳐 CSRF를 켜기로 이미 확정했다(auth.md "관리자" 절).
 * 그 결정을 지키기 위해 defaults.apply() 이후 이 체인에서만 csrf()를 다시 켠다.
 */
@Configuration
class AdminSecurityConfig {

    private static final String ADMIN = "TYPE_ADMIN";

    private final String adminAuthPath;

    AdminSecurityConfig(@Value("${admin.auth-path}") String adminAuthPath) { this.adminAuthPath = adminAuthPath; }

    @Bean
    @Order(ApiSecurityDefaults.DOMAIN_CHAIN_ORDER)
    SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, ApiSecurityDefaults defaults) throws Exception {
        return defaults.apply(http)
                .securityMatcher("/v1/admin/auth/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(POST, adminAuthPath)))
                .authorizeHttpRequests(auth -> auth
                        // 로그인(POST)은 인증 그 자체라 공개해야 한다
                        .requestMatchers(POST, adminAuthPath).permitAll()
                        // 재발급/로그아웃/향후 관리자 API는 TYPE_ADMIN 권한을 요구한다
                        .anyRequest().hasAuthority(ADMIN))
                .build();
    }

    // 관리자 비밀번호 해싱 전용. 회원은 카카오에 인증을 위임하므로 비밀번호 자체가 없다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}