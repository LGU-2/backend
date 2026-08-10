---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-10T14:33:15Z
저장소: https://github.com/LGU-2/backend.git
브랜치: main
커밋: 308f2a34dd9acb141ecfad501cd87476b6fe9366
범위: 작업 트리 (미추적 V1__init_schema.sql)
  사유: 스키마가 아직 커밋되지 않아 커밋 범위에 나타나지 않는다
기준 저장소:
  common: bb5fc9c  ../common (옆 저장소)
  infra: 5c06db8  ../infra (옆 저장소)
매칭 규칙: [migration]
활성 항목: 101 (backend 51, common 46, infra 4)
---

# G-LOCAL  308f2a3  V1__init_schema.sql 재판정

이전 판정(`devjohnpark_20260810-152744`)의 후속이다.

## 빌드 게이트

```
커버리지   통과. *.domain.service.* 클래스가 0개라 매칭 대상 없이 지나갔다
정적 분석  미확인. SONAR_TOKEN 이 없어 sonar 태스크가 돌지 않는다
```

## 판정 범위

```
src/main/resources/db/migration/V1__init_schema.sql   632줄, 테이블 33개
```

```
UNIQUE 41   CHECK 54   FOREIGN KEY 50   KEY 3
```

## 지난 지적 2건의 처리

### `IDS-2-02` 해소

```
전   public_id 0건
후   public_id 20개 테이블, BINARY(16) NOT NULL, UNIQUE 20건
```

`IDS-6-01`(`BINARY(16)` 이고 `NOT NULL` 인가)도 함께 충족됐다.
**중간에 `CHAR(36)` 으로 선언된 판이 있었으나 현재 파일은 `BINARY(16)` 이다.** `IDS-6` 표의 "`CHAR(36)` 금지" 를 지킨다.

### `DI-3-02` 부분 해소

지난번 지적한 8개가 전부 `CHECK` 를 얻었다.

```
OK  orders.status                      OK  payment.status
OK  order_item.item_status             OK  claim.status
OK  order_status_history.from_status   OK  shipment.status
OK  order_status_history.to_status     OK  coupon.discount_type
```

**3개가 남았다.** 아래 VIOLATION 으로 다시 낸다.

## VIOLATION 2건

### `DI-3-02` 값 범위 규칙이 CHECK 제약으로도 표현되어 있는가

```
product_option.sale_status    ON_SALE/SOLD_OUT/OFF_SALE
stock_allocation.status
member_coupon.status          ISSUED/USED/EXPIRED
```

세 컬럼이 주석으로만 허용값을 적고 있다.
같은 스키마의 `product.sale_status` 에는 `CHECK` 가 걸려 있어 **같은 이름의 컬럼이 테이블에 따라 갈렸다.**

나머지 컬럼과 같은 형태로 `CONSTRAINT chk_option_sale_status CHECK (sale_status IN (...))` 를 추가한다.

### `IDS-2-03` 기준에 해당하지 않는데 습관적으로 달지 않았는가

`public_id` 를 가진 20개 중 넷이 `identifier-strategy-guideline.md` 2절의 기준에 해당하지 않는다.

| 테이블 | 왜 해당하지 않는가 |
|---|---|
| `address` | **`base-entity-guideline.md` 6장이 배송지를 하위 엔티티로 명시한다.** `BaseMutableTimeEntity` 대상이다 |
| `product_option` | 상품 하위. `/products/{id}/options/{n}` 로 도달한다 |
| `product_image` | 상품 하위. 목록으로만 조회된다 |
| `cart` | `uk_cart_member` 로 1인 1카트다. `/carts/me` 로 도달하므로 식별자가 필요 없다 |

> 하위 엔티티는 부모 식별자와 순번으로 도달할 수 있고(`/orders/{id}/items/3`), 이력 테이블은 목록으로만 조회되므로 단건 참조 대상이 아니다.

`IDS-2-03` 이 드는 대가가 이것이다. **UNIQUE 인덱스 하나는 저장 공간과 삽입 비용을 모두 늘린다.**
`BINARY(16)` UNIQUE 넷이 쓰이지 않으면 순손실이다.

`payment`, `refund`, `shipment`, `claim` 은 다르다. PG 와 물류사에 식별자를 넘기므로
2절의 "외부 시스템과 식별자를 주고받는다" 에 해당한다. 이쪽은 유지가 맞다.

## CONFLICTING_BASELINE 2건

```
CMP-3-01  응답에 모르는 필드가 추가되어도 클라이언트가 깨지지 않는 전제를 문서화했는가
INF-6-01  스키마 변경이 확장 후 축소(추가만)인가
```

혼재 구간 길이가 세 문서에 1분, 2분, 3분으로 다르다. 지난 판정과 같다.

## OK 13건

```
BE-1-02   이력 테이블 7개에 updated_at 이 없다
BE-2-01   33개 테이블 전부 PK 가 BIGINT
BE-2-02   33개 전부 AUTO_INCREMENT
BE-2-04   문자열 PK 를 쓴 테이블이 없다
DI-3-01   UNIQUE 41건
DI-3-03   FOREIGN KEY 50건
DI-3-04   NOT NULL 다수
IDS-1-01   orders 가 order_id, order_no, public_id 를 각각 다른 층으로 든다
IDS-2-02   애그리거트 루트에 public_id 가 있다
IDS-3-02   v1, v3, v5, v6, v8 을 쓰지 않았다
IDS-6-01   public_id 가 BINARY(16) NOT NULL 이고 UNIQUE 가 걸려 있다
IDS-6-02   DEFAULT (UUID()) 를 걸지 않았다
IDS-6-03   FK 50건이 전부 내부 BIGINT 를 참조한다
```

## NOT_APPLICABLE 84건

애플리케이션 코드가 판정 대상인 항목들이다. 엔티티, 서비스, 컨트롤러가 아직 없다.

## 판정 외 관찰

**시각 컬럼이 `DATETIME` 이고 소수부가 0자리다.**

```
DATETIME     78건
DATETIME(6)   0건
```

`base-entity-rationale.md` 는 "`LocalDateTime` 은 MySQL 8.4 에서 `DATETIME(6)` 으로 매핑되어 마이크로초까지 저장된다" 고 적고 있고,
`application.yml` 이 `ddl-auto: validate` 다.

**엔티티를 붙이는 시점에 스키마 검증이 실패할 수 있다.** 이번에도 SQL 을 돌리지 않아 확인하지 못했다.
확인 방법은 엔티티 하나를 붙이고 Testcontainers 로 `validate` 를 태우는 것이다.

`created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` 도 함께 볼 지점이다.
`BE-3-01` 이 시각을 JPA Auditing 에 맡기라고 하므로 DB 기본값은 실제로 쓰이지 않는다. 충돌은 아니나 이중 선언이다.
