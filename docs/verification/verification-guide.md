# 검증 시스템 실행 방법

## 실행

**로컬.** 커밋한 뒤 backend 디렉터리에서 친다.

```
/v-commit
```

범위를 바꾸려면 인자를 준다.

| 인자 | 범위 |
|---|---|
| 없음 | 아직 push 하지 않은 커밋 전부 |
| `HEAD` | 최신 커밋 1개 |
| `HEAD~5` | 최신 커밋 5개 |

인자는 **몇 개를 볼지**를 뜻한다. git 의 `base..head` 와 다르게 읽는다.

**CI.** **PR 을 열었을 때만** 돈다. 칠 것이 없다.
PR 에 push 를 더하면 다시 돈다. main 에 직접 push 하면 아무것도 안 돈다.

**처음이라면** 아래 [준비](#준비) 를 먼저 한다. 옆에 없으면 `/v-commit` 이 물어보고 받는다.

---

코드 품질 점검을 LLM 에게 맡긴다. 점검 항목은 세 저장소에 나뉘어 총 567건이고, 판정 대상은 backend 코드다.

```
커밋하면      로컬에서 Claude 가 본다   (/v-commit 을 쳐야 돈다)
PR 을 열면    CI 에서 gemini 가 본다    (자동)
둘 다         병합을 막지 않는다
```

병합을 막는 것은 커버리지(service 패키지 메서드 100%)와 SonarQube 신규 Blocker 0건뿐이다.

칠 수 있는 명령은 [verification-commands.md](./verification-commands.md),
지금 무엇을 검증하고 있는지는 [verification-status.md](./verification-status.md),
결과의 실물은 [verification-example.md](./verification-example.md),
무엇이 언제 도는지는 [verification-workflow.md](./verification-workflow.md),
무엇으로 이루어져 있는지는 [verification-architecture.md](./verification-architecture.md),
설계 근거는 [qa-llm-verification.md](https://github.com/fresh-market/.github/blob/main/docs/software-quality/qa-llm-verification.md) 에 있다.

## 준비

**backend 만 clone 해도 된다.**

```bash
git clone https://github.com/fresh-market/fm-backend.git backend
```

판정 기준 567건 중 317건이 다른 두 저장소에 있다.
`/v-commit` 은 그 둘을 세 단계로 찾는다.

| 순서 | 위치 | 어떻게 다루나 |
|------|------|---------------|
| 1 | 옆에 clone 된 것 | 디렉터리 이름이 아니라 `items.yml` 의 `source` 로 찾는다. **그대로 쓴다** |
| 2 | `~/.cache/llm-verify/` | **원격 최신에 맞춘 뒤** 쓴다 |

없으면 한 번 물어보고 받는다 (약 1.3MB, 공개 저장소라 인증 불필요). 다음부터는 묻지 않는다.

**2번은 판정 전에 항상 원격 최신으로 맞춘다.** 낡은 기준으로 판정하는 일이 없다.
네트워크가 안 되면 받아 둔 것으로 진행하고 그 사실을 알린다.

1번은 사용자가 관리하는 저장소이므로 **갱신하지 않는다.** 기준을 바꾸려면 직접 `git pull` 한다.

어느 시점의 기준으로 판정했는지는 결과와 기록에 남는다.

```
기준: common @ dfe9fd9, infra @ aa74a76
```

## 로컬에서 도는 것

```
1  ./gradlew check      커버리지와 정적 분석. 알림만
2  push 하지 않은 커밋의 diff
3  판정                 바꾼 파일에 해당하는 항목 전부
4  기록 저장            backend/docs/llm-review/<계정>_<YYYYMMDD-HHMMSS>_llm-review.md
```

작업 중 여러 번 돌려도 된다. 중간 상태에서 위반이 나오는 것은 정상이고, 기록은 초 단위로 구분되어 덮어쓰지 않는다.

## CI

**PR 에서만 돈다.** 할 일은 없다.

| 언제 | 도는가 |
|------|--------|
| PR 생성 | 돈다 |
| PR 에 push (`synchronize`) | 돈다 |
| PR 을 닫았다 다시 열기 | 돈다 |
| **main 에 직접 push** | **안 돈다** |

마지막 줄이 지금 열려 있는 구멍이다.
추후 main 과 develop 을 보호해 **PR 병합으로만 반영**하도록 막을 예정이며, 그전까지는 직접 push 로 게이트를 건너뛸 수 있다.

`registry-check.yml` 은 조건이 하나 더 붙는다. **점검 항목 문서나 `items.yml` 을 건드린 PR 에서만** 돈다.
Java 코드만 고친 PR 에서는 실행조차 되지 않는다.

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
되짚어 볼 필요가 있으면 로컬에서 `/v-commit` 을 돌려 기록을 남긴다.

로컬 기록에는 **어느 저장소의 어느 시점 기준으로 판정했는지**가 함께 남는다.
옆 저장소를 썼는지 캐시를 썼는지도 경로로 구분된다.

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
계정: <계정명>
시각: <ISO 8601>
커밋: <전체 SHA>
범위: <base>..<head>
기준 저장소:
  common: <커밋 SHA>  <경로>
  infra: <커밋 SHA>  <경로>
매칭 규칙: [<규칙 id>]
활성 항목: <n> (backend <a>, common <b>, infra <c>)
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
매칭 규칙   <걸린 규칙>
활성 항목   <n>건
  1단계    <a>건
  2단계    <b>건
앵커 파일   읽음 <x>, 부재 <y>, 실패 <z>
확정값      <m>건 또는 불필요
```

**숫자를 여기 적어 두지 않는다.** 항목이 늘거나 앵커 규칙이 바뀌면 값이 달라지는데,
문서에 박아 두면 조용히 어긋난다. 실제 값은 위 명령이 알려 준다.

## 점검 항목을 고쳤을 때

`items.yml` 은 가이드 문서에서 생성된 파생물이다. **문서를 고쳤으면 다시 생성해야 한다.**
안 하면 문서에는 있는데 게이트는 모르는 항목이 생긴다.

```bash
cd ../common/.github/llm-verify

python3 gen_items.py ../../docs/software-quality 'qa-*.md' common \
        -o items.yml
python3 gen_items.py ../../../backend/docs/code-architecture '*-guideline.md' backend 코드 \
        -o ../../../backend/.github/llm-verify/items.yml
python3 gen_items.py ../../../infra/docs/infra-review '*-guideline.md' infra 코드 \
        -o ../../../infra/.github/llm-verify/items.yml
```

`--check` 를 주면 파일을 쓰지 않고 어긋났는지만 본다.

```
OK  backend 250건. 문서와 레지스트리가 일치한다
```

**재생성을 잊어도 CI 가 잡는다.** 세 저장소 모두 `registry-check.yml` 이 이 검사를 돌린다.
결정론적이라 오탐이 없으므로 **LLM 게이트와 달리 병합을 막는다.**
문서를 안 고친 PR 에서는 아예 실행되지 않으므로 평소에는 부담이 없다.

문서에 적는 형식은 이렇다. 층위 태그는 common 에만 붙인다.

```markdown
점검 항목
* `[코드]` `SEC-1-07` 세션 고정 공격을 막는가       <- common
* `EJ-1-07` 인스턴스화를 막으려면 private 생성자를 쓰는가   <- backend, infra
```

나머지 필드(`gate`, `domains`, `ci_stage` 등)는 `gen_items.py` 가 규칙으로 채운다.
`level` 에서 `gate` 가 정해지므로 **층위 태그를 잘못 붙이면 판정 시점이 바뀐다.**

## 안 될 때

| 증상 | 조치 |
|---|---|
| `/v-commit` 이 기준 저장소를 받겠다고 묻는다 | 처음 한 번만 묻는다. 승인한다 |
| 받기를 거절해 멈췄다 | 승인하거나, `common` 과 `infra` 를 옆에 clone 한다 |
| "원격을 확인하지 못했습니다" | 네트워크가 안 된다. 받아 둔 기준으로 판정한다 |
| 옆 저장소가 낡았다 | 1번은 자동 갱신하지 않는다. 직접 `git pull` 한다 |
| 활성 항목이 유난히 적고 규칙이 `on_no_match` 다 | 정상이다. 문서만 고쳤을 때 그렇다 |
| 같은 항목이 계속 `INSUFFICIENT_EVIDENCE` | `backend/.github/llm-verify/anchors.yml` 의 `anchors` 에 파일 추가 |
| 2단계가 매번 건너뛰어진다 | 코멘트의 사유를 보고 로컬로 대신 본다 |
| CI 워크플로가 빨갛다 | `GEMINI_API_KEY` 시크릿 등록 (조직 또는 `fresh-market/fm-backend`) |
| PR 을 열었는데 아무것도 안 돈다 | `registry-check` 는 문서를 건드린 PR 에서만 돈다 |
| main 에 push 했는데 아무것도 안 돌았다 | 정상이다. CI 는 PR 에서만 돈다 |
| 같은 지적이 매 PR 마다 나온다 | `common/.github/llm-verify/known-conflicts.yml` 에 등록 |

**지금은 아직 판정할 대상이 없다.** backend 에 Java 코드가 없어 모든 규칙이 기본 집합으로 떨어지고,
`build.gradle` 에 JaCoCo 와 SonarQube 설정이 없어 빌드 게이트가 돌지 않는다.
