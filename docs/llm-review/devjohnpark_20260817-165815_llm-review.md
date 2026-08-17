---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-17T07:58:15Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: feat/test-rules
커밋: fbe4a76e85eb6334f2773361d372bf2039d0091b
범위: 0a65ac2aab94d90106a76096f426466afa5074c0..fbe4a76e85eb6334f2773361d372bf2039d0091b
기준 저장소:
  common: c21e1725469ee71b0483b1dfbd74b19a2c1f7119  ../common (옆 저장소)
  infra: 332bba0ae4209c5dd178a7b4bef5b5f4ed944001  ../infra (옆 저장소)
매칭 규칙: [migration, test, archunit, build]
활성 항목: 194 (backend 116, common 74, infra 4)
---

G-LOCAL  fbe4a76  [Clean] DDL 머리말에서 필요 없는 주석을 지운다

빌드 게이트
  커버리지   통과
  정적 분석  로컬에서는 돌리지 않는다 (SONAR_TOKEN 없음)

매칭된 규칙  migration, test, archunit, build
활성 항목    116건  (backend 116, common 74와 infra 4는 --full 없이 돌려 판정 범위 밖)

VIOLATION 1건

  UT-3-04  조건 분기나 반복 같은 로직을 테스트 안에 넣지 않는가
    기준: backend unit-testing-guideline.md 3장
    src/test/java/com/freshmarket/TestPlacementTest.java:46
    src/integrationTest/java/com/freshmarket/PlacementIntegrationTest.java:51
    판정 대상 클래스를 고르려고 테스트 안에서 stream 과 filter 를 여러 겹 쓴다. 테스트 자체에 버그가 생길 자리다
    ArchUnit DSL 로 옮기면 로직이 사라지지만, 그러면 main 클래스까지 검사 대상에 들어와 소스셋을 가를 수 없다. 감수한 이유를 클래스 주석에 남겼다

CONFLICTING_BASELINE 0건

INSUFFICIENT_EVIDENCE 0건

OK 23  NOT_APPLICABLE 92

## OK 23건

BLD-1-01 BLD-1-02 BLD-1-03 BLD-1-04 BLD-1-05 BLD-1-06 BLD-1-07
BLD-2-01 BLD-2-02 BLD-2-03
DPB-4-10 DPB-6-03 DPB-6-04 DPB-6-05
UT-1-03 UT-1-04 UT-2-01 UT-3-01 UT-3-03 UT-5-04 UT-6-01 UT-6-02 UT-6-03

근거를 남길 만한 것만 적는다.

* `BLD-1-04` `jacocoTestCoverageVerification` 과 `jacocoTestReport` 가 `include('test.exec')` 다.
  가짜 `integrationTest.exec` 를 놓고 Gradle 이 읽는 실행 데이터를 확인했다
* `BLD-2-03` `main` 과 `develop` 의 필수 상태 검사가 `["G-BUILD"]` 하나다. `G-PR` 은 의도적으로 빠져 있다
* `DPB-4-10` `ArchitectureTest` 의 `컨트롤러_이름`, `서비스_이름`, `레포지토리_이름` 이 강제한다.
  위반을 심어 `./gradlew check` 가 실패하는 것을 확인했다
* `UT-5-04` `TestPlacementTest` 와 `PlacementIntegrationTest` 가 `check` 에 묶여 있다

## NOT_APPLICABLE 92건

* **BE 22건** 엔티티가 하나도 없다. DDL 변경은 머리말 주석 삭제뿐이라 컬럼과 제약이 그대로다
* **DPB 30건** 도메인 패키지가 아직 없다. `~Api`, `~ApiImpl`, 컨트롤러, 레포지토리가 존재하지 않는다
* **IDS 29건** `public_id` 는 이 스키마에 넣지 않기로 했고(`identifier-strategy-guideline.md` 머리말),
  엔티티도 없어 대조할 대상이 없다
* **UT 11건** `UT-1-01`, `UT-1-02`, `UT-2-02`, `UT-2-03`, `UT-3-02`, `UT-4-01`, `UT-4-02`, `UT-4-03`,
  `UT-5-01`, `UT-5-02`, `UT-5-03`. 프로덕션 로직을 검증하는 단위 테스트와 DB 통합 테스트가 아직 없다.
  이번에 추가한 것은 배치 규칙을 검사하는 아키텍처 테스트뿐이다

## 남길 말

`--full` 로 돌리지 않아 common 74건과 infra 4건은 판정하지 않았다.
CI 의 G-PR 도 backend 항목만 보므로 그 78건은 이 변경에 대해 아무도 보지 않은 상태다.
