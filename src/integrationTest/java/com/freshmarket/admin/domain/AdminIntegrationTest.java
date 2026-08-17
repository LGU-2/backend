package com.freshmarket.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.freshmarket.admin.domain.entity.Admin;
import com.freshmarket.admin.domain.entity.AdminRole;
import com.freshmarket.admin.domain.repository.AdminRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
 * Admin 엔티티가 실제 admin 테이블(V1__init_schema.sql, Flyway 가 이 컨테이너에 그대로 적용한다)과
 * 어긋나지 않는지 검증한다. 단위 테스트(AdminSessionServiceTest)는 AdminRepository 를 mock 으로
 * 갈아끼우기 때문에 이런 매핑 오류(예: PK 컬럼명이 id 가 아니라 admin_id 인데 놓친 경우)를 잡지 못한다.
 *
 * @DataJpaTest 를 쓰지 않는다. 이 프로젝트의 Boot 4.0.5 조합에서 그 애너테이션이 클래스패스에
 * 없었다 (원인 미확인, 로컬에서 재현됨). @SpringBootTest 는 존재가 확실하므로 이걸로 대신하고,
 * 자동 롤백이 없는 대가로 각 테스트 뒤에 직접 정리한다.
 */
@Testcontainers
@SpringBootTest
class AdminIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AdminRepository adminRepository;

    @AfterEach
    void cleanUp() {
        adminRepository.deleteAll();
    }

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
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);

        // when
        admin.issueRefreshToken("a".repeat(64), expiresAt);
        adminRepository.saveAndFlush(admin);

        // then
        Optional<Admin> reloaded =
                adminRepository.findByLoginId("integration.lee");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRefreshTokenHash())
                .isEqualTo("a".repeat(64));
    }
}