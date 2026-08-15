---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-15T16:01:12Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: test/violation
커밋: 371b8772923602322b1bfdc8dd439bca0cb6c48f
범위: 371b8772923602322b1bfdc8dd439bca0cb6c48f..371b8772923602322b1bfdc8dd439bca0cb6c48f
기준 저장소:
  common: f28968c  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common (옆 저장소)
  infra: 80c713d  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra (옆 저장소)
매칭 규칙: []
활성 항목: 50 (backend 50, common 0, infra 0)
---

# G-LOCAL 371b877 [Test] 사고 예산 수정 후 판정을 다시 받는다

## 빌드 게이트

통과

## 매칭된 규칙

없음. `test/violation` 브랜치는 `origin/test/violation` 과 커밋이 완전히 동일하다
(`git status`: "Your branch is up to date with 'origin/test/violation'.", working tree clean).
push 하지 않은 커밋이 0개이므로 범위가 `371b877..371b877` (커밋 0개, 변경 파일 0건)로 계산되었고,
어떤 `trigger` 글롭도 걸릴 파일이 없어 `defaults.on_no_match` 가 적용되었다: `levels: [코드]`, `prefixes: [EJ]`.

## 활성 항목

50건 (backend EJ-1-01 ~ EJ-11-02). common 과 infra 레지스트리에는 `EJ-` 접두사 항목이 없어 각 0건.

## VIOLATION 0건

## CONFLICTING_BASELINE 0건

## INSUFFICIENT_EVIDENCE 0건

## OK 0  NOT_APPLICABLE 50

판정 대상 diff 자체가 비어 있다(push 하지 않은 커밋 없음). EJ 항목 50건은 전부 Java 코드 관용(정적 팩터리,
불변성, 제네릭, 예외, 동시성, 직렬화 등)을 대상으로 하며 변경된 코드가 전혀 없으므로 전부 NOT_APPLICABLE 이다.

- EJ-1-01 ~ EJ-1-06 (객체 생성과 자원): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-2-01 ~ EJ-2-03 (equals/hashCode/toString): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-3-01 ~ EJ-3-06 (클래스와 인터페이스): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-4-01 ~ EJ-4-04 (제네릭): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-5-01 ~ EJ-5-03 (열거 타입과 애너테이션): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-6-01 ~ EJ-6-04 (람다와 스트림): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-7-01 ~ EJ-7-04 (메서드): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-8-01 ~ EJ-8-07 (일반 프로그래밍): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-9-01 ~ EJ-9-06 (예외): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-10-01 ~ EJ-10-05 (동시성): NOT_APPLICABLE, 변경된 커밋 없음
- EJ-11-01 ~ EJ-11-02 (직렬화): NOT_APPLICABLE, 변경된 커밋 없음
