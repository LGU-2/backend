package com.freshmarket.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.Filter;
import java.time.LocalDateTime;
import java.util.Optional;
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
 * admin 도메인의 통합 테스트를 한 파일에 모은다 (도메인당 통합 테스트 파일 하나 원칙).
 *
 * 두 계층을 함께 검증한다.
 *  1) 레포지토리 계층 - Admin 엔티티가 실제 admin 테이블(V1__init_schema.sql)과 어긋나지
 *     않는지, DB CHECK 제약을 실제로 지키는지. 단위 테스트(AdminAuthServiceTest)는
 *     AdminRepository 를 mock 으로 갈아끼우므로 이런 매핑/제약 위반을 잡지 못한다.
 *  2) 웹 계층 - AdminAuthController + SecurityConfig 가 실제 필터 체인 위에서 맞물려
 *     Set-Cookie 속성과 CSRF 동작을 의도대로 내는지 (G-LOCAL UT-1-01 지적 반영).
 *
 * @AutoConfigureMockMvc, SecurityMockMvcConfigurers 를 안 쓴다. 이 프로젝트의 Boot 4.0.5
 * 조합에서 둘 다 클래스패스에 없었다 (원인 미확인, @DataJpaTest 와 같은 부류). 대신
 * WebApplicationContext 로 MockMvc 를 직접 만들고, springSecurityFilterChain 빈을 손으로
 * 끼워 넣어 실제 보안 필터 체인을 태운다. 이 방식은 spring-security-test 의존성이 필요 없다.
 */
@Testcontainers
@SpringBootTest
class AdminAuthIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    private static final String RAW_PASSWORD = "Freahman!2026";

    // DB 컬럼(refresh_token_hash CHAR(64))과 연결된 값이라 이름을 붙였다 (MNT-3-02)
    private static final int REFRESH_TOKEN_HASH_LENGTH = 64;

    // 테스트 데이터용 고정 시각. LocalDateTime.now() 를 직접 쓰면 실행 시각에 따라
    // 입력이 매번 달라져 결정성이 떨어진다 (MNT-2-03)
    private static final LocalDateTime FIXED_TEST_TIME = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

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

    // ===== 레포지토리 계층 =====

    @Test
    void 관리자를_저장하고_아이디로_조회한다() {
        // given
        Admin admin = Admin.register(
                "integration.kim",
                "$2a$10$dummyHashForIntegrationTestOnly",
                "통합관리자",
                AdminRole.ADMIN
        );

        // when
        adminRepository.save(admin);
        Optional<Admin> found = adminRepository.findByLoginId("integration.kim");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();   // admin_id 로 실제 채번됐는지, 매핑이 맞다는 증거다
        assertThat(found.get().getName()).isEqualTo("통합관리자");
        assertThat(found.get().getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void 존재하지_않는_아이디는_빈_값을_반환한다() {
        // when
        Optional<Admin> found = adminRepository.findByLoginId("no-such-login-id");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void 리프레시_토큰_발급이_실제로_영속화된다() {
        // given
        Admin admin = adminRepository.save(
                Admin.register(
                        "integration.lee",
                        "$2a$10$dummyHashForIntegrationTestOnly",
                        "통합관리자2",
                        AdminRole.SUPER_ADMIN
                )
        );
        LocalDateTime expiresAt = FIXED_TEST_TIME.plusDays(1);
        String tokenHash = "a".repeat(REFRESH_TOKEN_HASH_LENGTH);

        // when
        admin.issueRefreshToken(tokenHash, expiresAt);
        adminRepository.saveAndFlush(admin);
        entityManager.clear();   // 1차 캐시를 비워, 아래 조회가 실제로 DB 를 다시 읽게 한다 (UT-5-03)

        // then
        Optional<Admin> reloaded = adminRepository.findByLoginId("integration.lee");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRefreshTokenHash()).isEqualTo(tokenHash);
        assertThat(reloaded.get().getRefreshTokenExpiresAt()).isEqualTo(expiresAt);   // 만료시각도 저장됐는지 확인 (UT-2-01)
    }

    /*
     * chk_admin_deleted 제약을 실제 DB로 검증한다: status=DELETED 는 deleted_at IS NOT NULL,
     * refresh_token_hash IS NULL 과 항상 함께여야 한다. 세 컬럼 중 하나라도 어긋나면
     * MySQL 이 저장 자체를 거부한다 - Admin.deactivate() 가 셋을 함께 처리하지 않으면
     * 이 테스트가 SQL 예외로 실패한다. mock 기반 단위 테스트로는 이 제약을 검증할 수 없다.
     */
    @Test
    void 비활성화하면_상태와_리프레시_토큰이_DB_제약대로_함께_반영된다() {
        // given
        Admin admin = adminRepository.save(
                Admin.register(
                        "integration.park",
                        "$2a$10$dummyHashForIntegrationTestOnly",
                        "통합관리자3",
                        AdminRole.ADMIN
                )
        );
        admin.issueRefreshToken("b".repeat(REFRESH_TOKEN_HASH_LENGTH), FIXED_TEST_TIME.plusDays(1));
        adminRepository.saveAndFlush(admin);

        // when
        admin.deactivate(FIXED_TEST_TIME);
        adminRepository.saveAndFlush(admin);   // CHECK 제약을 어기면 여기서 예외가 난다
        entityManager.clear();   // 1차 캐시를 비워, 아래 조회가 실제로 DB 를 다시 읽게 한다 (UT-5-03)

        // then
        Optional<Admin> reloaded = adminRepository.findByLoginId("integration.park");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().isActive()).isFalse();
        assertThat(reloaded.get().getRefreshTokenHash()).isNull();
        assertThat(reloaded.get().getRefreshTokenExpiresAt()).isNull();
    }

    // ===== 웹 계층 =====

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