---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-16T02:08:54Z
저장소: https://github.com/fresh-market/fm-backend.git
브랜치: main
커밋: 2d2e2dbba6491cd45f6fed83818662141710cf2e
범위: 16de189c12c9f60bb33a1f619a24c4e3a8ffaf2a..2d2e2dbba6491cd45f6fed83818662141710cf2e
기준 저장소:
  common: 25b25019360770fca1a5add72e219296f02b453e  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/common   # 옆 저장소
  infra: b67075751213ff2830d0e135b6c9c4b72f28932d  /Users/junseopark/Documents/dev/backend/ureca/comprehensive/infra   # 옆 저장소
매칭 규칙: [on_no_match]
활성 항목: 50 (backend 50, common 0, infra 0)
---

G-LOCAL  2d2e2db  [Docs] 팀원용 git 규약을 추가한다

빌드 게이트
  커버리지   건너뜀 (빌드 대상이 바뀌지 않았다)
  정적 분석  건너뜀 (빌드 대상이 바뀌지 않았다)

매칭된 규칙  on_no_match
활성 항목    50건  (backend 50, common 0, infra 0)

변경 파일 2건
  docs/git-convention.md                   신규 98줄
  docs/verification/verification-guide.md  +174 -63

VIOLATION 0건

CONFLICTING_BASELINE 0건
  known-conflicts.yml 의 unresolved 4건은 REL-, INF-, OPS-, CMP- 항목에만 걸린다. 활성 항목에 없다.

INSUFFICIENT_EVIDENCE 0건
  on_no_match 는 anchors 를 정의하지 않으므로 못 읽은 앵커가 없다.

OK 0  NOT_APPLICABLE 50

## NOT_APPLICABLE 50건

변경 파일이 마크다운 문서 2개뿐이고 `.java`, `src/main/resources/**`, `build.gradle`,
`settings.gradle`, `gradle/**` 중 어느 것도 바뀌지 않았다.
EJ 항목 50건은 전부 자바 프로덕션 코드를 판정 대상으로 하므로 이번 변경과 무관하다.

기준: backend `effective-java-guideline.md` 1~11장

- 1장   EJ-1-01, EJ-1-02, EJ-1-03, EJ-1-04, EJ-1-05, EJ-1-06
- 2장   EJ-2-01, EJ-2-02, EJ-2-03
- 3장   EJ-3-01, EJ-3-02, EJ-3-03, EJ-3-04, EJ-3-05, EJ-3-06
- 4장   EJ-4-01, EJ-4-02, EJ-4-03, EJ-4-04
- 5장   EJ-5-01, EJ-5-02, EJ-5-03
- 6장   EJ-6-01, EJ-6-02, EJ-6-03, EJ-6-04
- 7장   EJ-7-01, EJ-7-02, EJ-7-03, EJ-7-04
- 8장   EJ-8-01, EJ-8-02, EJ-8-03, EJ-8-04, EJ-8-05, EJ-8-06, EJ-8-07
- 9장   EJ-9-01, EJ-9-02, EJ-9-03, EJ-9-04, EJ-9-05, EJ-9-06
- 10장  EJ-10-01, EJ-10-02, EJ-10-03, EJ-10-04, EJ-10-05
- 11장  EJ-11-01, EJ-11-02

## 이 실행에 대한 지적

계산 결과가 `code_changed: false`, `skip: true` 였다.
`anchors.yml` 의 `defaults.code_globs` 기준으로 판정 대상 파일이 하나도 안 바뀌었으므로
g-local.md 1장 "판정할 것이 없으면 여기서 끝난다" 에 해당하는 실행이다.

`anchors.yml` 주석이 이미 같은 사실을 적고 있다.
"문서만 고친 커밋이 on_no_match 로 떨어지면 EJ 항목 50건이 켜지는데,
자바가 하나도 안 바뀌었으니 전부 NOT_APPLICABLE 이 나올 수밖에 없다."

이번 실행이 정확히 그 경우다. 결과에 새로운 정보가 없다.
문서 변경의 정합성은 `registry-check.yml` 이 따로 본다.
