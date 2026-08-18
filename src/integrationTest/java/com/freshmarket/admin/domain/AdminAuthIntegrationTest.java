package com.freshmarket.admin.domain;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.freshmarket.admin.domain.entity.Admin;
import com.freshmarket.admin.domain.entity.AdminRole;
import com.freshmarket.admin.domain.repository.AdminRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/*
 * AdminAuthController 를 실제 HTTP 요청으로 검증한다 (G-LOCAL UT-1-01 지적 반영).
 *
 * AdminAuthServiceTest(단위)는 서비스 로직만 본다. Set-Cookie 헤더의 실제 속성값이나
 * CSRF 필터가 정말 걸리는지는 SecurityConfig + 컨트롤러가 실제 필터 체인 위에서 맞물려야만
 * 드러난다 - mock 기반 단위 테스트로는 절대 검증할 수 없는 영역이다.
 *
 * @AutoConfigureMockMvc, SecurityMockMvcConfigurers 를 안 쓴다. 이 프로젝트의 Boot 4.0.5
 * 조합에서 둘 다 클래스패스에 없었다 (원인 미확인, @DataJpaTest 와 같은 부류). 대신
 * WebApplicationContext 로 MockMvc 를 직접 만들고, springSecurityFilterChain 빈을 손으로
 * 끼워 넣어 실제 보안 필터 체인을 태운다. 이 방식은 spring-security-test 의존성이 필요 없다.
 *
 * AdminIntegrationTest(레포지토리 계층)와는 검증 대상이 다르다. 이쪽은 웹 계층 + 보안 필터를 본다.
 */
@Testcontainers
@SpringBootTest
class AdminAuthIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    private static final String RAW_PASSWORD = "Freahman!2026";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-auth-path}")
    private String adminAuthPath;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @AfterEach
    void cleanUp() {
        adminRepository.deleteAll();
    }

    @Test
    void 로그인_성공_시_리프레시_토큰이_본문이_아니라_HttpOnly_쿠키로만_내려간다() throws Exception {
        // given
        adminRepository.save(Admin.register(
                "cookie.kim", passwordEncoder.encode(RAW_PASSWORD), "쿠키관리자", AdminRole.ADMIN));

        String body = """
                {"loginId":"cookie.kim","password":"%s"}
                """.formatted(RAW_PASSWORD);

        // when, then
        mockMvc.perform(post(adminAuthPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("refreshToken="),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=" + adminAuthPath))))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    /*
     * 로그인 자체는 CSRF 토큰 없이 통과해야 한다 (SecurityConfig 의 예외 대상).
     * adminAuthPath + POST 로 좁혀 둔 ignoringRequestMatchers 가 실제로 그렇게 동작하는지 본다.
     */
    @Test
    void 로그인_요청은_CSRF_토큰_없이도_통과한다() throws Exception {
        // given
        adminRepository.save(Admin.register(
                "csrf.exempt", passwordEncoder.encode(RAW_PASSWORD), "예외관리자", AdminRole.ADMIN));

        String body = """
                {"loginId":"csrf.exempt","password":"%s"}
                """.formatted(RAW_PASSWORD);

        // when, then
        mockMvc.perform(post(adminAuthPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /*
     * 같은 경로라도 POST 가 아닌 상태 변경 요청(로그아웃이 쓸 DELETE)은 CSRF 토큰이 없으면
     * 막혀야 한다. 로그아웃 엔드포인트 자체는 아직 없지만(별도 PR), CsrfFilter 는
     * DispatcherServlet 라우팅보다 먼저 동작하므로 핸들러가 없어도 CSRF 검사가 먼저 걸린다 -
     * 그래서 지금 시점에도 "로그인 외에는 CSRF 가 뚫려 있지 않다"를 검증할 수 있다.
     */
    @Test
    void 로그인이_아닌_상태_변경_요청은_CSRF_토큰이_없으면_거부된다() throws Exception {
        mockMvc.perform(delete(adminAuthPath))
                .andExpect(status().isForbidden());
    }
}