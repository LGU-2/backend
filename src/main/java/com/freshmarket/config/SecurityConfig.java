package com.freshmarket.config;

import com.freshmarket.common.auth.AuthRateLimitFilter;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtAuthenticationFilter;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/*
 * 무엇을 열고 무엇을 막을지만 정한다.
 * 오류 응답의 구조는 GlobalExceptionHandler 가 혼자 소유한다.
 *
 * 회원 인증(JWT + 카카오 OAuth2) 배선만 담당한다. 관리자(admin) 인증은 다른 팀원이 별도로
 * 진행 중이라 이 파일에는 admin 경로 매처를 넣지 않았다 — 나중에 admin 쪽 SecurityConfig
 * 변경과 합칠 때 함께 정리한다.
 *
 * docs/api/auth.md·member.md 기준 카카오 로그인은 프론트가 콜백(redirect_uri)을 직접 받아
 * code/state를 백엔드로 넘기는 구조라 Spring Security의 .oauth2Login(...) 필터 체인은 안 쓴다
 * (자세한 인과관계는 MemberLoginService 주석 참고) — 그래서 그 필터가 처리하던 리다이렉트/
 * 콜백 경로 매처가 없다. 대신 이 API가 쓰는 경로(/v1/auth/**, /v1/members/**)에 맞춰 매처를
 * 두고, 프론트가 다른 오리진에서 쿠키를 주고받을 수 있도록 CORS 빈을 둔다.
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
            "/swagger-ui.html",
            "/webhook/kakao/unlink", // 카카오가 호출하는 웹훅 — 인증 쿠키 없이 들어옴
            "/v1/auth/kakao/authorize", // 로그인 시작 — 아직 토큰이 없는 시점
            "/v1/products"
    };

    /*
     * 헬스체크 경로는 아직 없다. actuator 를 의존성에 넣지 않았다.
     * 넣을 때 /actuator/health/** 를 위 목록에 더한다. ALB 가 그 경로를 찌른다.
     */

    /*
     * 필터 체인에서 난 예외를 MVC 예외 처리로 되돌린다.
     * 이렇게 하지 않으면 인증 실패 응답만 여기서 따로 만들게 되어 오류 구조가 두 곳으로 갈린다.
     */
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenValidAfterRepository accessTokenValidAfterRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            JwtTokenProvider jwtTokenProvider,
            AccessTokenValidAfterRepository accessTokenValidAfterRepository,
            StringRedisTemplate redisTemplate) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenValidAfterRepository = accessTokenValidAfterRepository;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * 서버 세션을 두지 않으므로 CSRF 토큰을 보관할 곳이 없다.
                 * 쿠키 기반 인증으로 바꾸면 이 두 줄을 함께 되돌려야 한다.
                 *
                 * (2026-08-18 16:20) accessToken이 다시 쿠키로 나가면서(AuthCookieFactory 참고)
                 * 위 경고가 실제로 유효해졌다 — CSRF 노출 범위가 refreshToken 전용 좁은 경로가
                 * 아니라 인증이 필요한 모든 API로 넓어졌다. SameSite=Strict가 대부분을 막아주지만
                 * 완전한 방어는 아니다. CSRF 토큰(더블서밋 쿠키 등) 도입 여부는 아직 결정되지
                 * 않았다 — 지금은 csrf(disable) 그대로 둔 채 넘어간다.
                 */
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, accessTokenValidAfterRepository),
                        UsernamePasswordAuthenticationFilter.class)
                // (2026-08-20, SEC-6-01/SEC-6-02) 로그인/재발급 레이트리밋. JwtAuthenticationFilter보다
                // 먼저 돌 필요는 없지만(어차피 permitAll 경로라 인증 여부와 무관) 순서를 하나로
                // 묶어두는 게 필터 체인을 훑을 때 더 읽기 쉽다.
                .addFilterBefore(
                        new AuthRateLimitFilter(redisTemplate),
                        UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 로그인/재발급은 토큰이 없거나 만료된 상태로 오는 요청이라 permitAll이어야 한다.
                        .requestMatchers(HttpMethod.POST, "/v1/auth/tokens", "/v1/auth/tokens:refresh").permitAll()
                        .requestMatchers("/v1/members/**").hasAuthority("TYPE_MEMBER")
                        .requestMatchers(HttpMethod.DELETE, "/v1/auth/tokens").hasAuthority("TYPE_MEMBER")
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

    // docs/api/auth.md: accessToken/refreshToken 둘 다 HttpOnly 쿠키로 오간다 — 프론트가
    // 백엔드와 다른 오리진(예: localhost:5173)이라 쿠키를 주고받으려면 allowCredentials(true)와
    // 프론트 오리진을 명시한 allowedOrigins가 필요하다(둘 다 없으면 브라우저가 쿠키를 안 보낸다).
    // allowedOrigins는 app.cors.allowed-origins(콤마 구분)에서 읽는다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
