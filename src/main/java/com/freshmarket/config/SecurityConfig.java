package com.freshmarket.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/*
 * 무엇을 열고 무엇을 막을지만 정한다.
 * 오류 응답의 구조는 GlobalExceptionHandler 가 혼자 소유한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * 인증 없이 여는 경로다.
     * 여기 없는 것은 전부 인증을 요구한다. 기본값이 거부다 (SEC-1-04).
     */
    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /*
     * 로그인은 그 자체가 인증 수단이라 인증 없이 열어야 한다.
     * POST 만 연다. 나중에 세션 목록 조회(GET) 가 생기면 그건 인증이 필요하다.
     */
    private final String adminLoginPath;

    /*
     * 헬스체크 경로는 아직 없다. actuator 를 의존성에 넣지 않았다.
     * 넣을 때 /actuator/health/** 를 위 목록에 더한다. ALB 가 그 경로를 찌른다.
     */

    /*
     * 필터 체인에서 난 예외를 MVC 예외 처리로 되돌린다.
     * 이렇게 하지 않으면 인증 실패 응답만 여기서 따로 만들게 되어 오류 구조가 두 곳으로 갈린다.
     */
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            @Value("${app.security.admin-login-path}") String adminLoginPath) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.adminLoginPath = adminLoginPath;
    }

    /*
     * Sonar S4502:
     * 관리자 로그인은 인증 전 공개 POST이며 세션 기반 인증이나 인증 쿠키를 사용하지 않는다.
     * 애플리케이션은 STATELESS + Bearer JWT 인증 방식이므로 브라우저가 자동으로 전송하는
     * 인증 정보를 악용하는 일반적인 CSRF 공격 조건에 해당하지 않는다.
     *
     * CSRF 보호 전체를 비활성화하는 것이 아니라 관리자 로그인 POST 경로만 검사에서 제외한다.
     */
    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * 서버 세션을 두지 않으므로 CSRF 토큰을 보관할 곳이 없다.
                 * 쿠키 기반 인증으로 바꾸면 이 두 줄을 함께 되돌려야 한다.
                 */
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, adminLoginPath).permitAll()
                        .anyRequest().authenticated())
                /*
                 * 넘긴 예외는 GlobalExceptionHandler 의 @ExceptionHandler 가 받는다.
                 * handler 자리에 null 을 주는 것은 이 시점에 처리할 컨트롤러 메서드가 없기 때문이다.
                 */
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                handlerExceptionResolver.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) ->
                                handlerExceptionResolver.resolveException(request, response, null, exception)));
        return http.build();
    }

    // 관리자 비밀번호 해싱 전용. 회원은 카카오에 인증을 위임하므로 비밀번호 자체가 없다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
