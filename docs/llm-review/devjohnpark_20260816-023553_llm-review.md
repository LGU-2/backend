---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-16T02:35:53+09:00
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: test/violation
커밋: 84fa77d4c500eba74320433a8e093029c64ab4ab
범위: 87ef309a88530b75cffab398a107f6eca510472f..84fa77d4c500eba74320433a8e093029c64ab4ab
기준 저장소:
  common: ed3ea4d0a4fb5f0744399dd0d7e4cce0277cb9b8  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common   # 옆 저장소
  infra: 87d2832b8c698cb8c54f52439b15b4488911c216  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra   # 옆 저장소
매칭 규칙: [service, entity, repository]
활성 항목: 180 (backend 180, common 0, infra 0)
---

G-LOCAL  84fa77d  [Feat] 주문 조회와 취소를 추가한다

빌드 게이트
  커버리지   통과
  정적 분석  통과

매칭된 규칙  service, entity, repository
활성 항목    180건  (backend 180, common 0, infra 0)
판정 범위    backend 항목만. common 128건과 infra 항목은 이번에 묻지 않았다 (--full 로 본다)

확정값은 읽지 않았다. 활성 항목에 `REL-` 과 `INF-` 가 없어 대조할 값이 없다.
알려진 모순 5건은 전부 `REL-`, `INF-`, `OPS-`, `CMP-` 를 가리켜 이번 범위와 겹치지 않는다.

VIOLATION 27건

## OrderService.java

  EJ-9-06  예외를 무시하지 않는가 (아이템 77)
    기준: backend `effective-java-guideline.md` 9장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:33
    `catch (Exception e) { }` 가 비어 있어 취소 실패가 호출자에게도 로그에도 남지 않는다
    잡지 말고 올리거나, 잡는다면 `BusinessException` 으로 변환해 던진다

  EJ-9-02  복구 가능한 상황과 프로그래밍 오류를 구분해 예외 종류를 선택했는가 (아이템 70)
    기준: backend `effective-java-guideline.md` 9장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:33
    `Exception` 을 통째로 잡아 없는 주문과 NPE 같은 프로그래밍 오류가 한 덩어리로 묻힌다
    잡을 예외를 구체 타입으로 좁히고 프로그래밍 오류는 통과시킨다

  EJ-9-05  추상화 수준에 맞는 예외를 던지는가 (아이템 73)
    기준: backend `effective-java-guideline.md` 9장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:17, 30
    인자 없는 `orElseThrow()` 가 `NoSuchElementException` 을 그대로 올려 도메인 실패가 JDK 예외로 나간다
    `common/exception` 의 `BusinessException` 과 주문 `ErrorCode` 로 변환해 던진다

  JPA-4-03  복잡한 조회를 엔티티 전체 로딩과 객체 그래프 탐색으로 풀지 않고, 필요한 컬럼만 DTO로 프로젝션하는가
    기준: backend `jpa-rdb-guideline.md` 4장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:22-24
    `o.getItems().size()` 로 지연 컬렉션을 억지로 깨우는데, 이 메서드에 트랜잭션이 없어 영속성 컨텍스트가 닫힌 뒤면 `LazyInitializationException` 이 난다
    주문 항목이 필요하면 `@Query` 로 필요한 컬럼만 DTO 프로젝션해 한 번에 읽는다

  JPA-4-04  성능 문제를 사후에 실행 계획으로 수습하기 전에, 설계 시점에 쿼리 형태를 고려했는가
    기준: backend `jpa-rdb-guideline.md` 4장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:21-24
    주문 목록 1회 + 주문 건수만큼 `order_item` 조회가 나가는 N+1 이다
    `order_id` 목록으로 항목을 한 번에 읽어 애플리케이션에서 묶는다

  JPA-5-02  메모리 객체 자동 분해, 깊은 cascade 저장, 조인 테이블 자동 생성, JSON 자동 매핑 같은 기능은, 그것이 만들어 내는 DB 구조와 실행 쿼리를 정확히 이해하고 책임질 수 있을 때만 쓰는가
    기준: backend `jpa-rdb-guideline.md` 5장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:23
    지연 로딩 연관을 실행 쿼리 형태를 정하지 않은 채 호출부에서 깨운다 (JPA-1-02, JPA-4-04 와 같은 코드)
    연관 매핑을 걷어내고 조회 쿼리를 명시적으로 쓴다

  EC-2-13  상태 변경이 setter가 아니라 도메인 메서드로 이루어지는가
    기준: backend `entity-creation-guideline.md` 2장 R7
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:31
    `order.setStatus("CANCELED")` 로 서비스가 상태를 직접 쓴다
    `Order.cancel()` 을 엔티티에 두고 서비스는 그것만 호출한다

  EC-2-14  그 메서드가 전이 전제 조건을 검사하는가
    기준: backend `entity-creation-guideline.md` 2장 R7
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:31
    이미 배송된 주문도 취소되고, `V1__init_schema.sql` 이 요구하는 `order_item.item_status` 동반 변경이 빠져 상품 쿠폰이 풀리지 않는다
    `Order.cancel()` 안에서 현재 상태를 검사하고 주문 항목 상태까지 함께 바꾼다

  EJ-5-01  상수 집합을 int 상수 대신 enum으로 표현했는가 (아이템 34)
    기준: backend `effective-java-guideline.md` 5장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:31
    상태를 `"CANCELED"` 문자열 리터럴로 넣어 오타가 컴파일에서 걸리지 않는다 (EC-4-01 과 같은 코드)
    `OrderStatus` enum 을 만들어 상수로 참조한다

  EJ-7-01  파라미터 유효성을 메서드 시작 부분에서 검사하는가 (아이템 49)
    기준: backend `effective-java-guideline.md` 7장
    src/main/java/com/freshmarket/order/domain/service/OrderService.java:16, 20, 28
    세 메서드 모두 `orderId` 와 `memberId` 의 null 검사가 없어 스프링 데이터 내부에서 터진다
    메서드 첫 줄에서 `Objects.requireNonNull` 이나 도메인 예외로 막는다

