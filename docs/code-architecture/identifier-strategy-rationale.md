# 식별자 전략 점검 항목의 근거

이 문서는 [identifier-strategy-guideline.md](./identifier-strategy-guideline.md)의 점검 항목이 왜 필요한지를 구체적인 예시와 함께 설명한다.
원칙의 출처는 RFC 9562와 RFC 4122이며, 이 문서의 설명은 모두 새로 작성한 것이다.

식별자 설계에서 반복되는 오류는 하나다. **근거 없이 좋다고 알려진 선택을 그대로 가져오는 것.**
아래 근거는 각 선택이 성립하는 범위와 성립하지 않는 범위를 함께 명시한다.

## 1. 세 가지 식별자 층

### 왜 한 컬럼이 두 층을 겸하면 안 되는가

요구 조건이 서로 모순되기 때문이다.

| 층 | 요구 조건 | 그 조건이 필요한 이유 |
|---|---|---|
| 내부 | 작을 것 | InnoDB는 PK를 모든 세컨더리 인덱스에 복사한다 |
| | 순차적일 것 | B-tree 오른쪽 끝에만 삽입되어 페이지 분할이 없다 |
| 외부 | 추측 불가 | 인가 누락 시 피해가 전수 유출로 번지지 않는다 |
| | 사전 채번 | 저장 전에 외부와 식별자를 공유할 수 있다 |
| 비즈니스 | 짧고 읽을 수 있을 것 | 전화로 부르고 종이에 적는 값이다 |

짧고 읽기 쉬우면 추측 가능하고, 추측 불가능하면 전화로 부를 수 없다.
한 값으로 둘을 만족시키려 하면 양쪽 다 어중간해진다.

## 2. 구성 선택

### 왜 두 축을 섞지 않는가

**쓰기 처리량**은 배칭 가능 여부가 결정하고, **저장 공간과 캐시 효율**은 인덱스 개수가 결정한다.
둘의 답이 다를 수 있으므로 하나의 판단으로 뭉뚱그리면 잘못된 구성이 나온다.

| 항목 | 순수 BIGINT | 순수 UUID PK | 하이브리드 |
|---|---|---|---|
| PK 크기 | 8바이트 | 16바이트 | 8바이트 |
| 세컨더리 인덱스 부담 | 낮음 | 높음 (PK가 전부에 복사됨) | 낮음 + UNIQUE 1개 |
| 열거 공격 방어 | 없음 | 있음 | 있음 |
| INSERT 전 채번 | 불가 | 가능 | 가능 |
| JDBC 배치 INSERT | 불가 | 가능 | 불가 |
| 외부 조회 경로 | PK 직접 | PK 직접 | UNIQUE에서 PK로 2단계 |
| 식별자 혼동 위험 | 없음 | 없음 | **있음** |

### 왜 하이브리드가 배치 INSERT를 못 하는가

Hibernate는 `GenerationType.IDENTITY`에서 DB가 생성한 키를 즉시 받아야 하므로 INSERT를 하나씩 실행한다.
여러 문을 모아 보내는 JDBC 배칭이 원천적으로 불가능하다.

**하이브리드는 내부 PK가 IDENTITY이므로 이 제약을 그대로 갖는다.**
사전 채번은 `public_id`에만 적용되며 쓰기 처리량과 무관하다. 이 점을 혼동하면 하이브리드를 성능 개선책으로 잘못 도입하게 된다.

### 왜 모든 테이블에 외부 식별자를 달지 않는가

UNIQUE 인덱스 하나는 저장 공간과 삽입 비용을 모두 늘린다. **쓰이지 않는 컬럼에 다는 것은 순손실이다.**

나중에 노출이 필요해지면 그때 추가한다. 미리 다 다는 것은 쓰지 않을 인덱스를 미리 지불하는 것이다.

## 3. 사전 채번의 성립 범위

외부 식별자를 DB가 아니라 애플리케이션이 만드는 이유다. 근거 없이 반복되기 쉬운 주장이므로 성립 범위를 명시한다.

### 먼저, 과장하지 않는다

`GenerationType.IDENTITY`에서도 `save()`를 호출하면 Hibernate가 즉시 INSERT를 실행하고 생성된 키를 엔티티에 채운다.
**INSERT 자체 외에 추가 왕복은 없다.** "ID를 얻으려면 매번 DB 왕복이 강제된다"는 서술은 부정확하다.

