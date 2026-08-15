# 로컬 검증 기록

`/verify` (G-LOCAL) 를 돌린 결과가 이 디렉터리에 쌓인다.
절차는 `docs/verification/g-local.md`, 설계 근거는 `fresh-market/.github` 의 `docs/software-quality/qa-llm-verification.md` 에 있다.

## 파일명

```
<깃허브 계정명>_<YYYYMMDD-HHMMSS>_llm-review.md

devjohnpark_20260806-174500_llm-review.md
```

계정명을 넣는 이유는 팀원이 각자 로컬에서 돌리기 때문이고,
초 단위까지 넣는 이유는 한 커밋을 고쳐 가며 여러 번 돌리는 것이 정상 사용이기 때문이다.

## 왜 커밋하는가

로컬 판정은 개발자 재량이라 안 돌려도 아무 일이 일어나지 않는다.
기록이 남아야 **돌렸는지 안 돌렸는지가 구분된다.**

G-AUDIT 은 "복원 리허설을 수행했는가" 같은 것을 판정할 수 없고 "기록이 있고 기한 내인가" 만 판정할 수 있다.
같은 논리가 여기에도 적용된다. 이 디렉터리가 비어 있으면 로컬 게이트는 설계상으로만 존재하는 것이다.

다만 **이 기록의 존재를 점검하는 항목은 아직 등록되어 있지 않다.**
넣으려면 `fresh-market/.github` 의 품질 속성 문서에 항목을 추가하고 `items.yml` 을 다시 생성해야 한다.

## 무엇이 들어 있는가

각 파일은 화면에 낸 것과 같은 내용이다. 요약본이 아니다.

| 절 | 내용 |
|----|------|
| 머리말 | 계정, 시각, 커밋 SHA, **기준 저장소 SHA**, 매칭 규칙, 활성 항목 수 |
| 빌드 게이트 | 커버리지와 정적 분석 결과 |
| VIOLATION | 항목별 파일, 줄, 문제, 고치는 법 |
| CONFLICTING_BASELINE | 어긋나는 양쪽 값 |
| INSUFFICIENT_EVIDENCE | 못 읽은 앵커 경로 |
| 집계 | verdict 별 건수 |

**기준 저장소 SHA 가 가장 중요하다.**
점검 항목은 계속 바뀌므로, 어느 시점의 기준으로 판정했는지 모르면 과거 기록을 다시 읽을 수 없다.

## 읽는 법

같은 항목이 여러 기록에서 `INSUFFICIENT_EVIDENCE` 로 반복되면 판정이 어려운 코드가 아니라
`anchors.yml` 의 앵커 목록이 부족한 것이다. 규칙을 고칠 신호다.

`--dry-run` 으로 규칙 변경의 효과를 먼저 확인할 수 있다.

```bash
python3 ../common/.github/llm-verify/run.py --mode judge --dry-run \
  --backend . --common ../common --infra ../infra --base HEAD~1 --head HEAD
```

## 정리

쌓이는 속도가 부담스러워지면 분기별로 정리한다.
**지우기 전에 G-AUDIT 주기를 확인한다.** 주기 안의 기록을 지우면 그 기간이 미수행으로 판정된다.
