# 검증 시스템 실행 방법

## 실행

**로컬.** 커밋한 뒤 backend 디렉터리에서 친다.

```
/verify
```

범위를 바꾸려면 인자를 준다. 기본은 `HEAD~1..HEAD` 다.

```
/verify HEAD~3..HEAD
```

**CI.** PR 을 열거나 push 하면 자동으로 돈다. 칠 것이 없다.

**처음이라면** 아래 [준비](#준비) 를 먼저 한다. 세 저장소가 나란히 있어야 `/verify` 가 돈다.

---

코드 품질 점검을 LLM 에게 맡긴다. 점검 항목은 세 저장소에 나뉘어 총 567건이고, 판정 대상은 backend 코드다.

```
커밋하면   로컬에서 Claude 가 본다   (/verify 를 쳐야 돈다)
푸시하면   CI 에서 gemini 가 본다    (자동)
둘 다      병합을 막지 않는다
```

병합을 막는 것은 커버리지(service 패키지 메서드 100%)와 SonarQube 신규 Blocker 0건뿐이다.

결과의 실물은 [verification-example.md](./verification-example.md),
무엇이 언제 도는지는 [verification-workflow.md](./verification-workflow.md),
설계 근거는 [qa-llm-verification.md](https://github.com/LGU-2/.github/blob/main/docs/software-quality/qa-llm-verification.md) 에 있다.

## 준비

세 저장소를 **같은 부모 디렉터리에 나란히** 둔다. 로컬 검증이 상대 경로로 서로를 찾는다.

```bash
git clone https://github.com/LGU-2/.github.git common
git clone https://github.com/LGU-2/be.git       backend
git clone https://github.com/LGU-2/infra.git    infra
```

```
어딘가/
  common/     품질 속성 217건
  backend/    코드 관용 250건, 판정 대상
  infra/      인프라 제약 100건
```

`common` 이나 `infra` 가 없으면 backend 250건만 판정된다. 그래서 `/verify` 는 없으면 멈춘다.

## 로컬에서 도는 것

```
1  ./gradlew check      커버리지와 정적 분석. 알림만
2  마지막 커밋의 diff
3  판정                 바꾼 파일에 해당하는 항목 전부
4  기록 저장            backend/docs/llm-review/<계정>_<YYYYMMDD-HHMMSS>_llm-review.md
```

작업 중 여러 번 돌려도 된다. 중간 상태에서 위반이 나오는 것은 정상이고, 기록은 초 단위로 구분되어 덮어쓰지 않는다.

## CI

PR 을 열거나 push 하면 자동으로 돈다. 할 일은 없다.

코멘트는 push 마다 새로 달리지 않고 **같은 것이 갱신된다.**
코멘트는 위반을 둘로 나눈다.

```
이 PR 이 만든 위반    펼쳐서 보여 준다. 이것만 고치면 된다
기존 부채            접어 둔다. 이 PR 의 책임이 아니다
```

## 결과가 어디에 나오나

| 어디서 돌렸나 | 결과 |
|---|---|
| 로컬 | 터미널 화면 |
| 로컬 | `backend/docs/llm-review/<계정>_<YYYYMMDD-HHMMSS>_llm-review.md` |
| CI | PR 의 리뷰 코멘트 (하나가 계속 갱신된다) |
| CI | Actions 실행 화면의 **Summary** |

**CI 결과는 파일로 저장하지 않는다.** PR 코멘트와 Actions Summary 에만 남는다.
같은 코멘트를 갱신하므로 이력이 남지 않고, Actions 로그는 보존 기간이 지나면 사라진다.
되짚어 볼 필요가 있으면 로컬에서 `/verify` 를 돌려 기록을 남긴다.

```
backend/docs/llm-review/
  devjohnpark_20260806-174500_llm-review.md
  devjohnpark_20260806-181203_llm-review.md
  teammate_20260807-093011_llm-review.md
```

로컬 기록 파일은 화면에 나온 것과 같은 내용에 머리말이 붙는다.
어느 커밋을, 어느 시점의 점검 항목으로 판정했는지가 들어 있어 나중에 다시 읽을 수 있다.

```markdown
---
검증: G-LOCAL
계정: devjohnpark
시각: 2026-08-06T08:45:00Z
커밋: bd07e1ac15f2ca4257ecf7b976745c0f5d79d7eb
범위: HEAD~1..HEAD
기준 저장소:
  common: 61ad5797c7c0fc809ed504bf2073271f5e849841
  infra: 3e05b7f9933d654c20a30b3b64593252ab9a8501
매칭 규칙: [service]
활성 항목: 182 (backend 79, common 88, infra 15)
---
```

이 디렉터리는 **커밋한다.** 로컬 검증은 재량이라 안 돌려도 아무 일이 없고, 기록이 남아야 돌렸는지가 구분된다.
파일명에 계정과 초 단위 시각이 들어가므로 팀원끼리 충돌하지 않는다.

## 결과 읽기

| 값 | 뜻 | 할 일 |
|---|---|---|
| `VIOLATION` | 위반이다 | 고친다 |
| `OK` | 충족했다 | 없음 |
| `NOT_APPLICABLE` | 이 변경과 무관하다 | 없음 |
| `INSUFFICIENT_EVIDENCE` | 판정할 파일을 못 읽었다 | 반복되면 `anchors.yml` 의 앵커 목록 보강 |
| `CONFLICTING_BASELINE` | 확정값이 문서마다 다르다 | 팀이 어느 쪽인지 정한다 |
| `UNJUDGED` | 물어보지 않았다 | 로컬로 다시 본다 |

`OK` 와 `UNJUDGED` 는 다르다. 통과한 것과 안 본 것을 섞지 않기 위해 나눠 둔다.

LLM 판정이라 오탐이 있고 병합을 막지 않으므로, 틀렸다고 판단되면 그냥 병합해도 된다.

## 무엇이 켜지는지 미리 보기

앵커 규칙을 고쳤을 때 의도한 항목이 켜지는지 확인한다. LLM 을 부르지 않으므로 몇 번이든 돌려도 된다.

```bash
python3 ../common/.github/llm-verify/run.py --mode judge --dry-run \
  --backend . --common ../common --infra ../infra --base HEAD~1 --head HEAD
```

```
매칭 규칙   service
활성 항목   182건
  1단계    79건
  2단계    103건
앵커 파일   읽음 2, 부재 3, 실패 0
```

## 안 될 때

| 증상 | 조치 |
|---|---|
| `/verify` 가 시작하자마자 멈춘다 | `common`, `infra` 를 같은 부모 디렉터리에 clone |
| 활성 항목이 50건뿐이다 | 정상이다. 문서만 고쳤을 때 그렇다 |
| 같은 항목이 계속 `INSUFFICIENT_EVIDENCE` | `backend/.github/llm-verify/anchors.yml` 의 `anchors` 에 파일 추가 |
| 2단계가 매번 건너뛰어진다 | 코멘트의 사유를 보고 로컬로 대신 본다 |
| CI 워크플로가 빨갛다 | `GEMINI_API_KEY` 조직 시크릿 등록 |
| 같은 지적이 매 PR 마다 나온다 | `common/.github/llm-verify/known-conflicts.yml` 에 등록 |

**지금은 아직 판정할 대상이 없다.** backend 에 Java 코드가 없어 모든 규칙이 기본 집합으로 떨어지고,
`build.gradle` 에 JaCoCo 와 SonarQube 설정이 없어 빌드 게이트가 돌지 않는다.