따라서 이점은 `save()` 이후가 아니라, **INSERT를 아직 할 수 없거나 하면 안 되는 시점에 식별자가 필요한 경우**에만 성립한다.

### 성립 1: 외부 시스템을 먼저 호출해야 하는 흐름

결제 준비, 외부 문서 발급, 서드파티 세션 생성처럼 우리 DB에 확정 저장하기 전에 외부에 식별자를 넘겨야 하는 흐름이 있다.

```java
// 사전 채번 없음: 저장이 선행되어 유령 행이 남는다
@Transactional
public String prepare(PrepareCommand command) {
    Order order = orderRepository.save(new Order(command));
    return pgClient.requestPayment(order.getId(), command.amount());
    // 사용자가 결제창을 닫으면 주문 행만 남는다
}

// 사전 채번: 식별자만 확정하고 저장은 승인 이후로 미룬다
public String prepare(PrepareCommand command) {
    OrderPublicId orderId = OrderPublicId.of(publicIdGenerator.generate());
    paymentSessionStore.put(orderId, command, Duration.ofMinutes(15));
    return pgClient.requestPayment(orderId.toString(), command.amount());
}
```

부수 효과로 외부 시스템이 아는 식별자와 우리 식별자가 같아져 대사(reconciliation)의 조인 키가 하나로 정리된다.

**대가가 있다.** 세션 스토어라는 인프라가 하나 늘고 만료 처리 책임이 생긴다. 미완료 행이 쌓이는 비용과 비교해 판단한다.

### 성립 2: 실패한 시도의 추적성

```java
public OrderPublicId place(PlaceCommand command) {
    OrderPublicId orderId = OrderPublicId.of(publicIdGenerator.generate());
    log.info("order attempt started. orderId={}", orderId);

    try {
        orderService.saveInTransaction(orderId, command);
    } catch (BusinessException e) {
        log.warn("order failed. orderId={}, reason={}", orderId, e.getMessage());
        throw new OrderFailedException(orderId, e);   // 응답에도 같은 값을 싣는다
    }
    return orderId;
}
```

IDENTITY로는 롤백된 시도를 가리킬 값이 없다.
AUTO_INCREMENT 값은 롤백돼도 소비되지만 애플리케이션이 그 값을 알지 못하고 해당 행도 존재하지 않는다.

**실패 케이스의 추적성은 성공 케이스보다 CS에서 더 자주 필요하다.**
성공한 건은 화면에 번호가 떠 있지만 실패한 건은 아무 흔적이 없기 때문이다.

### 성립 3: 클라이언트 생성 식별자 (선택)

클라이언트가 식별자를 만들어 보내면 POST 재시도 멱등성을 UNIQUE 제약 하나로 해결할 수 있다.
네트워크 타임아웃으로 재전송돼도 두 번째 INSERT가 제약 위반으로 튕긴다.

**서버 생성과 배타적인 선택이며 아래를 감수해야 한다.**

* 형식과 버전 검증을 서버가 해야 한다. v4나 v7이 아닌 값, nil UUID, 순차 값이 들어올 수 있다
* 악의적 클라이언트가 값을 지정할 수 있다. 선점 시도를 UNIQUE 위반으로 처리하면 **해당 식별자의 존재 여부가 응답으로 새어 나간다**
* 클라이언트가 시각을 조작한 v7을 보내면 시간 정렬 전제가 깨진다

프로젝트 단위로 명시적으로 결정하고 문서에 남긴다.

### 성립하지 않는 것

**쓰기 처리량은 개선되지 않는다.** 내부 PK가 IDENTITY인 한 배칭은 꺼져 있다.

**이벤트 페이로드 조립은 이점이라 부르기 어렵다.** `save()`가 flush를 유발하므로 그 뒤에 조립하면 된다.
순서 제약이 사라지는 편의가 전부다.

## 4. UUID 버전 선택

### 왜 v1, v3, v5, v6, v8을 배제하는가

