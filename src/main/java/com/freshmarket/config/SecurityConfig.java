package com.freshmarket.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
	        "/swagger-ui.html",
	        "/v1/products",
	};

    /*
     * 필터 체인에서 난 예외를 MVC 예외 처리로 되돌린다.
     * 이렇게 하지 않으면 인증 실패 응답만 여기서 따로 만들게 되어 오류 구조가 두 곳으로 갈린다.
     */
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
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

    @Bean
    @Order(2)
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
}
