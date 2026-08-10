---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-10T06:27:44Z
저장소: https://github.com/LGU-2/backend.git
브랜치: main
커밋: d3f04afbde2680d94a8403791bb530da6c622253
범위: 작업 트리 (미커밋 + 미추적). 기본값 HEAD~1..HEAD 를 쓰지 않았다
  사유: 판정 대상인 V1__init_schema.sql 이 아직 커밋되지 않아 커밋 범위에 나타나지 않는다
기준 저장소:
  common: bb5fc9c  ../common (옆 저장소)
  infra: 5c06db8  ../infra (옆 저장소)
매칭 규칙: [migration]
활성 항목: 101 (backend 51, common 46, infra 4)
---

# G-LOCAL  d3f04af  작업 트리

## 빌드 게이트

```
커버리지   통과. 다만 *.domain.service.* 에 해당하는 클래스가 0개라 규칙이 매칭 대상 없이 지나갔다
정적 분석  미확인. SonarCloud 프로젝트와 SONAR_TOKEN 이 없어 sonar 태스크가 돌지 않는다
```

`build.gradle` 에 JaCoCo 와 Sonar 설정은 있다. `BLD-1-*`, `BLD-2-*` 는 이번 매칭 대상이 아니다.

## 판정 범위

```
docs/code-architecture/base-entity-guideline.md          수정
docs/code-architecture/base-entity-rationale.md          수정
docs/code-architecture/entity-creation-guideline.md      수정
docs/code-architecture/entity-creation-rationale.md      수정
docs/verification/verification-status.md                 수정
src/integrationTest/.../BaseEntityMappingTest.java       삭제
src/integrationTest/.../VerificationSample.java          삭제
src/main/resources/db/migration/V1__init_schema.sql      신규 (560줄, 테이블 32개)
```

매칭된 규칙  `migration`
활성 항목    101건 (backend 51, common 46, infra 4)

## VIOLATION 2건

### `IDS-2-02` 새 엔티티가 아래 기준에 해당하는데 `public_id`를 누락하지 않았는가

```
src/main/resources/db/migration/V1__init_schema.sql
```

스키마 전체에 `public_id` 컬럼이 **0건**이다. `BINARY(16)` 도 `UUID` 도 없다.

`identifier-strategy-guideline.md` 2절이 외부 식별자를 다는 기준으로 넷을 든다.
그중 "클라이언트 API의 URL 경로나 응답 본문에 식별자가 노출된다" 와 "사용자가 링크를 공유하거나 북마크할 수 있다" 에
`member`, `product`, `orders`, `review`, `qna`, `coupon` 이 해당한다. 전부 애그리거트 루트다.

애그리거트 루트에 `public_id BINARY(16) NOT NULL` 과 UNIQUE 인덱스를 추가한다.
v4 를 쓸 대상이면 `IDS-3-01` 에 따라 사유를 남긴다.

**이 하나가 다른 항목 셋을 함께 막고 있다.** 중복 지적을 피해 여기서만 발화한다.

```
IDS-6-01  public_id 가 BINARY(16) 이고 NOT NULL 인가        판정할 컬럼이 없다
IDS-7-01  응답 DTO 나 API 경로에 내부 Long id 가 없는가       쓸 수 있는 외부 식별자가 없다
IDS-10-01 비즈니스 식별자를 API 식별자로 쓰지 않는가          order_no, product_code 외에 선택지가 없다
```

### `DI-3-02` 값 범위 규칙이 CHECK 제약으로도 표현되어 있는가

```
src/main/resources/db/migration/V1__init_schema.sql
```

상태성 컬럼 8개가 **DB 제약 없이 주석으로만** 허용값을 적고 있다.

```
orders.status                     PAYMENT_PENDING/PAID/... 12개   CHECK 없음
order_item.item_status                                            CHECK 없음
order_status_history.from_status                                  CHECK 없음
order_status_history.to_status                                    CHECK 없음
payment.status                    PENDING/PAID/FAILED/...          CHECK 없음
claim.status                      REQUESTED/APPROVED/...           CHECK 없음
shipment.status                   PREPARING/SHIPPING/DELIVERED     CHECK 없음
coupon.discount_type              AMOUNT/RATE                      CHECK 없음
```

같은 스키마의 `member.status`, `admin.role`, `product.sale_status`, `product.storage_type`,
`claim.reason_type`, `stock_movement.movement_type` 에는 `CHECK` 가 걸려 있다. **같은 성격의 컬럼이 두 갈래로 갈렸다.**

원인이 보인다. 이 8개는 이전 스키마에서 `_code` 테이블을 참조하는 FK 로 값이 강제되던 것들이고,
코드 테이블을 걷어내면서 **FK 는 사라졌는데 CHECK 가 그 자리를 대신하지 않았다.**

