---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-15T17:03:37Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: test/violation
커밋: 5570af16c11e42ce987ec267eba3ff91dfb2b96a
범위: 84fa77d4c500eba74320433a8e093029c64ab4ab..5570af16c11e42ce987ec267eba3ff91dfb2b96a
기준 저장소:
  common: 0ce2242  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common (옆 저장소)
  infra: 3512355  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra (옆 저장소)
매칭 규칙: [service, test]
활성 항목: 216 (backend 103, common 87, infra 26)
---

# G-LOCAL 5570af1 [Test] OrderService 테스트를 채워 커버리지 게이트를 통과시킨다

변경 파일 1건. `src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java` 신규 43줄.

## 빌드 게이트

```
커버리지   통과 (*.domain.service.* METHOD 100%)
정적 분석  로컬 미실행. sonar 가 check 에 묶여 있지 않다 (build.gradle:171-175)
```

`./gradlew check` 통과. 다만 **커버리지 게이트가 통과한 것이 이번 판정의 핵심 문제다.**
`OrderService` 의 세 메서드를 호출만 해도 METHOD 커버리지는 100% 가 되고, 단언이 하나도 없어도 게이트는 초록이다.
빌드 게이트는 이 커밋을 막지 않지만 아래 UT 항목들이 전부 걸린다.

빌드 게이트는 작업 트리(`c7ff250`)에서 돌았다. `05b77c7` 과 `c7ff250` 은 `src/` 를 건드리지 않으므로
판정 커밋 시점의 결과와 같다.

## 매칭된 규칙

`service`, `test`

`test` 는 `src/test/**/*Test.java` 로 걸렸다. `service` 는 trigger 글롭 `**/domain/service/*.java` 가
테스트 파일 경로 `src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java` 를 함께 잡아서 걸렸다.
아래 "앵커 규칙에 대한 지적" 에서 다룬다.

## 활성 항목

216건 (backend 103, common 87, infra 26)

| 접두사 | 건수 | 출처 |
|---|---|---|
| EJ | 50 | backend |
| DPB | 33 | backend |
| INF | 26 | infra |
| UT | 20 | backend |
| DI | 17 | common |
| FUN | 14 | common |
| REL | 13 | common |
| SEC | 13 | common |
| PERF | 11 | common |
| MNT | 10 | common |
| FLX | 6 | common |
| TRD | 3 | common |

읽은 앵커: `SecurityConfig.java`, `Order.java`, `OrderItem.java`, `OrderRepository.java`, `OrderService.java`.
`**/domain/client/**/*.java` 와 `**/*Api.java` 는 저장소에 파일이 없다. 부재 자체가 확인된 것이라
INSUFFICIENT_EVIDENCE 로 두지 않는다.

확정값 문서(`needs_baseline: true`)는 `$INFRA/docs/system-design/` 에서 확인했다.
이번 변경에 트랜잭션 타임아웃과 잠금 대기 상한을 대조할 코드가 없다.

## VIOLATION 8건

전부 이번 커밋이 추가한 `OrderServiceTest.java` 에 있다.

