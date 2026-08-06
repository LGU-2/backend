# 베이스 엔티티 리뷰 가이드

이 문서는 JPA 엔티티가 공유하는 베이스 클래스 계층 규칙을 코드 리뷰 점검 항목으로 정리한 가이드다.
`@Entity` 또는 `@MappedSuperclass`가 붙은 클래스가 추가되거나 변경되는 PR에 적용한다.

기준 스택은 Java, Spring Data JPA, MySQL 8.4, Lombok이다.
각 항목이 왜 필요한지는 [base-entity-rationale.md](./base-entity-rationale.md)를 참고한다.
엔티티의 인스턴스 생성 방식은 [entity-creation-guideline.md](./entity-creation-guideline.md)가 다루며, 이 문서는 그 엔티티들이 공통으로 물려받는 뼈대만 본다.

## 1. 계층 구조

모든 실제 엔티티는 아래 둘 중 하나를 상속한다.

| 클래스 | PK | created_at | updated_at | 상속 대상 |
|--------|:--:|:----------:|:----------:|-----------|
| `BaseImmutableTimeEntity` | Long | O | X | 이력, 로그 (append-only) |
| `BaseMutableTimeEntity` | Long | O | O | 일반 도메인 엔티티, 코드 테이블 |

`ImmutableTimeEntity`와 `MutableTimeEntity`는 시각 컬럼만 제공하는 `@MappedSuperclass`이며 직접 상속하지 않는다.

점검 항목
* `BE-1-01` 모든 `@Entity`가 `BaseImmutableTimeEntity` 또는 `BaseMutableTimeEntity`를 상속하는가
* `BE-1-02` 수정되지 않는 테이블(이력, 로그)에 `BaseMutableTimeEntity`를 쓰지 않았는가
  `updated_at`이 영원히 `created_at`과 같은 값으로 남아 컬럼과 인덱스가 낭비된다.
* `BE-1-03` 시각 계층(`ImmutableTimeEntity`, `MutableTimeEntity`)을 엔티티가 직접 상속하지 않았는가
  PK가 없는 계층이라 상속하면 `@Id`를 엔티티마다 손으로 선언하게 된다.

```java
// 점검 대상: 베이스를 상속하지 않고 id 와 시각을 직접 선언
@Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime createdAt;
}

// 개선: 베이스 상속으로 뼈대를 통일
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseMutableTimeEntity {
    private String email;
}
```

## 2. PK 규칙

점검 항목
* `BE-2-01` PK가 `Long`(MySQL `BIGINT`)인가
* `BE-2-02` `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 쓰는가
* `BE-2-03` 코드 테이블도 정수 PK를 쓰는가
* `BE-2-04` 문자열 PK를 쓴다면 5절의 예외 조건과 사유가 PR에 있는가

## 3. 시각 컬럼과 Auditing

생성과 수정 시각은 JPA Auditing이 채운다. 코드에서 직접 넣지 않는다.

점검 항목
* `BE-3-01` 생성 시각을 `LocalDateTime.now()`로 직접 대입하지 않는가
* `BE-3-02` `created_at`에 `updatable = false`가 지정되어 있는가
* `BE-3-03` 설정 클래스에 `@EnableJpaAuditing`이 한 번 선언되어 있는가
  선언이 없으면 `@CreatedDate`가 조용히 동작하지 않아 시각이 `null`로 저장된다.
* `BE-3-04` `@EntityListeners(AuditingEntityListener.class)`를 하위 클래스에 중복 선언하지 않았는가

```java
// 점검 대상: 시각을 코드에서 직접 채움
this.createdAt = LocalDateTime.now();

// 개선: 베이스의 Auditing 에 맡긴다
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

## 4. 코드 테이블

코드성 값의 기본은 enum이다. 운영자가 값 목록을 화면에서 관리해야 하는 경우에만 테이블로 둔다.
판단 기준은 [entity-creation-guideline.md](./entity-creation-guideline.md)의 코드성 값 절을 따른다.

점검 항목
* `BE-4-01` 테이블로 만들 근거(운영자의 런타임 관리 요구)가 있는가
  근거가 없으면 enum이어야 한다.
* `BE-4-02` 코드 테이블이 `BaseMutableTimeEntity`를 상속하는가
* `BE-4-03` `code` 컬럼에 UNIQUE 제약이 있는가
* `BE-4-04` 코드값 조회 경로(`findByCode`)와 캐싱이 준비되어 있는가

```java
// 개선: 정수 PK + code UNIQUE
@Entity
@Table(name = "grade",
    uniqueConstraints = @UniqueConstraint(name = "uk_grade_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grade extends BaseMutableTimeEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;
}
```

## 5. 문자열 PK 예외

문자열 코드를 PK로 두는 것은 표준이 아니라 예외다. 아래 세 조건이 **모두** 성립할 때만 허용한다.

1. 운영자가 값 목록을 관리해야 한다 (그래서 enum이 아니라 테이블)
2. 그 값이 대량으로 참조된다 (그래서 조인 비용이 실제 문제가 된다)
3. 캐싱으로도 조인을 없애기 어렵다

점검 항목
* `BE-5-01` 세 조건이 모두 성립하는가
  등급이나 카테고리 같은 소형 코드 테이블은 2번이 맞지 않아 해당하지 않는다.
* `BE-5-02` 베이스 미상속 사유가 PR에 기재되어 있는가
* `BE-5-03` 시각 컬럼을 직접 선언했는가
  베이스를 상속하지 않으므로 `created_at`과 `updated_at`이 자동으로 생기지 않는다.

## 6. 상속 선택 기준

| 테이블 성격 | 상속 대상 |
|-------------|-----------|
| 일반 도메인 엔티티 (회원, 주문, 상품) | `BaseMutableTimeEntity` |
| 운영 관리 코드 테이블 (등급, 카테고리) | `BaseMutableTimeEntity` (id + code UNIQUE) |
| 이력, 로그, append-only | `BaseImmutableTimeEntity` |
| 코드성 값 중 운영 관리가 불필요한 것 | 테이블을 만들지 않고 enum |
| 대량 참조 + 캐싱 곤란한 특수 코드 테이블 | 문자열 PK 예외 (베이스 미상속, 사유 기재) |

## 7. 참고

- Spring Data JPA Auditing: https://docs.spring.io/spring-data/jpa/reference/auditing.html
- Hibernate ORM User Guide: https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html
