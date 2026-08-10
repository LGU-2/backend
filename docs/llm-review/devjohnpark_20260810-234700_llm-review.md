---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-10T14:47:00Z
저장소: https://github.com/LGU-2/backend.git
브랜치: main
커밋: 479c254571520c6f6de58fb8bd32996cf0a0b33b
범위: 479c254~1..479c254 (308f2a3..479c254)
기준 저장소:
  common: bb5fc9c  ../common (옆 저장소)
  infra: 5c06db8  ../infra (옆 저장소)
매칭 규칙: [migration]
활성 항목: 101 (backend 51, common 46, infra 4)
---

# G-LOCAL  479c254  초기 스키마 마이그레이션 추가 (테이블 33개)

이전 판정(`devjohnpark_20260810-233315`, 작업 트리 기준)의 후속이다.
그 판정은 파일이 아직 커밋되지 않은 상태에서 돈 것이고, 이번은 `479c254`로 **커밋된 diff**를 대상으로 다시 돈다.

## 빌드 게이트

```
커버리지   통과. *.domain.service.* 클래스가 0개라 매칭 대상 없이 지나갔다
정적 분석  미확인. SONAR_TOKEN 이 없어 sonar 태스크가 돌지 않는다 (check 는 sonar 를 포함하지 않는다)
```

## 판정 범위

```
src/main/resources/db/migration/V1__init_schema.sql   632줄 추가, 테이블 33개
```

## 매칭된 규칙

`migration` (trigger: `src/main/resources/db/migration/*.sql`)

## 지난 지적의 처리

### `DI-3-02` 해소됨 (VIOLATION -> OK)

지난 판정에서 CHECK 가 없다고 지적한 3개 컬럼 전부 확인했다.

```
OK  product_option.sale_status   chk_option_sale_status (V1__init_schema.sql:151)
OK  stock_allocation.status      chk_alloc_status        (V1__init_schema.sql:278)
OK  member_coupon.status         chk_mc_status            (V1__init_schema.sql:583)
```

`member.provider` 는 여전히 CHECK 가 없지만, 주석이 "확장 대비 컬럼"이라 명시해 닫힌 값 집합이 아니다.
값 범위 규칙 자체가 없으므로 `DI-3-02` 대상이 아니다.

### `IDS-2-03` 미해소 (VIOLATION 유지)

`address`, `product_option`, `product_image`, `cart` 4개 테이블에 `public_id` 가 그대로 남아 있다.

## VIOLATION 1건

### `IDS-2-03` 반대로 기준에 해당하지 않는데 습관적으로 달지 않았는가

```
V1__init_schema.sql:51    address.public_id
V1__init_schema.sql:139   product_option.public_id
V1__init_schema.sql:156   product_image.public_id
V1__init_schema.sql:192   cart.public_id
```

`identifier-strategy-guideline.md` 2절의 외부 식별자 부여 기준(URL/응답 노출, 외부 시스템 연동, 서비스 분리 시 참조, 링크 공유) 중 어느 것에도 해당하지 않는다.

`base-entity-guideline.md` 6장이 "하위 엔티티(주문 항목, 배송지)"를 `BasePublicMutableTimeEntity` 가 아니라 `BaseMutableTimeEntity` 대상으로 명시한다. `address` 가 이 예시에 정확히 해당한다.
`product_option`, `product_image` 도 같은 성격이다. `/products/{id}/options/{n}`, `/products/{id}/images/{n}` 로 부모 식별자와 순번으로 도달하므로 외부 식별자가 필요 없다.
`cart` 는 `uk_cart_member` 로 회원당 1개다. `/carts/me` 로 도달하므로 단건 참조에 식별자가 쓰이지 않는다.

`payment`, `refund`, `shipment`, `claim` 은 PG/물류사와 식별자를 주고받으므로 기준 2절의 "외부 시스템과 식별자를 주고받는다"에 해당해 유지가 맞다. 이 4개와는 다르다.

**수정 방법**: `address`, `product_option`, `product_image`, `cart` 4개 테이블에서 `public_id` 컬럼과 `uk_*_public` UNIQUE 제약을 제거한다. `BINARY(16)` UNIQUE 인덱스 4개는 저장 공간과 삽입 비용의 순손실이다.

## CONFLICTING_BASELINE 2건

```
CMP-3-01  응답에 모르는 필드가 추가되어도 클라이언트가 깨지지 않는 전제를 문서화했는가
INF-6-01  스키마 변경이 확장 후 축소(추가만)인가
```

`known-conflicts.yml` 에 `status: unresolved` 로 등록된 항목이다. 혼재 구간 길이가 문서마다 다르다.

```
백엔드공통_장애대응목표와_아키텍처결정.md 7.1절: 약 2분
백엔드공통_장애대응목표와_아키텍처결정.md 7.4절: 약 3분
백엔드공통_무중단배포_롤링.md 8절: 약 1분
```

-> 값을 하나로 확정하는 결정이 필요하다.

## OK 14건

```
BE-1-02   이력 테이블 7개에 updated_at 이 없다
BE-2-01   33개 테이블 전부 PK 가 BIGINT
BE-2-02   33개 전부 AUTO_INCREMENT
BE-2-04   문자열 PK 를 쓴 테이블이 없다
DI-3-01   UNIQUE 41건
DI-3-02   값 범위 규칙이 있는 컬럼 전부 CHECK 로 표현됨 (이번에 해소)
DI-3-03   FOREIGN KEY 50건
DI-3-04   NOT NULL 다수
IDS-1-01  orders 가 order_id, order_no, public_id 를 각각 다른 층으로 든다
IDS-2-02  애그리거트 루트에 public_id 가 있다
IDS-3-02  v1, v3, v5, v6, v8 을 쓰지 않았다
IDS-6-01  public_id 가 BINARY(16) NOT NULL 이고 UNIQUE 가 걸려 있다
IDS-6-02  DEFAULT (UUID()) 를 걸지 않았다
IDS-6-03  FK 50건이 전부 내부 BIGINT 를 참조한다
```

## NOT_APPLICABLE 84건

애플리케이션 코드가 판정 대상인 항목들이다. 엔티티, 서비스, 컨트롤러가 아직 없다.

## 판정 외 관찰

시각 컬럼이 `DATETIME` 이고 소수부가 0자리다(78건, `DATETIME(6)` 0건).
`base-entity-rationale.md` 는 `LocalDateTime` 이 MySQL 8.4 에서 `DATETIME(6)` 으로 매핑된다고 적고, `application.yml` 이 `ddl-auto: validate` 다.
엔티티를 붙이는 시점에 스키마 검증이 실패할 수 있다. 이번에도 SQL 을 돌리지 않아 확인하지 못했다. 등록된 점검 항목이 아니라 VIOLATION 으로 세지 않는다.
