---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-16T03:25:27+09:00
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: test/violation
커밋: 5570af16c11e42ce987ec267eba3ff91dfb2b96a
범위: 84fa77d4c500eba74320433a8e093029c64ab4ab..5570af16c11e42ce987ec267eba3ff91dfb2b96a
기준 저장소:
  common: 909a1067bafac0f76e8ef8434114ebef6d267c78  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common   # 옆 저장소
  infra: b67075751213ff2830d0e135b6c9c4b72f28932d  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra   # 옆 저장소
매칭 규칙: [service, test]
활성 항목: 216 (backend 103, common 87, infra 26)
판정 범위: backend 103건만. common 과 infra 항목은 --full 없이 돌려 묻지 않았다
---

G-LOCAL  5570af1  [Test] OrderService 테스트를 채워 커버리지 게이트를 통과시킨다

빌드 게이트
  커버리지   통과 (`./gradlew check`)
  정적 분석  통과 (`./gradlew check`)

매칭된 규칙  service, test
활성 항목    216건  (backend 103, common 87, infra 26)
판정 항목    103건  (backend 만)

VIOLATION 6건

  UT-1-01  회귀 방어: 테스트가 실제 버그를 잡아내는가
    기준: backend unit-testing-guideline.md 1장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:28,34,40
    세 테스트 모두 stub 을 걸고 메서드를 호출하기만 하고 assert 가 하나도 없어 어떤 회귀도 잡지 못한다
    반환값과 상태를 단언한다. `assertThat(orderService.findOrder(1L)).isEqualTo(order)` 처럼 결과를 검증한다

  UT-2-01  결과나 상태 변화 등 외부에서 관찰 가능한 동작을 검증하는가
    기준: backend unit-testing-guideline.md 2장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:38-42
    `주문을_취소한다` 가 `OrderService.cancel` 을 부르고 끝나서 status 가 `CANCELED` 로 바뀌는지, 저장이 일어나는지 아무것도 관찰하지 않는다
    `assertThat(order.getStatus()).isEqualTo("CANCELED")` 로 상태 변화를 단언한다

  UT-3-01  준비(given), 실행(when), 검증(then) 단계가 구분되는가
    기준: backend unit-testing-guideline.md 3장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:26-42
    given(when 스텁)과 when(호출)만 있고 then 단계가 아예 없다
    각 테스트에 검증 단계를 넣고 세 단계를 주석이나 빈 줄로 구분한다

  UT-3-03  테스트 이름이 어떤 상황에서 무엇을 기대하는지 드러내는가
    기준: backend unit-testing-guideline.md 3장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:27,33,39
    `주문을_조회한다`, `회원의_주문을_모두_조회한다`, `주문을_취소한다` 는 호출할 메서드 이름을 옮긴 것이라 상황도 기대도 없다
    문서 3장 예시처럼 `재고가_부족하면_주문에_실패한다` 꼴로 상황과 기대를 이름에 담는다

  UT-5-01  데이터베이스처럼 우리가 관리하고 외부에 노출되지 않는 의존성은 실제로 사용해 통합 테스트하는가
    기준: backend unit-testing-guideline.md 5장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:19-20
    관리 의존성인 `OrderRepository` 를 `@Mock` 으로 대체했고 실제 DB 를 쓰는 테스트가 저장소에 하나도 없어, `OrderService.findAll` 의 지연 로딩 루프와 `cancel` 의 매핑/저장이 실제로 도는지 검증되지 않는다
    `@DataJpaTest` 로 `OrderRepository` 통합 테스트를 두어 쿼리와 매핑을 실제 DB 로 검증한다

  UT-6-03  커버리지 숫자 자체를 목표로 삼지 않는가
    기준: backend unit-testing-guideline.md 6장
    src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java:1-43
    커밋 메시지가 "커버리지 게이트를 통과시킨다" 이고 실제로 단언 없는 실행만으로 `domain.service` 메서드 커버리지 게이트를 통과시켰다. 문서 6장이 지목한 "검증 없이 실행만 하는 테스트" 그대로다
    커버리지가 아니라 동작을 목표로 단언을 채운다. 단언이 붙으면 커버리지는 따라온다

CONFLICTING_BASELINE 0건

INSUFFICIENT_EVIDENCE 0건

OK 11  NOT_APPLICABLE 86

OK (11)
  UT-1-02, UT-1-03, UT-1-04, UT-2-02, UT-2-03, UT-3-02, UT-3-04, UT-4-01, UT-4-03, UT-6-01, UT-6-02

NOT_APPLICABLE (86)
  UT-4-02  mock 으로 검증하는 곳이 자체가 없다
  UT-5-02  통제 불가능한 외부 공유 의존성이 없다
  UT-5-03  통합 테스트가 없다. 부재 자체는 UT-5-01 이 지적했다
  DPB 33건, EJ 50건  이 범위에서 프로덕션 자바가 하나도 바뀌지 않았다

## 판정 외 관찰

* `anchors.yml` 의 `service` 규칙 트리거 `**/domain/service/*.java` 가 이번 커밋의 유일한 변경 파일인
  `src/test/java/com/freshmarket/order/domain/service/OrderServiceTest.java` 에 매칭됐다.
  테스트 파일 하나에 `service` 와 `test` 두 규칙이 함께 걸려 DPB 33건과 EJ 50건이 켜졌는데,
  `anchors.yml` 은 주석에서 "test 규칙에 DPB 와 EJ 를 넣지 않는다. 프로덕션 코드를 겨냥한 항목이라
  테스트에 걸면 오탐이 는다" 고 밝히고 있다. 트리거를 `src/main/**/domain/service/*.java` 로 좁히면
  이번 실행에서 답이 정해져 있던 83건이 애초에 켜지지 않는다.
* 앵커로 읽은 `OrderService.java` 자체에 이 범위 밖의 문제가 보인다.
  `cancel` 의 빈 catch 블록(EJ-9-06), `findAll` 의 지연 로딩 루프(PERF-2-01),
  `@Transactional` 부재, `findOrder`/`findAll` 의 소유권 검증 부재(SEC-1-01)다.
  이번 커밋이 프로덕션 코드를 바꾸지 않아 판정 대상이 아니고 SEC 와 PERF 는 common 항목이라
  `--full` 없이는 묻지도 않는다. 그 파일을 건드리는 커밋에서 다시 본다.
