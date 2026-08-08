# 베이스 엔티티 점검 항목의 근거

이 문서는 [base-entity-guideline.md](./base-entity-guideline.md)의 점검 항목이 왜 필요한지를 구체적인 예시와 함께 설명한다.
가이드는 판정 기준만 담고, 이 문서는 그 기준이 나온 결정과 그 결정이 포기한 것을 다룬다.

베이스 엔티티가 존재하는 이유는 하나다. **PK와 시각 컬럼이 테이블마다 다르게 선언되면, 그 차이가 나중에 조회 코드와 마이그레이션에 전부 번진다.**

## 1. 계층 구조

### 왜 시각 축(수정 여부)으로 나누는가

한 번 저장되면 수정되지 않는 테이블(이력, 로그)에 `updated_at`을 두면 그 값이 영원히 `created_at`과 같다.
컬럼 하나와 그 컬럼을 채우는 Auditing 처리, 그리고 그 위에 걸릴 수 있는 인덱스가 모두 낭비된다.

더 중요한 것은 의미다. `updated_at`이 있는 테이블은 "수정될 수 있다"고 읽힌다.
append-only여야 할 이력 테이블에 그 신호가 붙으면, 나중에 누군가 이력 행을 수정하는 코드를 자연스럽게 작성한다.

```java
// 이력 테이블: updated_at 이 없어야 append-only 라는 의도가 드러난다
@Entity
@Table(name = "access_log")
public class AccessLog extends BaseImmutableTimeEntity {
    private String path;
}
```

### 왜 시각만 가진 중간 계층을 두지 않는가

초판은 `ImmutableTimeEntity`와 `MutableTimeEntity`를 `@MappedSuperclass`로 두고, 그 위에 PK를 더한 `Base*`를 얹었다.
`created_at` 정의를 재사용하려는 것이었고, 대신 **"직접 상속하면 안 되는 클래스"가 저장소에 둘 존재하게 됐다.**

그걸 막으려고 점검 항목을 하나 두어야 했다. 계층을 없애면 항목도 필요 없다.

```
전   ImmutableTimeEntity(상속 금지) -> BaseImmutableTimeEntity(상속 대상)
후   BaseImmutableTimeEntity 하나
```

대가는 `created_at` 선언이 네 곳에 중복되는 것이다. `@CreatedDate` 한 줄씩이라 감수한다.
**상속하면 안 되는 클래스가 존재하는 비용이 중복 네 줄보다 크다.**

### 왜 선언이 여러 곳에 중복되는가

`@Id @GeneratedValue`와 `@CreatedDate`가 여러 베이스에 각각 들어간다.

수정 여부와 외부 노출 여부 **두 축을 모두 상속으로 표현하는데 Java는 단일 상속만 된다.**
한 축을 상속으로 나누면 다른 축은 중복이 된다.

| 대안 | 문제 |
|------|------|
| id를 최상위에 두고 나머지를 아래로 | 축이 둘이라 한쪽으로만 나뉜다 |
| 인터페이스로 id 분리 | `@Id`는 필드에 붙어야 해 인터페이스로 못 옮긴다 |
| 여러 곳에 중복 선언 (채택) | 어노테이션 몇 줄이 중복된다 |

`BasePublic*`은 `Base*`를 상속하므로 `public_id`는 두 곳에만 들어간다.

### 왜 베이스가 UUID를 들고 타입 래퍼는 하위가 씌우는가

`AbstractPublicId` 하위 타입은 엔티티마다 다르다. 베이스가 그 타입을 직접 들려면 둘 중 하나다.

| 방식 | 문제 |
|------|------|
| 제네릭 베이스 `BasePublic...<ID extends AbstractPublicId>` | JPA가 제네릭 필드를 매핑하려면 **엔티티마다 `AttributeConverter`**가 필요하다 |
| 베이스가 `UUID`를 들고 하위가 래핑 (채택) | 변환기가 하나. 하위는 `getPublicId()` 한 줄 |

`publicIdValue()`를 `protected`로 두어 **밖으로는 타입이 붙은 것만 나가게** 한다.

### 왜 베이스에 생성자가 없는가

`public_id`를 ORM 이 INSERT 직전에 채우기 때문이다. 하위 엔티티가 값을 넘길 일이 없다.

초판은 생성자 인자로 받아 사전 채번을 얻으려 했으나, 애그리거트 루트를 만들 때마다 서비스가 생성기를 호출해야 했다.
**사전 채번을 포기하고 호출을 없앴다.** 판단 근거는 `identifier-strategy-guideline.md` 5.1절에 있다.

## 2. PK 규칙

### 왜 bigint로 통일하는가

코드 테이블만 문자열 PK를 쓰면 **"코드 테이블은 예외"라는 규칙이 하나 생긴다.**
그 예외는 베이스 상속에서 시작해 조인 코드, 마이그레이션 스크립트, 매핑 설정으로 번진다.

정수 PK로 통일하면 코드 테이블도 공통 베이스를 그대로 상속해 전 테이블이 하나의 PK 규칙을 따른다.
대가는 코드값을 읽을 때 조인이 필요하다는 것인데, 코드 테이블은 행이 적고 값이 거의 안 바뀌어 캐싱으로 대부분 제거된다.

```sql
-- 코드 테이블 조인. 등급 3~5행이므로 캐싱으로 대부분 제거된다
SELECT u.*, g.name
FROM users u
JOIN grade g ON u.grade_id = g.id;
```

### 왜 IDENTITY 전략인가

`Long`은 MySQL 8.4의 `BIGINT`에 매핑되고 `IDENTITY`는 `AUTO_INCREMENT`를 쓴다.
PK가 순차적으로 증가해 B-tree 오른쪽 끝에만 삽입되므로 페이지 분할이 발생하지 않는다.