| 버전 | 배제 사유 |
|---|---|
| v1, v6 | 노드 ID에 MAC 주소가 들어가고 100ns 타임스탬프가 붙어, 식별자 하나에서 어느 장비가 언제 만들었는지 복원된다. **MySQL 내장 `UUID()`가 v1이다.** |
| v3, v5 | 이름 기반 해시라 난수가 0비트다. 이메일처럼 추측 가능한 값을 입력으로 쓰면 누구나 계산할 수 있어 열거 방어가 순차 정수보다 나빠진다. |
| v8 | 자유 형식이라 커스텀 레이아웃을 만들 수 있으나 비트를 난수에서 빼앗아 온다. 엔트로피 계산이 선행되어야 한다. |

v5는 **외부 자연키를 UUID 공간으로 사상하는 결정론적 멱등 키** 용도로는 허용한다.
이 경우 추측 가능성이 문제가 아니라 재현성이 목적이기 때문이다.

### 왜 엔트로피와 생성 속도가 판단 근거가 아닌가

| 항목 | v4 | v7 |
|---|---|---|
| 난수 비트 | 122 | 74 |
| 삽입 지역성 | 무작위 (페이지 분할 잦음) | 시간순 (B-tree 오른쪽 끝) |
| 노출되는 부가 정보 | 없음 | 생성 시각 (밀리초) |

**엔트로피는 판단 근거가 되지 못한다.** 74비트는 약 1.9 x 10의 22승이다.
초당 10억 회를 시도해도 60만 년이 걸린다. 122비트가 수학적으로 크지만 둘 다 브루트포스가 불가능한 영역이라 차이가 결과를 바꾸지 않는다.

**생성 속도도 판단 근거가 되지 못한다.** v7이 6바이트 적게 요구하지만 `SecureRandom.nextBytes` 호출 1회의 오버헤드가 바이트 수보다 크다.
DB 왕복 한 번이 수 밀리초인데 생성은 마이크로초 이하이므로 요청 처리 시간 기여도가 사실상 0이다.

**실질 차이는 두 가지뿐이다.** v7은 UNIQUE 인덱스에 순차 삽입되어 페이지 분할이 줄고, 생성 시각을 밀리초 단위로 공개한다.

```java
/** v7 에서 생성 시각을 뽑아내는 데 필요한 코드 전부 */
public static Instant extractTimestamp(UUID uuid) {
    return Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
}
```

### 왜 기본이 v7인가

**해당 리소스가 응답 본문에 생성 시각을 이미 노출하고 있다면 시각 노출이 비용이 아니다.**
이미 주는 정보를 식별자로도 준다고 해서 새로 잃는 것이 없으므로 인덱스 지역성이라는 순이득만 남는다.

대부분의 리소스가 여기 해당하므로 실질적 기본값은 v7이다.

v4를 쓰는 세 경우 중 **목록 응답에서 처리 순서가 드러나는 경우가 놓치기 쉽다.**
목록 API가 20건의 식별자를 반환하면 클라이언트는 그 20건의 생성 간격을 알 수 있다.
`createdAt`을 초 단위로 절삭해 내보내더라도 식별자에는 밀리초가 그대로 남아 **절삭 의도가 무력화된다.**

### 왜 어떤 버전도 인가를 대체하지 않는가

RFC 4122 이래 유지되는 원칙이다.
UUID가 추측하기 어렵다고 가정하지 말 것이며, 소지만으로 접근 권한이 부여되는 보안 능력으로 사용해서는 안 된다.

`public_id`는 Referer 헤더, 브라우저 히스토리, 접근 로그, 스크린샷으로 유출된다.
**유출 경로가 추측 난이도와 무관하므로** 소유권 검증은 버전과 상관없이 필수다.

```java
@Transactional(readOnly = true)
public AccountResponse get(AccountPublicId publicId, Long requesterId) {
    Account account = accountRepository.findByPublicId(publicId.value())
            .orElseThrow(() -> new AccountNotFoundException(publicId));

    // 추측 불가능성은 인가를 대체하지 않는다.
    if (!account.isAccessibleBy(requesterId)) {
        throw new AccessDeniedException("access denied");
    }
    return AccountResponse.from(account);
}
```

## 5. 난수원 규칙

### 왜 버전 선택보다 중요한가

난수원이 예측 가능하면 v4의 122비트든 v7의 74비트든 전부 계산 가능해져 **버전 논의 자체가 무의미해진다.**

RFC 9562는 예측이 어렵고 충돌 가능성이 낮은 값을 위해 CSPRNG 사용을 권고하며, 프로세스 fork 같은 상태 변화 시 재시드에 주의하라고 명시한다.

