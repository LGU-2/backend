# 식별자 전략 리뷰 가이드

이 문서는 식별자 설계 규칙을 코드 리뷰 점검 항목으로 정리한 가이드다.
엔티티, 스키마, API 경로와 응답 DTO에 식별자가 추가되거나 변경되는 PR에 적용한다.

기준 스택은 Java, Spring, MySQL 8.4(InnoDB)이다.
각 항목이 왜 필요한지는 [identifier-strategy-rationale.md](./identifier-strategy-rationale.md)를 참고한다.
엔티티 PK의 베이스 규칙은 [base-entity-guideline.md](./base-entity-guideline.md)를 따르며, 이 문서는 그 위에 외부 노출 식별자를 얹을지와 어떻게 얹을지를 다룬다.

## 1. 세 가지 식별자 층

| 층 | 소비자 | 요구 조건 | 대표 타입 |
|---|---|---|---|
| 내부 | DB 엔진, 조인, FK | 작을 것, 순차적일 것 | `BIGINT UNSIGNED` |
| 외부 | 클라이언트, 타 서비스 | 추측 불가, 사전 채번 | UUID |
| 비즈니스 | 사람 (CS, 고객) | 짧고 읽을 수 있을 것 | 규칙 있는 문자열 |

세 층이 항상 다 필요하지는 않다. 필요한 층만 만들되 **하나의 컬럼이 두 층을 겸하게 하지 않는다.**

점검 항목
* `IDS-1-01` 하나의 컬럼이 두 층의 역할을 겸하지 않는가
  짧고 읽기 쉬우면 추측 가능하고, 추측 불가능하면 전화로 부를 수 없다. 요구 조건이 서로 모순된다.
* `IDS-1-02` 리소스가 여러 식별자를 가진다면 각각의 소비자와 용도가 문서에 명시되어 있는가

## 2. 구성 선택

```
1. 외부(클라이언트 API, 타 서비스, 외부 시스템)에 식별자가 노출되는가?
   아니오 -> 순수 BIGINT. 여기서 끝.
   예    -> 2번으로

2. 대량 벌크 INSERT 가 핵심 요구사항인가?
   예    -> 순수 UUID PK. IDENTITY 는 생성 키를 받아야 해서 JDBC 배칭이 꺼진다.
   아니오 -> 3번으로

3. 테이블당 세컨더리 인덱스가 많은가? (대략 3개 이상)
   예    -> 하이브리드. PK 가 모든 인덱스에 복사된다.
   아니오 -> 순수 UUID PK 도 무방.
```

점검 항목
* `IDS-2-01` 구성 선택의 근거가 기록되어 있는가
* `IDS-2-02` 새 엔티티가 아래 기준에 해당하는데 `public_id`를 누락하지 않았는가
* `IDS-2-03` 반대로 기준에 해당하지 않는데 습관적으로 달지 않았는가
  UNIQUE 인덱스 하나는 저장 공간과 삽입 비용을 모두 늘린다. 쓰이지 않으면 순손실이다.

외부 식별자를 다는 기준은 아래 중 하나라도 해당할 때다.

* 클라이언트 API의 URL 경로나 응답 본문에 식별자가 노출된다
* 외부 시스템(결제사, 물류사, 알림 채널)과 식별자를 주고받는다
* 향후 서비스 분리 시 다른 서비스가 이 리소스를 참조하게 된다
* 사용자가 링크를 공유하거나 북마크할 수 있다

실무상 **애그리거트 루트가 이 조건에 대응한다.**
하위 엔티티는 부모 식별자와 순번으로 도달할 수 있고(`/orders/{id}/items/3`), 이력 테이블은 목록으로만 조회되므로 단건 참조 대상이 아니다.

## 3. UUID 버전

**기본은 v7이다.** 해당 리소스가 응답 본문에 생성 시각을 이미 노출하고 있다면 v7을 쓴다.

아래에 해당하는 리소스만 v4를 쓴다.

