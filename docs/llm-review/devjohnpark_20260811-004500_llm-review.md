---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-11T00:45:00+09:00
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: main
커밋: 1ea1c94
범위: 1ea1c94~1..1ea1c94
기준 저장소:
  common: bb5fc9c  ../common (옆 저장소)
  infra: 5c06db8  ../infra (옆 저장소)
매칭 규칙: [migration]
활성 항목: 101 (backend 51, common 46, infra 4)
---

# G-LOCAL  1ea1c94  [Fix] 하위 엔티티 4개에서 public_id 제거

**이번 실행은 앞선 두 번과 절차가 다르다.** 6단계(확정값)와 8단계(판정 기준 본문)를 실제로 수행했다.
앞선 기록 두 건(`152744`, `233315`)은 그 두 단계를 건너뛰고 판정했으므로 `OK` 와 `NOT_APPLICABLE` 분류의 근거가 약하다.

## 읽은 것

```
6단계 확정값   infra/docs/system-design/  10개 문서에서 DDL 관련 확정값 검색
8단계 기준     qa-data-integrity.md 3장,  qa-compatibility.md 5장,
              qa-flexibility.md 5장,     infra/code-guideline.md 6장,
              base-entity-guideline.md,  identifier-strategy-guideline.md
7단계 모순     known-conflicts.yml
```

활성 항목의 문서별 분포는 이렇다.

```
backend  base-entity-guideline.md           24
backend  identifier-strategy-guideline.md   27
common   qa-compatibility.md                17
common   qa-data-integrity.md               17
common   qa-flexibility.md                  12
infra    code-guideline.md                   4
```

## 빌드 게이트

```
커버리지   통과. *.domain.service.* 클래스가 0개
정적 분석  미확인. SONAR_TOKEN 없음
```

## 지난 지적의 처리

### `IDS-2-03` 해소

`public_id` 를 하위 엔티티에서 뺐다. 20개 -> 16개.

```
제거   address, product_option, product_image, cart
유지   member, admin, category, supplier, product, orders, payment, claim,
       refund, shipment, review, qna, coupon, coupon_campaign, member_coupon, notification
```

`BINARY(16) NOT NULL` 과 UNIQUE 16건이 유지된다. `IDS-2-02`, `IDS-6-01` 도 계속 충족한다.

### `DI-3-02` 미해소

세 컬럼이 그대로다.

```
product_option.sale_status
stock_allocation.status
member_coupon.status
```

## VIOLATION 2건

### `INF-6-04` CI에 파괴적 DDL 차단이 걸려 있는가

```
.github/workflows/llm-verify.yml
.github/workflows/registry-check.yml
```

**DDL 을 언급하는 워크플로가 없다.** 파괴적 DDL 을 감지하는 장치가 저장소에 존재하지 않는다.

확정값 문서 셋이 이것을 요구한다.

```
백엔드공통_시스템디자인_종합.md:374   스키마는 확장 후 축소만 허용한다. CI에서 파괴적 DDL을 차단한다
백엔드공통_무중단배포_롤링.md:384     CI에서 파괴적 DDL을 감지해 차단한다
백엔드공통_장애대응목표와_아키텍처결정.md:283   스키마는 확장 후 축소만 허용한다
```

`백엔드공통_백업과복원_설계.md:69` 가 이 규칙과 스냅샷을 **두 겹 방어**로 설명한다. 지금은 두 겹 중 하나도 없다.

`ALTER TABLE ... DROP COLUMN`, `RENAME`, 기본값 없는 `NOT NULL` 추가, 타입 축소를 잡는 검사를 CI 에 붙인다.

**이 항목은 앞선 두 실행에서 `NOT_APPLICABLE` 로 넘어갔다.** 6단계를 건너뛰어 확정값을 못 봤기 때문이다.

### `DI-3-02` 값 범위 규칙이 CHECK 제약으로도 표현되어 있는가

```
product_option.sale_status    ON_SALE/SOLD_OUT/OFF_SALE
stock_allocation.status
member_coupon.status          ISSUED/USED/EXPIRED
```

**다만 판정 기준 표를 읽고 나니 근거가 처음 생각보다 약하다.** `qa-data-integrity.md` 3장의 표는 이렇다.

| 규칙 유형 | DB 제약으로 표현 |
|---|---|
| 값 범위 (수량 0 이상, 비율 0~100) | CHECK 필수 |
| 상태 전이 규칙 | 애플리케이션에서. 제약으로 표현하기 어렵다 |

**허용값 집합은 이 표의 어느 줄에도 정확히 해당하지 않는다.** 수치 범위도 아니고 전이 규칙도 아니다.

그래서 판정 근거를 문서가 아니라 **이 스키마 자체의 일관성**에 둔다.
같은 스키마의 `product.sale_status` 에는 `CHECK` 가 있는데 `product_option.sale_status` 에는 없다.
`orders.status` 를 포함한 다른 상태 컬럼 8개는 지난 지적 이후 전부 `CHECK` 를 얻었다. 이 셋만 남았다.