InnoDB는 PK를 모든 세컨더리 인덱스에 복사하므로, PK 크기가 인덱스 수만큼 곱해져 저장 공간에 영향을 준다.
외부 노출용 식별자가 별도로 필요한 경우의 판단은 [identifier-strategy-guideline.md](./identifier-strategy-guideline.md)를 따른다.

## 3. 시각 컬럼과 Auditing

### 왜 코드에서 시각을 직접 넣지 않는가

직접 대입하면 생성 경로마다 값이 달라진다.
정적 팩터리가 여럿이면 그중 하나는 대입을 빠뜨리고, 배치나 테스트가 만드는 엔티티는 또 다른 시각을 갖는다.

```java
// 점검 대상: 생성 경로마다 시각 대입이 흩어진다
public static Order place(Long memberId) {
    Order o = new Order(memberId);
    o.createdAt = LocalDateTime.now();   // 다른 팩터리에서 빠지면 null
    return o;
}
```

Auditing에 맡기면 대입 지점이 프레임워크 한 곳으로 모여 어떤 경로로 만들어도 값이 채워진다.

### 왜 @EnableJpaAuditing 확인이 점검 항목인가

**빠뜨려도 컴파일이 되고 기동도 된다.** `@CreatedDate`가 조용히 동작하지 않아 시각이 `null`로 저장될 뿐이다.

`nullable = false`가 걸려 있으면 저장 시점에 제약 위반으로 드러나지만, 그때는 이미 운영 중일 수 있다.
설정 한 줄의 부재가 런타임에야 나타나는 유형이라 리뷰에서 명시적으로 확인한다.

```java
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

### 왜 created_at에 updatable = false를 두는가

생성 시각은 최초 저장 이후 바뀔 이유가 없다.
`updatable = false`가 없으면 엔티티를 수정할 때마다 UPDATE 문에 `created_at`이 포함되어, 코드 어딘가에서 값을 덮어쓰면 그대로 저장된다.

`LocalDateTime`은 MySQL 8.4에서 `DATETIME(6)`으로 매핑되어 마이크로초까지 저장된다.

### 왜 EntityListeners를 최상위에만 붙이는가

`@EntityListeners(AuditingEntityListener.class)`는 하위 클래스가 상속받는다.
하위에 중복 선언하면 리스너가 두 번 등록되어 동작이 불명확해지고, 어느 선언이 유효한지 읽는 사람이 판단할 수 없게 된다.

## 4. 코드 테이블

### 왜 기본이 enum인가

코드성 값은 대개 값의 종류가 고정적이고, 개발자가 배포로 관리하며, 값에 로직이 딸린다.
이 경우 테이블의 "행만 추가하면 된다"는 유연성이 쓸모없다. **새 값에 로직이 딸리므로 어차피 코드를 고쳐야 하기 때문이다.**

enum은 별도 테이블, FK, 조인, 캐싱이 모두 사라지고 타입 안전성까지 얻는다.
상세한 비교는 [entity-creation-rationale.md](./entity-creation-rationale.md)의 코드성 값 절에 있다.

### 왜 코드 테이블이 Mutable 계열인가

코드 테이블의 부가 속성(표시명 등)은 운영 중에 수정된다.
`code`는 안 바뀌어도 `name`은 바뀌므로 `updated_at`이 의미를 갖는다.

### 왜 code에 UNIQUE가 필요한가

PK가 `id`이므로 `code` 중복을 DB가 막지 않는다.
`findByCode`가 코드값 조회 경로인데 중복이 들어오면 조회 결과가 둘이 되어 어느 쪽이 맞는지 알 수 없다.

## 5. 문자열 PK 예외

### 왜 세 조건을 모두 요구하는가

문자열 PK의 이점은 하나뿐이다. **참조 측이 값을 직접 가져 조인이 사라진다.**

이 이점이 실제 가치를 가지려면 조인 비용이 실제로 문제여야 하고, 그러려면 대량 참조와 캐싱 곤란이 함께 성립해야 한다.
셋 중 하나라도 빠지면 PK 통일을 깨는 대가만 치르고 얻는 것이 없다.

| 조건 | 빠졌을 때 |
|------|-----------|
| 운영자 관리 요구 | 테이블 자체가 불필요. enum이 답이다 |
| 대량 참조 | 조인 비용이 미미해 최적화할 대상이 아니다 |
| 캐싱 곤란 | 캐싱으로 조인이 사라져 문자열 PK가 불필요하다 |

등급이나 카테고리 같은 소형 코드 테이블은 두 번째가 맞지 않는다.
수천 개 지역 코드를 수백만 행이 참조하는 수준이라야 세 조건이 함께 성립한다.

### 왜 사유를 PR에 남기게 하는가

예외를 허용하되 흔적을 남기지 않으면, 다음 사람이 그 테이블을 보고 "여기는 이렇게 해도 되는구나"라고 판단해 예외가 번진다.
사유가 적혀 있으면 자신의 테이블이 그 조건에 해당하는지 스스로 대조하게 된다.

## 6. 다루지 않는 것

**엔티티 생성 규칙.** 생성자, 정적 팩터리, 검증 등 인스턴스를 만드는 방식은 [entity-creation-guideline.md](./entity-creation-guideline.md)를 따른다.
베이스 엔티티는 뼈대(PK, 시각)만 제공한다.

**외부 노출 식별자.** `public_id` 같은 외부용 식별자가 필요한지와 그 설계는 [identifier-strategy-guideline.md](./identifier-strategy-guideline.md)가 다룬다.
