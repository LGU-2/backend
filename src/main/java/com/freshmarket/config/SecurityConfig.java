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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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
     * 관리자 인증 리소스 경로다 (auth.md: POST 로그인/재발급, DELETE 로그아웃이 이 하나를 공유한다).
     * 로그인(POST)은 인증 그 자체라 공개해야 한다.
     */
    private final String adminAuthPath;

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
            @Value("${app.security.admin-auth-path}") String adminAuthPath) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.adminAuthPath = adminAuthPath;
    }

    /*
     * 리프레시 토큰을 HttpOnly 쿠키로 내려주므로 CSRF 보호를 켠다 (리뷰 반영, auth.md "토큰을 어떻게 전달하나" 절).
     * 브라우저가 쿠키를 요청마다 자동으로 실어 보내는 순간부터 그걸 노리는 위조 요청(CSRF)이 성립하는 공격 조건이 된다.
     *
     * 1차 방어는 쿠키의 SameSite=Strict 다(대부분의 크로스사이트 요청 자체가 막힌다).
     * 그 위에 CSRF 토큰(더블 서브밋 쿠키, CookieCsrfTokenRepository)을 방어층으로 더 쌓는다 — 서버 세션이 없는 STATELESS 구성이라 기본 저장소(HttpSession)를 못 쓴다.
     *
     * 로그인(POST) 만 예외로 둔다. 로그인 시점에는 아직 리프레시 토큰 쿠키 자체가 없어서 위조할 인증 상태가 없다.
     * 메서드까지 지정해서 예외를 좁힌 이유는, 재발급/로그아웃이 로그인과 같은 경로 문자열(adminAuthPath)을 쓰기 때문이다
     * — 경로만으로 예외를 주면 나중에 추가할 로그아웃(DELETE, 쿠키를 실제로 소비하는 요청)까지 조용히 CSRF 검사에서 빠져 버린다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, adminAuthPath)))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, adminAuthPath).permitAll()
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
    public PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
}