문서 쪽도 정리가 필요하다. **표에 "허용값 집합" 줄을 넣어 CHECK 필수인지 아닌지를 못 박는 것**을 권한다.

## CONFLICTING_BASELINE 2건

```
CMP-3-01  응답에 모르는 필드가 추가되어도 클라이언트가 깨지지 않는 전제를 문서화했는가
INF-6-01  스키마 변경이 확장 후 축소(추가만)인가
```

```
백엔드공통_장애대응목표와_아키텍처결정.md  7.1절   약 2분
백엔드공통_장애대응목표와_아키텍처결정.md  7.4절   약 3분
백엔드공통_무중단배포_롤링.md              8절     약 1분
```

-> 결정 필요. `infra/docs/infra-review/pending-decisions.md` 1.4절.

`INF-6-01` 은 값이 정해지면 판정이 갈린다. **V1 은 최초 스키마라 축소할 대상이 없어 실질적으로는 충족**이지만,
혼재 구간 길이가 확정되지 않아 "구 버전을 깨뜨리는가" 의 판단 기준 자체가 서 있지 않다.

## OK 13건

```
BE-1-02   이력 테이블 7개에 updated_at 이 없다
BE-2-01   33개 테이블 전부 PK 가 BIGINT
BE-2-02   33개 전부 AUTO_INCREMENT
BE-2-04   문자열 PK 를 쓴 테이블이 없다
DI-3-01   UNIQUE. 멱등 키에 해당하는 payment.pg_tid, orders.order_no 에 걸려 있다
DI-3-03   FOREIGN KEY 50건. 판정 기준 표의 "참조 관계: 외래 키 권장" 을 충족
DI-3-04   NOT NULL 다수
IDS-1-01   orders 가 order_id, order_no, public_id 를 각각 다른 층으로 든다
IDS-2-02   애그리거트 루트 16개에 public_id 가 있다
IDS-2-03   하위 엔티티에서 public_id 를 뺐다
IDS-3-02   v1, v3, v5, v6, v8 을 쓰지 않았다
IDS-6-01   public_id 가 BINARY(16) NOT NULL 이고 UNIQUE 가 걸려 있다
IDS-6-03   FK 50건이 전부 내부 BIGINT 를 참조한다
```

## NOT_APPLICABLE 84건

기준 본문을 읽고 확인했다. 아래는 판정 대상이 애플리케이션 코드이고, `src/main` 에는 공통 베이스 넷과 설정 하나뿐이다.

```
DI-2-*    잠금. 서비스 로직이 없다
DI-4-*    트랜잭션 경계. @Transactional 을 쓰는 코드가 없다
DI-6-*    메시지와 이벤트. 큐가 없다
DI-7-*    정합성 감지. 배치가 없다
CMP-2-*   API 버전. 컨트롤러가 없다
CMP-3-*   필드 확장. DTO 가 없다
CMP-4-*   오류 응답. 예외 핸들러가 없다
CMP-6-*   커넥션 자원과 캐시 키. 배치와 캐시가 없다
CMP-7-*   명명과 오류 메시지. API 표면이 없다
FLX-1-*   싱글톤 상태. 빈이 없다
FLX-2-*   스케줄러와 인메모리 락. 없다
FLX-4-*   설정 분리. application.yml 하나뿐이고 비밀정보가 없다
BE-1-01, BE-1-03 ~ BE-1-09   @Entity 클래스가 0개
BE-3-*    Auditing 대상 엔티티가 없다
BE-4-*, BE-5-*   코드 테이블이 스키마에 없다
INF-6-02, INF-6-03   API 와 배치가 없다
```

`CMP-5-02` 와 `FLX-5-01`(컬럼 제거 단계)은 `defers_to` 에 따라 `INF-6-01` 이 소유하므로 여기서 발화하지 않는다.

## 판정 외 관찰

### V1 마이그레이션 파일을 커밋 후에 고쳤다

```
479c254  V1__init_schema.sql 추가
1ea1c94  같은 파일에서 8줄 삭제
```

**Flyway 는 적용된 마이그레이션의 체크섬을 저장하고, 파일이 바뀌면 기동을 거부한다.**
아직 어디에도 적용되지 않았으므로 지금은 문제가 없다. 다만 팀원이 `479c254` 시점에 한 번이라도 앱을 띄웠다면
그 로컬 DB 는 다음 기동에서 `Migration checksum mismatch` 로 멈춘다.

조치는 로컬 DB 를 지우는 것이다(`docker compose down -v`). 팀에 공유되기 전에 확정하는 편이 낫다.

### 시각 컬럼 정밀도

```
DATETIME     78건
DATETIME(6)   0건
```

`base-entity-rationale.md` 는 `LocalDateTime` 이 `DATETIME(6)` 으로 매핑된다고 적고 있고 `ddl-auto: validate` 다.
**엔티티를 붙이는 시점에 스키마 검증이 실패할 수 있다.** 세 번째 실행이지만 여전히 미확인이다.