`entity-creation-rationale.md` 5장이 enum 을 기본으로 정한 것은 맞지만,
`entity-creation-guideline.md` R4-1 이 "MySQL 8.4 는 CHECK 제약을 실제로 강제하므로 최종 방어선까지 세울 수 있다" 고 한다.
나머지 컬럼처럼 `CONSTRAINT chk_order_status CHECK (status IN (...))` 를 추가한다.

## CONFLICTING_BASELINE 2건

### 구 버전과 신 버전 혼재 구간

```
CMP-3-01  응답에 모르는 필드가 추가되어도 클라이언트가 깨지지 않는 전제를 문서화했는가
INF-6-01  스키마 변경이 확장 후 축소(추가만)인가
```

```
백엔드공통_장애대응목표와_아키텍처결정.md  7.1절   약 2분
백엔드공통_장애대응목표와_아키텍처결정.md  7.4절   약 3분
백엔드공통_무중단배포_롤링.md              8절     약 1분
```

-> 결정 필요. `infra/docs/infra-review/pending-decisions.md` 1.4절에 정하는 법이 있다.

## INSUFFICIENT_EVIDENCE 0건

앵커는 전부 결과를 얻었다. `**/domain/entity/*.java` 는 **부재가 확인**된 것이므로 판정 근거가 된다.

## OK 11건

```
BE-1-02   이력 테이블 7개(stock_disposal, stock_movement, order_status_history,
          claim_item, coupon_campaign_product, point_history, audit_log)에 updated_at 이 없다
BE-2-01   32개 테이블 전부 PK 가 BIGINT
BE-2-02   32개 전부 AUTO_INCREMENT
BE-2-04   문자열 PK 를 쓴 테이블이 없다
DI-3-01   UNIQUE 20건. uk_member_provider_user, uk_order_no, uk_cart_member 등
DI-3-03   FOREIGN KEY 49건
DI-3-04   NOT NULL 228건
IDS-1-01   orders 가 order_id(내부)와 order_no(비즈니스)를 따로 든다
IDS-3-02   v1, v3, v5, v6, v8 을 쓰지 않았다
IDS-6-02   DEFAULT (UUID()) 를 걸지 않았다
IDS-6-03   FK 49건이 전부 내부 BIGINT 를 참조한다
```

## NOT_APPLICABLE 86건

애플리케이션 코드가 판정 대상인 항목들이다. `src/main` 에 공통 베이스 넷과 설정 하나뿐이고
서비스, 컨트롤러, 엔티티 클래스가 아직 없다.

```
BE-1-01, BE-1-03 ~ BE-1-09    @Entity 클래스가 0개
BE-3-01 ~ BE-3-04             Auditing 대상 엔티티가 없다
BE-4-*, BE-5-*                코드 테이블이 스키마에서 사라졌다
CMP-4-*, CMP-6-*, CMP-7-*     API 와 배치가 없다
DI-2-*, DI-4-*, DI-6-*, DI-7-*  서비스 로직이 없다
FLX-1-*, FLX-2-*, FLX-4-*, FLX-5-*  빈과 설정이 없다
IDS-4-*, IDS-5-*, IDS-8-*, IDS-9-*  public_id 자체가 없어 생성과 노출을 판정할 대상이 없다
INF-6-02 ~ INF-6-04           API 와 CI 파괴적 DDL 차단이 대상
```

## 이번 실행에서 드러난 검증 시스템의 구멍

판정과 별개로 기록한다.

**1. `src/integrationTest/**` 가 어떤 앵커에도 안 걸린다.**

`test` 규칙의 트리거가 `src/test/**/*Test.java` 다.
이번에 통합 테스트 2개가 삭제됐는데 **어떤 규칙도 매칭되지 않았다.**
`build.gradle` 이 `integrationTest` 소스셋을 만들고 `check` 가 그것에 의존하는데, 앵커는 그 경로를 모른다.

**2. `migration` 규칙이 공통 베이스 엔티티를 앵커로 갖지 않는다.**

`entity` 규칙에는 `**/common/entity/*.java` 가 있으나 `migration` 규칙에는 없다.
DDL 과 베이스 엔티티의 대조가 필요한 `BE-*` 항목이 이번 실행에서 근거 없이 판정될 뻔했다.

**3. 실행하지 않아 확인하지 못한 것**

`created_at DATETIME` 은 소수부가 0자리다. `base-entity-rationale.md` 는
"`LocalDateTime` 은 MySQL 8.4 에서 `DATETIME(6)` 으로 매핑" 이라고 적고 있고,
`application.yml` 이 `ddl-auto: validate` 다.

**엔티티를 붙이는 시점에 스키마 검증이 실패할 가능성이 있다.** 이번 실행에서는 SQL 을 돌리지 않아 확인하지 못했다.