```
UT-1-01  회귀 방어: 테스트가 실제 버그를 잡아내는가
  기준: backend unit-testing-guideline.md 1장
  src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:26,32,38
  세 테스트가 메서드를 호출만 하고 단언이 하나도 없어 어떤 회귀도 잡지 못한다
  반환값과 상태 변화를 assertThat 으로 검증한다

UT-2-01  결과나 상태 변화 등 외부에서 관찰 가능한 동작을 검증하는가
  기준: backend unit-testing-guideline.md 2장
  OrderServiceTest.java:26-40
  findOrder 의 반환 Order, findAll 의 반환 List 크기, cancel 후 status 를 하나도 보지 않는다
  주문을_취소한다 에서는 order.getStatus() 가 "CANCELED" 인지 확인한다

UT-3-01  준비(given), 실행(when), 검증(then) 단계가 구분되는가
  기준: backend unit-testing-guideline.md 3장
  OrderServiceTest.java:25-40
  then 단계가 통째로 없고 given 과 when 도 구분 없이 두 줄로 붙어 있다
  given, when, then 을 주석이나 빈 줄로 나누고 then 을 채운다

UT-3-03  테스트 이름이 어떤 상황에서 무엇을 기대하는지 드러내는가
  기준: backend unit-testing-guideline.md 3장
  OrderServiceTest.java:25,31,37
  주문을_조회한다 처럼 동작만 적어 어떤 상황인지도 무엇을 기대하는지도 드러나지 않는다
  존재하지_않는_주문을_조회하면_예외가_발생한다 처럼 상황과 기대를 이름에 넣는다

UT-4-03  모든 의존성을 무분별하게 모킹하지 않는가
  기준: backend unit-testing-guideline.md 4장
  OrderServiceTest.java:19-20
  단 하나의 의존성인 OrderRepository 를 @Mock 으로 덮어 실제로 실행되는 코드가 OrderService 본문뿐이다
  관리 의존성인 DB 는 모킹하지 않는다. UT-5-01 과 같은 코드다

UT-5-01  데이터베이스처럼 우리가 관리하고 외부에 노출되지 않는 의존성은 실제로 사용해 통합 테스트하는가
  기준: backend unit-testing-guideline.md 5장
  OrderServiceTest.java:19-20, 26, 33, 39
  관리 의존성인 OrderRepository 를 mock 으로 대체해 실제 쿼리와 매핑 오류를 잡지 못한다
  build.gradle 에 이미 붙어 있는 testcontainers-mysql 로 src/integrationTest 에 통합 테스트를 쓴다

UT-5-03  통합 테스트가 관리 의존성과의 실제 연동(쿼리, 트랜잭션, 매핑)을 검증하는가
  기준: backend unit-testing-guideline.md 5장
  src/integrationTest 디렉터리 자체가 없다 (build.gradle:25,96 에 소스셋과 태스크는 있다)
  파생 쿼리 findByMemberId 와 Order-OrderItem @OneToMany 매핑이 어디에서도 실행되지 않는다
  @DataJpaTest 또는 testcontainers 기반 통합 테스트로 저장 후 조회를 검증한다

UT-6-03  커버리지 숫자 자체를 목표로 삼지 않는가
  기준: backend unit-testing-guideline.md 6장
  OrderServiceTest.java 전체, 커밋 메시지 "커버리지 게이트를 통과시킨다"
  단언 없이 실행만 하는 테스트로 *.domain.service.* METHOD 100% 게이트를 통과시켰다
  게이트를 통과시키는 것이 아니라 동작을 검증하는 테스트로 바꾼다
```

### 이번 판정에 함께 잡힌 사실 하나

`주문을_취소한다` 는 `OrderService.cancel` 이 예외를 통째로 삼키기 때문에(`OrderService.java:33-34` 의 빈 catch)
저장이 실패해도 무조건 초록이다. 단언을 넣어도 이 catch 가 있는 한 실패를 관찰할 수 없다.

`OrderService.java` 는 이번 커밋의 변경 대상이 아니라 앵커로 읽은 파일이다.
빈 catch 자체는 앞 커밋 `84fa77d [Feat] 주문 조회와 취소를 추가한다` 가 소유하므로
MNT-4-04 나 EJ-9-06 으로 여기서 발화시키지 않는다. UT-1-01 을 고치려면 저 커밋도 함께 봐야 한다는 사실만 남긴다.

## CONFLICTING_BASELINE 0건

`known-conflicts.yml` 의 `status: unresolved` 목록과 활성 항목이 겹치는 것은 `INF-2-01` 하나다
(`deploy-capacity`: 확보 용량 50% 대 100%).
이번 변경에 배치도 종료 신호 처리도 없어 판정할 대상 자체가 없으므로 NOT_APPLICABLE 로 둔다.
어느 쪽 값을 고르는 판정이 아니므로 유보할 것이 없다.

