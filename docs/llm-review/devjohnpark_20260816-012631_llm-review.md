---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-15T16:26:31Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: test/violation
커밋: 371b8772923602322b1bfdc8dd439bca0cb6c48f
범위: efe9eff649cd0989129ab6d7df714b792836e070..371b8772923602322b1bfdc8dd439bca0cb6c48f
기준 저장소:
  common: 239ae33  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common (옆 저장소)
  infra: 3512355  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra (옆 저장소)
매칭 규칙: []
활성 항목: 50 (backend 50, common 0, infra 0)
---

# G-LOCAL 371b877 [Test] 사고 예산 수정 후 판정을 다시 받는다

`./verify.sh HEAD~1` 을 다시 돌렸다. `verify.sh` 의 ref 해석이 바뀌어 이제 `HEAD~1` 은
"최신 커밋 1개" 가 아니라 "git 이 읽는 그대로" 그 커밋 하나(`<ref>~1..<ref>`)를 뜻한다.
저장소 HEAD 가 그 사이 `e423a21`(`[Fix] ref 는 git 대로 읽고 개수는 -n 으로 뺀다`)로 한 커밋 더 나가서,
`HEAD~1` 은 여전히 `371b877` 을 가리켜 범위가 앞선 실행들과 동일하게 계산되었다(`efe9eff..371b877`).
판정 내용도 동일하다.

## 빌드 게이트

통과

## 매칭된 규칙

없음. `371b877` 은 파일을 하나도 바꾸지 않은 빈 커밋이다(`git show --stat`: 메시지 외 변경 없음).
변경 파일 0건이라 어떤 `trigger` 글롭도 걸리지 않아 `defaults.on_no_match` 가 적용되었다:
`levels: [코드]`, `prefixes: [EJ]`.

## 활성 항목

50건 (backend EJ-1-01 ~ EJ-11-02). common 과 infra 레지스트리에는 `EJ-` 접두사 항목이 없어 각 0건.

## VIOLATION 0건

## CONFLICTING_BASELINE 0건

## INSUFFICIENT_EVIDENCE 0건

## OK 0  NOT_APPLICABLE 50

이 커밋은 파일 변경이 없는 빈 커밋이다. EJ 항목 50건은 전부 Java 코드 관용(정적 팩터리, 불변성,
제네릭, 예외, 동시성, 직렬화 등)을 대상으로 하므로 변경된 코드가 없는 이 diff 와 무관하다.

- EJ-1-01 ~ EJ-1-06 (객체 생성과 자원): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-2-01 ~ EJ-2-03 (equals/hashCode/toString): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-3-01 ~ EJ-3-06 (클래스와 인터페이스): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-4-01 ~ EJ-4-04 (제네릭): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-5-01 ~ EJ-5-03 (열거 타입과 애너테이션): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-6-01 ~ EJ-6-04 (람다와 스트림): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-7-01 ~ EJ-7-04 (메서드): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-8-01 ~ EJ-8-07 (일반 프로그래밍): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-9-01 ~ EJ-9-06 (예외): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-10-01 ~ EJ-10-05 (동시성): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
- EJ-11-01 ~ EJ-11-02 (직렬화): NOT_APPLICABLE, 변경된 코드 없음 (빈 커밋)