* 생성 시각 자체가 민감 정보인 리소스 (제보, 인사 기록, 의료 기록, 감사 대상 문서)
* 응답 본문에 생성 시각을 포함하지 않기로 한 리소스
* 목록 응답에서 처리 순서나 배치 주기가 드러나면 곤란한 리소스

점검 항목
* `IDS-3-01` v4를 선택한 테이블에 사유가 기록되어 있는가
  사유가 없으면 v7이다. 기록이 없으면 다음 사람이 판단을 재현할 수 없다.
* `IDS-3-02` v1, v3, v5, v6, v8을 쓰지 않았는가
  v1과 v6은 MAC 주소가 박히고, v3과 v5는 난수가 0비트다. MySQL 내장 `UUID()`가 v1이다.
* `IDS-3-03` 외부 식별자로 조회할 때 소유권 또는 권한 검증을 함께 수행하는가
  추측 불가능성은 인가를 대체하지 않는다.
  일반적인 인가 판정은 common 저장소 `qa-security.md` 1장(`SEC-1-*`)이 소유한다. 이 항목은 그중 외부 식별자 경로에 한정한 재확인이다.

## 4. 난수원

**버전 선택보다 중요하다.** 난수원이 예측 가능하면 버전 논의 자체가 무의미해진다.

점검 항목
* `IDS-4-01` 난수원이 `SecureRandom`인가
  `java.util.Random`은 시드가 48비트뿐이라 몇 개의 출력만 관측해도 내부 상태가 복원된다. `ThreadLocalRandom`도 금지한다.
* `IDS-4-02` `SecureRandom.getInstanceStrong()`을 쓰지 않았는가
  리눅스에서 블로킹 엔트로피 소스에 연결될 수 있어 컨테이너 기동 직후 애플리케이션이 통째로 정지한다.
* `IDS-4-03` 외부 UUID 라이브러리를 도입했다면 난수원을 확인했는가
  일부 v7 구현체가 속도를 위해 rand_b를 비암호학적 PRNG로 채운다.
* `IDS-4-04` RFC 9562의 단조 카운터 옵션(Method 1, 2)이나 rand_a 서브밀리초 대체(Method 3)를 쓰지 않았는가

## 5. 생성 시점

점검 항목
* `IDS-5-01` 식별자가 `@PrePersist`나 ORM 생성기가 아니라 **생성자 인자로** 확정되는가
  persist 시점에 채워지면 저장 전까지 `null`이라 사전 채번의 이점이 사라지고, 테스트에서 값을 고정할 수 없다.
* `IDS-5-02` 생성을 `PublicIdGenerator` 인터페이스 뒤에 두었는가
  테스트 대역을 둘 수 있는 것이 이 인터페이스의 존재 이유다.
* `IDS-5-03` `@UuidGenerator`나 `GenerationType.UUID`를 쓰지 않았는가

```java
// 개선: 식별자를 생성자 인자로 받는다
@Transactional
public AccountPublicId register(String email) {
    Account account = new Account(publicIdGenerator.generate(), email);
    accountRepository.save(account);
    return account.getPublicId();
}
```

## 6. 스키마

```sql
CREATE TABLE account (
    account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id  BINARY(16)      NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_public_id (public_id)
) ENGINE=InnoDB;
```

| 규칙 | 근거 |
|---|---|
| 컬럼명은 전 프로젝트 공통 `public_id` | 이름이 갈리면 `@MappedSuperclass` 공통 매핑을 쓸 수 없다 |
| `BINARY(16)`, `CHAR(36)` 금지 | utf8mb4에서 최대 144바이트가 되어 인덱스가 9배가 된다 |
| `NOT NULL` | NULL을 허용하면 모든 조회 경로에 방어 코드가 생긴다 |
| FK는 내부 `BIGINT` 참조 | 16바이트 조인은 하이브리드를 채택한 이유를 무효화한다 |
| `UUID_TO_BIN` 스왑 플래그 미사용 | 스왑은 v1 교정 기능이다. v4와 v7에는 의미가 없다 |
| `DEFAULT (UUID())` 금지 | 사전 채번이 막히고 v1이 유입된다 |

