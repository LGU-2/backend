package com.freshmarket.config;

import com.freshmarket.common.auth.AuthRateLimitFilter;
import com.freshmarket.common.auth.jwt.AccessTokenValidAfterRepository;
import com.freshmarket.common.auth.jwt.JwtAuthenticationFilter;
import com.freshmarket.common.auth.jwt.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/*
 * 무엇을 열고 무엇을 막을지만 정한다.
 * 오류 응답의 구조는 GlobalExceptionHandler 가 혼자 소유한다.
 *
 * 회원 인증(JWT + 카카오 OAuth2)과 관리자 인증(JWT + 아이디/비밀번호)이 이 파일 하나에서 같이
 * 배선된다 (merge: feat/member-auth, feat/admin-login 두 브랜치를 develop에 합치면서 정리함).
 * 두 도메인 모두 같은 JwtTokenProvider/JwtAuthenticationFilter를 공유하고, type 클레임
 * ("MEMBER"/"ADMIN")과 role 클레임으로 인가를 구분한다 — 필터 체인을 도메인별로 쪼개지 않는다
 * (JwtAuthenticationFilter 참고). 액추에이터용 필터 체인(actuatorFilterChain, @Order(1))은
 * develop에 먼저 들어온 것으로, 8081 자식 컨텍스트에만 적용되고 이 배선과는 무관하다.
 *
 * docs/api/auth.md·member.md 기준 카카오 로그인은 프론트가 콜백(redirect_uri)을 직접 받아
 * code/state를 백엔드로 넘기는 구조라 Spring Security의 .oauth2Login(...) 필터 체인은 안 쓴다
 * (자세한 인과관계는 MemberLoginService 주석 참고) — 그래서 그 필터가 처리하던 리다이렉트/
 * 콜백 경로 매처가 없다. 대신 이 API가 쓰는 경로(/v1/auth/**, /v1/members/**, admin-auth-path)에
 * 맞춰 매처를 두고, 프론트가 다른 오리진에서 쿠키를 주고받을 수 있도록 CORS 빈을 둔다.
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
     * 관리자 인증 리소스 경로다 (auth.md: POST 로그인/재발급, DELETE 로그아웃이 이 하나를 공유한다).
     * 로그인(POST)은 인증 그 자체라 공개해야 한다.
     */
    private final String adminAuthPath;

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
            StringRedisTemplate redisTemplate,
            @Value("${admin.auth-path}") String adminAuthPath) {
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenValidAfterRepository = accessTokenValidAfterRepository;
        this.redisTemplate = redisTemplate;
        this.adminAuthPath = adminAuthPath;
    }

    /*
     * 액추에이터는 8081 로 분리되어 자식 컨텍스트로 뜬다.
     * 아래 기본 체인이 그 포트에 적용되지 않으므로 별도 체인이 필요하다.
     * 이것이 없으면 ALB 헬스체크와 Prometheus 스크랩이 401 을 받는다.
     *
     * 인증을 요구하지 않는 것은 경계가 네트워크에 있기 때문이다.
     * 8081 은 보안 그룹이 ALB 와 모니터링 인스턴스에게만 열어 둔다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /*
     * 리프레시 토큰과 accessToken 둘 다 HttpOnly 쿠키로 내려주므로 CSRF 보호를 켠다 (admin-login
     * 리뷰 반영, auth.md "토큰을 어떻게 전달하나" 절). 브라우저가 쿠키를 요청마다 자동으로 실어
     * 보내는 순간부터 그걸 노리는 위조 요청(CSRF)이 성립하는 공격 조건이 된다 — member 쪽
     * accessToken도 쿠키로 나가면서(AuthCookieFactory 참고) 노출 범위가 인증이 필요한 모든
     * API로 넓어져 있었다(merge 전 member-auth 브랜치는 이 이유로 csrf(disable) 상태로 "아직
     * 결정 안 됨"이라 남겨뒀었다 — admin-login이 먼저 정한 CSRF 활성화 방향으로 합친다).
     *
     * 1차 방어는 쿠키의 SameSite=Strict 다(대부분의 크로스사이트 요청 자체가 막힌다).
     * 그 위에 CSRF 토큰(더블 서브밋 쿠키, CookieCsrfTokenRepository)을 방어층으로 더 쌓는다 — 서버 세션이 없는 STATELESS 구성이라 기본 저장소(HttpSession)를 못 쓴다.
     *
     * 로그인(POST)만 예외로 둔다. 로그인 시점에는 아직 인증 쿠키 자체가 없어서 위조할 인증 상태가
     * 없다. 메서드까지 지정해서 예외를 좁힌 이유는, admin은 재발급/로그아웃이 로그인과 같은 경로
     * 문자열(adminAuthPath)을 쓰기 때문이다 — 경로만으로 예외를 주면 나중에 추가할 로그아웃
     * (DELETE, 쿠키를 실제로 소비하는 요청)까지 조용히 CSRF 검사에서 빠져 버린다. member의
     * POST /v1/auth/tokens(최초 로그인)도 같은 이유로 예외에 추가한다 — 재발급(:refresh)과
     * 로그아웃(DELETE)은 이미 발급된 쿠키를 쓰는 요청이라 CSRF 검사 대상에 그대로 남는다.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, adminAuthPath),
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/v1/auth/tokens")))
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
                        .requestMatchers(HttpMethod.POST, adminAuthPath).permitAll()
                        // (merge) admin-auth-path 아래 로그인(POST)만 공개하고, 그 경로를 포함해
                        // 나머지 관리자 API(재발급/로그아웃/향후 admin 리소스 전부)는 TYPE_ADMIN
                        // 권한을 요구한다 — admin-login 브랜치엔 아직 로그인 하나뿐이라 개별
                        // 경로 대신 "/v1/admin/**" 전체에 걸어둔다(SEC-1-04 기본값 거부와 동일한
                        // 취지). admin 쪽에 라우팅이 늘어나면 이 한 줄로 계속 커버된다.
                        .requestMatchers("/v1/admin/**").hasAuthority("TYPE_ADMIN")
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

    // 관리자 비밀번호 해싱 전용. 회원은 카카오에 인증을 위임하므로 비밀번호 자체가 없다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
