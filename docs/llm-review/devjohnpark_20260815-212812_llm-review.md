---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-15T12:28:12Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: main
커밋: c06915c9e2b2157ffcabfe3f273537a5c737ea1a
범위: a1445214532e1a1d688dee7103fcaf3011a0e49f..c06915c9e2b2157ffcabfe3f273537a5c737ea1a
기준 저장소:
  common: 1cdc425  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common (옆 저장소)
  infra: 2b66714  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra (옆 저장소)
매칭 규칙: []
활성 항목: 50 (backend 50, common 0, infra 0)
---

# G-LOCAL c06915c [Feat] G-LOCAL 진입점을 verify.sh 하나로 통합한다

## 빌드 게이트

통과 (`./gradlew check --no-daemon -q`)

## 매칭된 규칙

없음. 변경 파일 5건은 모두 `anchors.yml` 의 어떤 `trigger` 글롭에도 걸리지 않는다.

- `.claude/commands/verify.md`
- `verify.sh`
- `docs/verification/g-local.md`
- `docs/verification/verification-architecture.md`
- `docs/verification/verification-status.md`

트리거는 `**/*.java`, `**/*Api.java`, `src/main/resources/db/migration/*.sql`, `src/main/resources/application*.yml`,
`src/test/**/*Test.java`, `build.gradle` 등 코드/설정 대상뿐이라 위 문서/셸 스크립트 변경과 무관하다.
`defaults.on_no_match` 적용: `levels: [코드]`, `prefixes: [EJ]`.

## 활성 항목

50건 (backend EJ-1-01 ~ EJ-11-02). common 과 infra 레지스트리에는 `EJ-` 접두사 항목이 없어 각 0건.

## VIOLATION 0건

## CONFLICTING_BASELINE 0건

## INSUFFICIENT_EVIDENCE 0건

## OK 0  NOT_APPLICABLE 50

이번 커밋은 G-LOCAL 진입점을 `verify.sh` 하나로 합치면서 `.claude/commands/verify.md` 를 얇은 포인터로 줄이고
관련 문서 3건을 갱신한 것으로, Java 코드 변경이 없다.
EJ 항목 50건은 전부 Java 코드 관용(정적 팩터리, 불변성, 제네릭, 예외, 동시성, 직렬화 등)을 대상으로 하므로 이 diff 와 무관하다.

- EJ-1-01 ~ EJ-1-06 (객체 생성과 자원): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-2-01 ~ EJ-2-03 (equals/hashCode/toString): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-3-01 ~ EJ-3-06 (클래스와 인터페이스): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-4-01 ~ EJ-4-04 (제네릭): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-5-01 ~ EJ-5-03 (열거 타입과 애너테이션): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-6-01 ~ EJ-6-04 (람다와 스트림): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-7-01 ~ EJ-7-04 (메서드): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-8-01 ~ EJ-8-07 (일반 프로그래밍): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-9-01 ~ EJ-9-06 (예외): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-10-01 ~ EJ-10-05 (동시성): NOT_APPLICABLE, 변경된 Java 코드 없음
- EJ-11-01 ~ EJ-11-02 (직렬화): NOT_APPLICABLE, 변경된 Java 코드 없음