점검 항목
* `IDS-6-01` `public_id`가 `BINARY(16)`이고 `NOT NULL`인가
* `IDS-6-02` `DEFAULT (UUID())`를 걸지 않았는가
* `IDS-6-03` FK와 조인 조건이 내부 식별자를 쓰는가

## 7. 계층별 사용 규칙

| 위치 | 사용할 식별자 | 근거 |
|---|---|---|
| API 경로, 요청/응답 본문 | 외부 | 내부 구조와 규모를 노출하지 않는다 |
| 서비스 간 이벤트 페이로드 | 외부 | 경계를 넘어도 의미가 유지된다 |
| 외부 시스템 연동 | 외부 | 유출 시 열거로 번지지 않는다 |
| 애플리케이션 로그 | 내부 | 짧고, 유출돼도 API 접근에 쓸 수 없다 |
| DB 조인, FK | 내부 | 8바이트 비교 |
| 배치, 대량 조회 | 내부 | UNIQUE에서 PK로 가는 2단계가 행 수만큼 반복된다 |

예외로 **저장 전 단계의 로그는 외부 식별자를 쓴다.** 내부 ID가 아직 없기 때문이다.

점검 항목
* `IDS-7-01` 응답 DTO나 API 경로에 내부 `Long id`가 없는가
  한 번 노출되면 클라이언트가 의존하기 시작해 되돌릴 수 없다.
* `IDS-7-02` 대량 조회 경로가 외부 식별자 목록으로 돌지 않는가
* `IDS-7-03` 응답 DTO의 필드명이 `publicId`가 아니라 `id`인가
  클라이언트에게는 그것이 유일한 식별자다. 다른 ID의 존재를 알릴 이유가 없다.

## 8. 타입과 예외

점검 항목
* `IDS-8-01` 외부 입력 UUID 파싱 실패가 400으로 매핑되는가
  `UUID.fromString`의 `IllegalArgumentException`을 그대로 두면 500이 나간다.
* `IDS-8-02` `AbstractPublicId` 하위 타입이 상태를 추가하지 않는가
  추가하면 `getClass()` 기반 equals 전제가 깨진다.

## 9. 정렬

점검 항목
* `IDS-9-01` UUID를 정렬 키나 커서 페이지네이션 키로 쓰지 않는가
  v7도 같은 밀리초 내 순서는 보장되지 않아 행이 건너뛰어진다. 정렬은 시각 컬럼과 PK 조합으로 한다.

## 10. 비즈니스 식별자

사람이 전화로 부르거나 종이에 적어야 하는 번호가 필요하면 외부 식별자와 별개로 만든다.

```
주문번호: 20260805-0001A
송장번호: 물류사 발급
```

점검 항목
* `IDS-10-01` 비즈니스 식별자를 API 식별자로 쓰지 않는가
  규칙이 있어 추측 가능하다. 조회 파라미터로는 쓸 수 있으나 그때도 소유권 검증이 선행되어야 한다.

## 11. 신규 프로젝트 도입 체크리스트

* [ ] 2절 판단 순서로 구성을 결정하고 근거를 남겼다
* [ ] 3절에 따라 기본 버전을 확정하고 v4 예외 대상을 식별했다
* [ ] 클라이언트 생성 식별자 허용 여부를 결정했다
* [ ] `common-core`에 생성기, 래퍼, `@MappedSuperclass`를 배치했다
* [ ] 2절 기준으로 적용 대상 테이블을 확정하고 ERD에 반영했다
* [ ] 비즈니스 식별자가 필요한 리소스와 채번 규칙을 정했다
* [ ] `FixedPublicIdGenerator` 등 테스트 대역을 준비했다

## 12. 참고

* RFC 9562, Universally Unique IDentifiers: https://www.rfc-editor.org/rfc/rfc9562.html
* RFC 4122 Section 6, Security Considerations: https://datatracker.ietf.org/doc/html/rfc4122
* MySQL 8.4, Miscellaneous Functions: https://dev.mysql.com/doc/refman/8.4/en/miscellaneous-functions.html
* MySQL 8.4, Online DDL Operations: https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html