## Order.java, OrderItem.java

  BE-1-01  모든 `@Entity`가 위 네 클래스 중 하나를 상속하는가
    기준: backend `base-entity-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    둘 다 베이스를 상속하지 않아 `created_at` 과 `updated_at` 매핑이 없는데, `V1__init_schema.sql` 은 두 컬럼을 `NOT NULL` 로 잡고 있어 INSERT 가 실패한다
    두 엔티티 모두 `BaseMutableTimeEntity` 를 상속한다

  BE-1-05  `id`와 시각 컬럼을 엔티티가 직접 선언하지 않았는가
    기준: backend `base-entity-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:16-18, OrderItem.java:11-13
    `orderId`, `orderItemId` 를 엔티티가 직접 `@Id` 로 선언해 베이스의 공통 매핑에서 벗어난다
    베이스의 `id` 를 쓰고 직접 선언을 지운다

  EC-1-01  엔티티에 `@Setter`, `@Data`가 붙어 있지 않은가
    기준: backend `entity-creation-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:13
    `@Setter` 가 모든 필드를 열어 불변식을 지킬 자리가 없다
    `@Setter` 를 지우고 상태 변경은 도메인 메서드로만 연다

  EC-2-12  세 애너테이션이 엔티티에 없는가
    기준: backend `entity-creation-guideline.md` 2장 R6
    src/main/java/com/freshmarket/order/domain/entity/Order.java:13
    R6 이 금지한 `@Setter` 가 붙어 있다 (EC-1-01 과 같은 코드)
    `@Setter` 를 지운다

  EC-2-01  `@NoArgsConstructor(access = AccessLevel.PROTECTED)`가 있는가
    기준: backend `entity-creation-guideline.md` 2장 R1
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    선언이 없어 자바가 public 기본 생성자를 만든다
    두 엔티티에 `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 를 붙인다

  EC-2-02  기본 생성자가 public이 아닌가
    기준: backend `entity-creation-guideline.md` 2장 R1
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    암묵 기본 생성자가 public 이라 `new Order()` 로 빈 엔티티를 만들 수 있다
    EC-2-01 과 같은 조치로 닫는다

  EC-2-03  public 생성자 또는 필수를 받지 않는 public `builder()`가 있는가
    기준: backend `entity-creation-guideline.md` 2장 R2
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    public 기본 생성자가 검증 없는 생성 경로가 되고, `@Setter` 와 합쳐져 사후 조립이 가능하다
    검증을 모으는 private 생성자와 정적 팩터리 하나로 경로를 좁힌다

  EC-2-05  `id`를 외부에서 세팅할 수 있는가
    기준: backend `entity-creation-guideline.md` 2장 R3
    src/main/java/com/freshmarket/order/domain/entity/Order.java:13, 18
    `@Setter` 가 `setOrderId` 를 만들어 `save()` 가 persist 가 아니라 merge 로 흘러 남의 행을 덮어쓸 수 있다
    `@Setter` 를 지우고 식별자는 생성 파라미터에서도 뺀다

  EC-2-06  상태 필드를 외부에서 지정할 수 있는가
    기준: backend `entity-creation-guideline.md` 2장 R4
    src/main/java/com/freshmarket/order/domain/entity/Order.java:22
    `setStatus` 로 외부가 초기 상태와 이후 상태를 마음대로 정한다
    초기 상태는 생성자 본문에서 `PAYMENT_PENDING` 으로 고정한다

  EC-2-07  필수 필드 검증이 private 생성자에 있는가
    기준: backend `entity-creation-guideline.md` 2장 R4-1
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14-25
    생성자가 없어 `memberId` 가 null 인 주문이 만들어질 수 있다
    검증을 모으는 private 생성자를 두고 필수 필드를 거기서 막는다

  EC-2-09  필수 필드가 팩터리 파라미터로 컴파일 시점에 강제되는가
    기준: backend `entity-creation-guideline.md` 2장 R5
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    팩터리가 없어 필수 강제가 아예 없다
    `Order.place(memberId, ...)` 같은 정적 팩터리를 유일한 진입점으로 둔다

  EJ-1-01  생성자 대신 정적 팩터리 메서드라는 선택지를 검토했는가 (아이템 1)
    기준: backend `effective-java-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14, OrderItem.java:9
    이름 있는 생성 경로가 없다 (EC-2-09 와 같은 코드)
    도메인 행위를 드러내는 정적 팩터리를 둔다

  EJ-3-03  가변성을 최소화했는가, 가능하면 불변 객체로 두었는가 (아이템 17)
    기준: backend `effective-java-guideline.md` 3장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:13
    `@Setter` 로 전 필드가 가변이다 (EC-1-01 과 같은 코드)
    변경 지점을 도메인 메서드로 좁힌다

  EC-4-01  후보값이 정해진 속성에 enum을 썼는가
    기준: backend `entity-creation-guideline.md` 4장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:22
    `status` 가 `String` 이라 `V1__init_schema.sql` 의 `chk_order_status` 12개 값이 코드에 드러나지 않고 DB CHECK 위반이 런타임에야 난다
    `OrderStatus` enum 을 만들고 `@Enumerated(EnumType.STRING)` 을 명시한다

  JPA-1-01  테이블 하나에 엔티티 하나가 대응하도록 최대한 단순하게 유지했는가
    기준: backend `jpa-rdb-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:14-25
    `orders` 의 `order_no`, `product_amount`, `total_amount`, `ship_recipient` 등 `NOT NULL` 컬럼이 매핑에서 빠져 엔티티만 봐서는 테이블이 보이지 않고 INSERT 도 실패한다
    테이블 컬럼을 빠짐없이 매핑하거나, 이번에 안 쓸 컬럼이면 그 사유를 남긴다

  JPA-1-02  외래 키를 연관 객체 매핑 대신 식별자 컬럼으로 그대로 반영하는 선택을 검토했는가
    기준: backend `jpa-rdb-guideline.md` 1장
    src/main/java/com/freshmarket/order/domain/entity/Order.java:24-25, OrderItem.java:15-17
    이 프로젝트는 FK 를 `Long` 으로 들고 JPA 연관을 매핑하지 않는데(`entity-creation-guideline.md` 3장 G3, `IDS-6-03`) `@OneToMany` 와 `@ManyToOne` 을 걸었다
    `OrderItem` 에 `Long orderId` 를 두고 양쪽 연관 매핑을 걷어낸다

