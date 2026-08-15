---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-15T11:38:49Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: main
커밋: 07f94adb8ee21d81fd8e4303eb9a7a0303ab7336
범위: HEAD~1..HEAD
기준 저장소:
  common: e2b1bdd  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common (옆 저장소)
  infra: 399ba15  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra (옆 저장소)
매칭 규칙: []
활성 항목: 24 (backend 24, common 0, infra 0)
---

# G-LOCAL 07f94ad [Docs] 캐시로 떨어졌을 때도 기준 저장소 경로가 채워지게 한다

## 빌드 게이트

- 커버리지: `./gradlew check` 통과. `domain.service` 패키지 자체가 아직 없어 커버리지 판정 대상이 없음 (`jacocoTestCoverageVerification` SKIPPED, 테스트 소스 없음)
- 정적 분석: `sonar` 태스크는 이번 실행에 포함되지 않음 (`check` 은 `jacocoTestCoverageVerification` 까지만 의존). `build.gradle` 에 JaCoCo(5행)와 SonarQube(6행) 플러그인 설정은 존재함

## 매칭된 규칙

없음. 변경 파일 `.claude/commands/verify.md` 는 `anchors.yml` 의 어떤 `trigger` 글롭에도 걸리지 않음 (`.java`, `.sql`, `.yml`, `build.gradle` 대상 규칙뿐). `defaults.on_no_match` 적용: `levels: [코드]`, `prefixes: [EJ]`.

## 활성 항목

24건 (backend EJ-1-01 ~ EJ-11-02). common 과 infra 레지스트리에는 `EJ-` 접두사 항목이 없어 0건.

## VIOLATION 0건

## CONFLICTING_BASELINE 0건

## INSUFFICIENT_EVIDENCE 0건

## OK 0  NOT_APPLICABLE 24

이번 커밋은 `.claude/commands/verify.md` 문서 한 줄만 고친 것으로, Java 코드 변경이 없다.
EJ 항목 24건은 전부 Java 코드 관용(생성자, 예외, 스트림, 동시성 등)을 대상으로 하므로 이 diff 와 무관하다.

- EJ-1-01 ~ EJ-1-06 (자원과 생성): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-2-01 ~ EJ-2-03 (equals/hashCode/toString): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-3-01 ~ EJ-3-06 (클래스와 인터페이스): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-4-01 ~ EJ-4-04 (제네릭): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-5-01 ~ EJ-5-03 (열거 타입): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-6-01 ~ EJ-6-04 (람다와 스트림): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-7-01 ~ EJ-7-04 (메서드): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-8-01 ~ EJ-8-07 (일반 프로그래밍): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-9-01 ~ EJ-9-06 (예외): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-10-01 ~ EJ-10-05 (동시성): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-11-01 ~ EJ-11-02 (직렬화): NOT_APPLICABLE, 변경된 Java 코드 없음