### 왜 getInstanceStrong()을 쓰지 않는가

리눅스에서 블로킹 엔트로피 소스에 연결될 수 있고, 엔트로피 풀이 마르면 스레드가 멈춘다.
**컨테이너 기동 직후 애플리케이션이 통째로 정지하는 사고가 여기서 나온다.**

식별자는 장기 비밀이 아니므로 기본 `new SecureRandom()`으로 충분하다.

### 왜 RFC의 단조 카운터 옵션을 쓰지 않는가

RFC 자신이 증가값 1인 카운터는 결과값을 쉽게 추측할 수 있게 하므로, 추측 불가능성을 중시하는 구현체는 쓰지 말아야 한다고 명시한다.
**정렬은 시각 컬럼의 일이지 식별자의 일이 아니다.**

rand_a를 서브밀리초로 대체하는 옵션도 쓰지 않는다.
난수가 74에서 62비트로 줄고 시각 노출 정밀도가 약 4배 올라가 버전 선택의 판단과 반대로 간다.

## 6. 구현

### UUIDv7 생성기

JDK에는 UUIDv7 생성 API가 없다. `java.util.UUID`는 128비트 컨테이너로만 쓰고 비트 배치는 직접 한다.

```java
/**
 * RFC 9562 UUIDv7 생성기.
 *
 * 비트 배치:
 *   [0..47]   Unix epoch 밀리초 (48비트)
 *   [48..51]  version = 7 (4비트)
 *   [52..63]  rand_a (12비트)
 *   [64..65]  variant = 10 (2비트)
 *   [66..127] rand_b (62비트)
 */
public final class UuidV7 {

    // SecureRandom 은 스레드 안전하지만 내부 동기화 방식이라
    // 공유 인스턴스는 요청 스레드가 늘어날 때 락 경합 지점이 된다.
    private static final ThreadLocal<SecureRandom> RANDOM =
            ThreadLocal.withInitial(SecureRandom::new);

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    /** 타임스탬프를 주입받는 오버로드. 테스트에서 결정론적 검증에 사용한다. */
    public static UUID generate(long epochMilli) {
        byte[] bytes = new byte[10];
        RANDOM.get().nextBytes(bytes);

        long msb = (epochMilli & 0xFFFF_FFFF_FFFFL) << 16;      // 48비트 타임스탬프
        msb |= 0x7000L;                                         // version = 7
        msb |= ((bytes[0] & 0x0FL) << 8) | (bytes[1] & 0xFFL);  // rand_a (난수 유지)

        long lsb = 0L;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;                          // 상위 2비트 제거
        lsb |= 0x8000_0000_0000_0000L;                          // variant = 10

        return new UUID(msb, lsb);
    }
}
```

**가상 스레드 환경이면 `ThreadLocal`을 재검토한다.** 수만 개가 생길 수 있어 스레드당 인스턴스가 메모리 부담이 된다.

**알려진 한계.** 같은 밀리초 안에서 생성된 값 사이에는 순서 보장이 없다.
시계가 되감기면 이전 밀리초 값이 재사용될 수 있으나 74비트 난수가 있어 충돌하지 않는다.

### 왜 생성을 인터페이스 뒤에 두는가

**테스트 대역을 둘 수 있는 것이 이 인터페이스의 존재 이유다.**
엔티티 생성자가 `UuidV7.generate()`를 직접 호출하면 값을 고정할 수 없어, 식별자가 응답이나 로그에 실리는 경로를 검증할 수 없다.

```java
public class FixedPublicIdGenerator implements PublicIdGenerator {

    private final Queue<UUID> values;

    public FixedPublicIdGenerator(UUID... values) {
        this.values = new ArrayDeque<>(Arrays.asList(values));
    }

    @Override
    public UUID generate() {
        UUID next = values.poll();
        if (next == null) {
            throw new IllegalStateException("no more prepared UUID");
        }
        return next;
    }
}
```

v4 대상 리소스를 위한 빈을 함께 둘 때는 기본 주입 대상을 `@Primary`로 명시한다. 명시하지 않으면 후보가 겹쳐 기동이 실패한다.

### 왜 ORM 생성기에 위임하지 않는가