## 저장소 전체

  DPB-6-03  ArchUnit 테스트가 빌드 파이프라인에 묶여 있는가
    기준: backend `domain-package-boundary-guideline.md` 6장
    src/test/java 아래에 `ArchitectureTest` 가 없다
    계층 구조에서 유일한 자동 검증 수단이 없어 경계 위반이 리뷰에서만 걸린다
    6.2절 규칙 묶음으로 `ArchitectureTest` 를 추가하고 `check` 에 묶는다

CONFLICTING_BASELINE 0건

INSUFFICIENT_EVIDENCE 0건

OK 47  NOT_APPLICABLE 106

## 비고

`IDS-*` 29건 중 26건이 NOT_APPLICABLE 이다. `identifier-strategy-guideline.md` 가 외부 노출 식별자를
추후로 미루었고 현재 스키마에 `public_id` 가 없어서다. 문서가 스스로 유보를 밝힌 정상 상태다.

`DPB-6-03` 의 근거인 `ArchitectureTest` 는 이번에 걸린 세 규칙(service, entity, repository)의
앵커 목록에 없다. `archunit` 규칙에만 들어 있어 그 파일이 바뀌지 않으면 앵커로 동봉되지 않는다.
이번에는 테스트 디렉터리를 직접 확인해 부재를 확정했으나, 앵커에 기대면 판정할 수 없는 항목이다.
`anchors.yml` 의 service/entity/repository 규칙에 `src/test/**/ArchitectureTest.java` 를 더할지 검토한다.

아래 둘은 backend 가 아니라 common 이 소유해 이번 범위 밖이다. 코드에 해당 정황이 있으므로
push 전에 `./verify.sh 84fa77d --full` 로 한 번 더 본다.

* `OrderService.findOrder` 와 `cancel` 이 호출자가 주문 소유자인지 확인하지 않는다 -> `SEC-1-*`
* `OrderService` 어디에도 `@Transactional` 이 없어 `cancel` 의 읽고 쓰기가 한 트랜잭션이 아니다 -> `DI-4-*`
