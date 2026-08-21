package com.freshmarket.product;

import static org.springframework.http.HttpMethod.GET;

import com.freshmarket.common.auth.ApiSecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/*
 * product 도메인이 자기 경로의 인가를 소유한다.
 *
 * 관리자 경로(/v1/admin/**)는 이 체인이 잡지 않는다.
 * 어느 도메인도 주장하지 않는 경로는 SecurityConfig 의 마지막 체인이 받아 인증을 요구한다.
 * 그 편이 /v1/admin/products 아래에 다른 도메인(stock)의 경로가 끼어 있는 지금 구조와 맞는다.
 */
@Configuration
class ProductSecurityConfig {

    @Bean
    @Order(ApiSecurityDefaults.DOMAIN_CHAIN_ORDER)
    SecurityFilterChain productSecurityFilterChain(HttpSecurity http, ApiSecurityDefaults defaults)
            throws Exception {
        return defaults.apply(http)
                .securityMatcher("/v1/products", "/v1/products/**")
                .authorizeHttpRequests(auth -> auth
                        // 상품 목록과 상세는 비로그인도 본다
                        .requestMatchers(GET, "/v1/products", "/v1/products/**").permitAll()

                        .anyRequest().authenticated())
                .build();
    }
}