목록에 없는 새 모순은 발견되지 않았다.

## INSUFFICIENT_EVIDENCE 0건

앵커 글롭에 맞는 파일을 모두 읽었다.

## OK 11  NOT_APPLICABLE 197

OK 11건은 전부 UT 항목이다.

| ID | 근거 |
|---|---|
| UT-1-02 | 단언도 verify 도 없어 구현 세부에 묶인 곳이 없다. 회귀 방어를 버려서 얻은 내성이라 UT-1-01 과 함께 읽는다 |
| UT-1-03 | 순수 mock 기반이라 실행이 빠르다 |
| UT-1-04 | 43줄, 테스트당 2~3줄로 읽기 쉽다 |
| UT-2-02 | 호출 순서나 횟수를 검증하는 verify 가 없다 |
| UT-2-03 | private 메서드를 직접 부르지 않는다 |
| UT-3-02 | 테스트마다 실행(when)이 하나다 |
| UT-3-04 | 테스트 안에 분기도 반복도 없다 |
| UT-4-01 | stub 과의 상호작용을 verify 로 검증하지 않는다 |
| UT-4-02 | mock 검증을 오용한 곳이 없다 |
| UT-6-01 | 중복 픽스처나 읽기 어려운 준비 코드가 없다 |
| UT-6-02 | @Mock 이 테스트마다 새로 만들어져 상태 공유가 없다 |

NOT_APPLICABLE 197건.

* UT-5-02 (1건): 이 코드에 결제 API 같은 외부 공유 의존성이 없다.
* MNT 10건: 프로덕션 코드가 바뀌지 않았다. 테스트 이름은 UT-3-03 이 소유한다.
* EJ 50, DPB 33, INF 26, DI 17, FUN 14, REL 13, SEC 13, PERF 11, FLX 6, TRD 3 (186건):
  전부 프로덕션 코드를 대상으로 하는 항목인데 이번 diff 는 테스트 파일 하나뿐이다.
  `service` 규칙이 잘못 걸려 켜진 항목들이다.

## 앵커 규칙에 대한 지적

`backend/.github/llm-verify/anchors.yml` 의 `service` 규칙 trigger 가 넓다.

```yaml
- id: service
  trigger:
    - "**/domain/service/*.java"
```

`run.py` 의 `matches()` 는 저장소 상대 경로 전체에 글롭을 맞추므로 이 패턴이
`src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java` 도 잡는다.
그 결과 `test` 규칙이 **일부러 뺀** DPB 와 EJ 가 `service` 경로로 다시 켜졌다.
같은 파일의 주석이 경고한 상황 그대로다.

```yaml
- id: test
  activate:
    # DPB 와 EJ 는 넣지 않는다. 프로덕션 코드를 겨냥한 항목이라
    # 테스트에 걸면 목 주입, 픽스처 빌더, 긴 메서드명이 전부 지적으로 나온다
```

이번에는 프로덕션 코드가 하나도 안 바뀌어 전부 NOT_APPLICABLE 로 흘러가 피해가 없었다.
프로덕션 서비스와 테스트를 같이 고치는 커밋에서는 구분이 되지 않으므로 드러나지 않는다.
드러나는 것은 이번처럼 테스트만 바꾼 커밋이고, 활성 항목이 20건이면 될 자리에 216건이 켜진다.

고치는 방법은 trigger 를 프로덕션 경로로 못박는 것이다.

```yaml
- id: service
  trigger:
    - "src/main/**/domain/service/*.java"
```

`entity`, `repository`, `controller`, `external-client` 도 같은 형태라 함께 본다.
`test` 규칙의 trigger 가 이미 `src/test/**` 로 시작하므로 대칭이 맞는다.

`anchors.yml` 의 앵커 목록 자체는 부족하지 않았다. INSUFFICIENT_EVIDENCE 가 0건이다.
