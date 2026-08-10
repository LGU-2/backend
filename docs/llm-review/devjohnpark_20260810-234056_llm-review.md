---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-10T14:40:56Z
저장소: https://github.com/LGU-2/backend.git
브랜치: main
커밋: d2854d4203d0ede040b925a05bd15586fde58ced
범위: HEAD~1..HEAD
기준 저장소:
  common: bb5fc9c  ../common (옆 저장소)
  infra: 5c06db8  ../infra (옆 저장소)
매칭 규칙: []
활성 항목: 50 (backend 50, common 0, infra 0)
---

# G-LOCAL  d2854d4  스키마 재판정 기록 추가

## 빌드 게이트

```
커버리지   통과. *.domain.service.* 클래스가 0개라 매칭 대상 없이 지나갔다
정적 분석  미확인. SONAR_TOKEN 이 없어 sonar 태스크가 돌지 않는다 (check 는 sonar 를 포함하지 않는다)
```

## 판정 범위

```
docs/llm-review/devjohnpark_20260810-233315_llm-review.md   144줄, 신규 파일
```

이전 판정(`308f2a3`) 결과를 기록한 문서 1건만 추가됐다. 소스 코드 변경은 없다.

## 앵커 매칭

`.github/llm-verify/anchors.yml` 의 어떤 `trigger` 글롭도 `docs/llm-review/*.md` 에 걸리지 않는다.
`defaults.on_no_match` 를 적용한다: `levels: [코드]`, `prefixes: [EJ]`.

backend `items.yml` 의 `EJ-*` 50건은 전부 `level: 코드`, `domains: [application]` 이다.
`common`, `infra` 레지스트리에는 `EJ` 접두사 항목이 없다 (0건).

## 판정

`effective-java-guideline.md` 1절이 "모든 자바 변경에 적용한다" 고 명시한다.
이번 변경은 마크다운 기록 파일 추가뿐이고 자바 소스 변경이 없으므로, 활성화된 50건 전부가 이 변경과 무관하다.

## VIOLATION 0건

## CONFLICTING_BASELINE 0건

## INSUFFICIENT_EVIDENCE 0건

## OK 0건

## NOT_APPLICABLE 50건

```
EJ-1-01 ~ EJ-1-06   (6)   객체 생성과 파괴
EJ-2-01 ~ EJ-2-03   (3)   모든 객체의 공통 메서드
EJ-3-01 ~ EJ-3-06   (6)   클래스와 인터페이스
EJ-4-01 ~ EJ-4-04   (4)   제네릭
EJ-5-01 ~ EJ-5-03   (3)   열거 타입과 애너테이션
EJ-6-01 ~ EJ-6-04   (4)   람다와 스트림
EJ-7-01 ~ EJ-7-04   (4)   메서드
EJ-8-01 ~ EJ-8-07   (7)   일반적인 프로그래밍 원칙
EJ-9-01 ~ EJ-9-06   (6)   예외
EJ-10-01 ~ EJ-10-05 (5)   동시성
EJ-11-01 ~ EJ-11-02 (2)   직렬화
```

자바 소스 변경이 없어 전부 판정 대상이 아니다.