Hibernate 7.0부터 `@UuidGenerator(style = VERSION_7)`을 제공하지만 사용하지 않는다.

* **값이 persist 시점에 채워진다.** 생성자 호출부터 `save()` 전까지 `null`이라 사전 채번의 두 성립 조건이 불가능해진다.
* **Hibernate 구현은 rand_a 12비트를 서브밀리초 타임스탬프로 쓴다.** 난수 62비트, 시각 노출 정밀도 약 0.25ms가 되어 난수원 규칙과 정면으로 어긋난다.
* **테스트에서 값을 고정할 수 없다.** Hibernate 내부에서 생성되므로 대체 지점이 없다.
* **하이브리드에서 `public_id`는 `@Id`가 아니다.** 식별자 생성기의 적용 대상 자체가 아니다.

JPA 표준의 `GenerationType.UUID`는 명세가 버전을 규정하지 않으며 Hibernate는 v4로 구현한다.
버전을 지정하려면 어차피 Hibernate 전용 애너테이션이 필요하므로 표준 이식성이라는 명분도 성립하지 않는다.

### 왜 타입 안전 래퍼가 필요한가

내부 ID(`Long`)와 외부 ID(`UUID`)가 원시 타입이면 뒤바꿔 넣어도 컴파일이 통과하고, 잘못된 엔티티의 UUID를 넘겨도 **조회 결과가 비었을 뿐 원인이 드러나지 않는다.**

```java
public abstract class AbstractPublicId {

    private final UUID value;

    protected AbstractPublicId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        /* instanceof 가 아니라 getClass() 로 비교한다.
           서로 다른 엔티티의 식별자가 같은 UUID 값을 가질 때 동등하다고 판정되면
           타입으로 막으려던 혼동이 다시 열린다. */
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return value.equals(((AbstractPublicId) o).value);
    }
}
```

`getClass()` 비교는 리스코프 치환 원칙을 일부 위반하지만, 하위 타입이 상태를 추가하지 않는 값 객체이므로 실용적으로 허용한다.
**하위 타입에 필드를 추가하면 이 전제가 깨진다.**

파싱 실패는 도메인 예외로 변환한다. `UUID.fromString`의 `IllegalArgumentException`을 그대로 두면 잘못된 사용자 입력이 500으로 나간다.

### 왜 공통 모듈에 모으는가

**서비스마다 따로 구현하면 난수원 규칙이 국소적으로 깨진다.**
한 서비스가 성능을 이유로 `ThreadLocalRandom`으로 바꿔도 전역에서 드러나지 않는다.

생성 코드를 한 곳에 두면 규칙 위반이 리뷰 한 번에 걸린다.

## 7. 기존 테이블에 도입하는 절차

**1단계, NULL 허용으로 추가**

```sql
ALTER TABLE account
    ADD COLUMN public_id BINARY(16) NULL,
    ALGORITHM=INPLACE, LOCK=NONE;
```

**2단계, 신규 행에만 값을 채우는 코드 배포**

1단계와 합치지 않는다. 컬럼이 없는 상태에서 값을 쓰는 코드가 먼저 배포되면 전 요청이 실패하므로 순서가 강제된다.

**3단계, 기존 행 배치 백필**

PK 범위로 잘라서 돌린다. 한 번의 UPDATE로 전체를 갱신하면 락 범위와 undo 로그가 테이블 크기에 비례해 커진다.

```sql
UPDATE account
SET public_id = UUID_TO_BIN(UUID())
WHERE public_id IS NULL
  AND account_id BETWEEN ? AND ?;
```

MySQL의 `UUID()`는 v1이라 DB 서버의 MAC 주소가 값에 박힌다.
**외부에 노출되는 테이블이면 그 주소가 그대로 공개되므로, 애플리케이션에서 v4나 v7로 채우는 배치를 대신 돌린다.**

**4단계, 제약 추가**

백필 완료 전에 제약을 걸면 실패하고 대용량 테이블에서 락 시간이 길어지므로 여기까지 미룬다.

```sql
-- 선행 확인: SELECT COUNT(*) FROM account WHERE public_id IS NULL -> 0
ALTER TABLE account
    MODIFY COLUMN public_id BINARY(16) NOT NULL,
    ADD UNIQUE KEY uk_account_public_id (public_id),
    ALGORITHM=INPLACE, LOCK=NONE;
```
