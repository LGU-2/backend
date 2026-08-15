---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-10T15:35:00+09:00
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: main
커밋: d3f04afbde2680d94a8403791bb530da6c622253
범위: HEAD~1..HEAD
기준 저장소:
  common: bb5fc9c  ../common   # 옆 저장소, 직접 clone
  infra: 5c06db8  ../infra   # 옆 저장소, 직접 clone
매칭 규칙: []  # 어떤 trigger 글롭에도 걸리지 않아 defaults.on_no_match 적용
활성 항목: 50 (backend 50, common 0, infra 0)
---

# G-LOCAL d3f04af [Refactor] 아무 일도 하지 않는 lombok.config 와 BLD-1-05 제거

## 빌드 게이트

**커버리지**
`./gradlew check` 는 성공했으나 `jacocoTestCoverageVerification` 은 `SKIPPED` 다.
Gradle info 로그: `Skipping task ':jacocoTestCoverageVerification' as task onlyIf 'Any of the execution data files exists' is false.`
현재 `src/test`, `src/integrationTest` 에 자바 테스트가 하나도 없고(전부 삭제됨),
`*.domain.service.*` 에 해당하는 클래스도 아직 없어 실행 자체가 스킵됐다. 판정 대상이 없는 상태이므로
위반은 아니지만, "통과"로 보고할 근거도 없다 (실행되지 않았다).

**정적 분석**
`check` 는 `sonar` 태스크에 의존하지 않는다 (`build.gradle:132-134`, SonarCloud 인증 필요).
로컬 `./gradlew check` 만으로는 신규 Blocker 건수를 확인할 수 없다.

## 판정 범위 산출

```
git diff --name-only HEAD~1..HEAD
.github/llm-verify/anchors.yml
.github/llm-verify/items.yml
docs/code-architecture/build-gate-guideline.md
docs/code-architecture/build-gate-rationale.md
docs/verification/verification-status.md
lombok.config (deleted)
```

## 앵커 규칙 매칭

`.github/llm-verify/anchors.yml` 의 11개 규칙 trigger 를 모두 대조했으나 이번 diff 파일 중
어느 것도 매칭되지 않았다 (전부 검증 메타 파일: 레지스트리 yml, 가이드 문서, `lombok.config` 삭제).
`lombok.config` 는 이 커밋에서 `build` 규칙의 trigger 목록에서 스스로 빠졌으므로 매칭 대상도 아니다.

`defaults.on_no_match` 적용: `levels: [코드]`, `prefixes: [EJ]`.

세 레지스트리에서 EJ 항목을 센 결과 backend 50건, common 0건, infra 0건. 활성 항목 50건.

## 판정

`effective-java-guideline.md` 서두: "모든 자바 변경에 적용한다."
이번 커밋은 자바 파일을 전혀 건드리지 않았다 (yml, md, 설정 파일 삭제뿐).
활성화된 EJ-1-01 ~ EJ-11-02 50건 전부 이 변경과 무관하다.

VIOLATION 0건

CONFLICTING_BASELINE 0건

INSUFFICIENT_EVIDENCE 0건

OK 0  NOT_APPLICABLE 50

## 참고 (판정 외)

`build.gradle:132`, `:137` 의 주석이 각각 `BLD-1-06`, `BLD-1-07` 을 가리킨다.
이번 커밋이 `items.yml` 에서 `BLD-1-05`(lombok.config 항목)를 제거하며 `check` 의존성 항목과
`sonar` 순서 항목의 ID 가 각각 `BLD-1-05`, `BLD-1-06` 으로 한 칸씩 당겨졌는데, `build.gradle` 은
이 커밋의 diff 에 없어 주석이 옛 번호(`BLD-1-06`, `BLD-1-07`)를 그대로 가리키고 있다.
`build.gradle` 이 이번 diff 에 없어 `build` 앵커 규칙이 발화하지 않았고, 그래서 BLD 항목은
이번 회차에 활성 항목이 아니었다. 다음에 `build.gradle` 을 건드리는 커밋에서 이 불일치가
`BLD` 판정에 걸릴 수 있으니 그때 주석 번호도 함께 맞추는 것을 권한다.
