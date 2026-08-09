package com.freshmarket.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/* 공통 베이스가 실제 DB 에 어떻게 떨어지는지 확인한다.
   운영과 같은 mysql:8.4 를 띄운다. 인메모리 DB 는 컬럼 타입이 달라 이 검증이 성립하지 않는다. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class BaseEntityMappingTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    private String columnType(String column) {
        return (String) entityManager.createNativeQuery("""
                        SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'verification_sample'
                          AND COLUMN_NAME = :name
                        """)
                .setParameter("name", column)
                .getSingleResult();
    }

    @Test
    @DisplayName("public_id 는 AttributeConverter 없이 binary(16) 으로 생성된다")
    void publicIdIsBinary16() {
        assertThat(columnType("public_id")).isEqualToIgnoringCase("binary(16)");
    }

    @Test
    @DisplayName("columnDefinition 없이 맨 UUID 도 binary(16) 으로 떨어진다")
    void bareUuidIsBinary16() {
        assertThat(columnType("bare_uuid")).isEqualToIgnoringCase("binary(16)");
    }

    @Test
    @DisplayName("베이스가 id 와 시각 컬럼을 내려준다")
    void baseColumnsExist() {
        assertThat(columnType("id")).containsIgnoringCase("bigint");
        assertThat(columnType("created_at")).isNotBlank();
        assertThat(columnType("updated_at")).isNotBlank();
    }

    @Test
    @Transactional
    @DisplayName("ORM 이 INSERT 직전에 UUID v7 을 채운다")
    void ormFillsPublicIdWithVersion7() {
        VerificationSample sample = VerificationSample.of("sample");
        assertThat(sample.publicId()).isNull();

        entityManager.persist(sample);
        entityManager.flush();

        UUID generated = sample.publicId();
        assertThat(generated).isNotNull();
        assertThat(generated.version()).isEqualTo(7);
    }

    @Test
    @Transactional
    @DisplayName("QueryDSL 애노테이션 프로세서가 Q클래스를 만들고 조회가 동작한다")
    void querydslWorks() {
        entityManager.persist(VerificationSample.of("first"));
        entityManager.persist(VerificationSample.of("second"));
        entityManager.flush();

        QVerificationSample sample = QVerificationSample.verificationSample;
        List<VerificationSample> found = new JPAQueryFactory(entityManager)
                .selectFrom(sample)
                .where(sample.label.eq("first"))
                .fetch();

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getLabel()).isEqualTo("first");
    }
}
