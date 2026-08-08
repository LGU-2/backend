---
description: G-LOCAL. 마지막 커밋의 변경분을 세 저장소의 점검 항목으로 판정한다
allowed-tools: Bash(git *), Bash(./gradlew *), Bash(gh api *), Bash(mkdir *), Bash(date *), Bash(ls *), Bash(test *), Read, Glob, Grep, Write
---

# G-LOCAL 검증

마지막 커밋의 변경분을 판정한다. **차단하지 않는다.** 작업 중 반복 실행하는 도구이므로 중간 상태에서 위반이 나오는 것이 정상이다.

설계 근거는 `LGU-2/.github` 의 `docs/software-quality/qa-llm-verification.md`, 실행 방법과 워크 플로우는 이 저장소의 `docs/verification/` 에 있다.

인자로 커밋 범위를 받으면 그것을 쓰고, 없으면 `HEAD~1..HEAD` 를 쓴다: $ARGUMENTS

## 0. 기준 저장소 위치 확인

판정 기준 567건 중 317건이 다른 두 저장소에 있다. 먼저 그 위치를 정한다.

`common` 과 `infra` 를 **따로 판단한다.** 하나만 없을 수도 있다.

```bash
ls ../common/.github/llm-verify/items.yml
ls ~/.cache/llm-verify/common/.github/llm-verify/items.yml
```

찾는 순서는 이렇다.

| 순서 | 위치 | 어떻게 다루나 |
|------|------|---------------|
| 1 | `../common` | 사용자가 직접 clone 해 둔 것. **그대로 쓰고 건드리지 않는다** |
| 2 | `~/.cache/llm-verify/common` | 아래 절차로 **원격 최신에 맞춘 뒤** 쓴다 |

**1번을 우선하는 이유**는 사용자가 관리하는 저장소이기 때문이다.
직접 clone 한 사람은 이 명령이 네트워크를 쓰지 않고, 그 저장소를 갱신하지도 않는다.
기준을 바꾸고 싶으면 사용자가 직접 `git pull` 한다.

`infra` 도 같은 순서로 정한다.

이후 단계에서는 여기서 정한 경로를 쓴다. 아래 본문의 `../common`, `../infra` 는 그 경로를 가리킨다.

### 옆에 없으면 받아서 최신으로 맞춘다

`~/.cache/llm-verify/` 에 두고 **판정 전에 항상 원격 최신으로 맞춘다.**

처음이면 사용자에게 묻는다. 네트워크를 쓰고 디스크에 쓰는 동작이므로 말없이 하지 않는다.

```
common 과 infra 를 찾지 못했습니다.
~/.cache/llm-verify 에 받을까요? (약 1.3MB, 공개 저장소라 인증 불필요)
```

승인하면 받는다.

```bash
mkdir -p ~/.cache/llm-verify
git clone --depth 1 https://github.com/LGU-2/.github.git ~/.cache/llm-verify/common
git clone --depth 1 https://github.com/LGU-2/infra.git   ~/.cache/llm-verify/infra
```

이미 있으면 묻지 않고 최신으로 맞춘다.

```bash
git -C ~/.cache/llm-verify/common fetch --depth 1 -q origin HEAD && \
git -C ~/.cache/llm-verify/common reset --hard -q FETCH_HEAD
git -C ~/.cache/llm-verify/infra  fetch --depth 1 -q origin HEAD && \
git -C ~/.cache/llm-verify/infra  reset --hard -q FETCH_HEAD
```

**`pull` 대신 `fetch` + `reset --hard` 를 쓴다.** 얕은 clone 이라 병합할 이력이 없고,
이쪽은 사람이 고칠 곳이 아니므로 원격을 그대로 덮는 것이 맞다.

받거나 맞춘 뒤 어느 시점인지 한 줄로 알린다. **캐시라는 말은 하지 않는다.**
사용자가 알아야 할 것은 "어느 시점의 기준으로 판정하는가" 뿐이다.

```
기준: common @ dfe9fd9, infra @ aa74a76
```

### 네트워크가 안 되면

받아 둔 것이 있으면 **그것으로 진행하고 사실만 알린다.**

```
원격을 확인하지 못했습니다. 받아 둔 기준으로 판정합니다: common @ dfe9fd9 (8월 8일)
```

아무것도 없으면 멈춘다. **없는 채로 진행하면 backend 250건만 판정하고 그 사실이 결과에 드러나지 않는다.**

## 1. 빌드 게이트 (G-BUILD 와 같은 기준, 여기서는 알림만)

```bash
./gradlew check
```

두 가지를 본다.

* `*.domain.service.*` 패키지 메서드 커버리지 100%
* 정적 분석 신규 `Blocker` 0건

미달이면 어느 클래스의 어느 메서드인지까지 보고한다. CI 에서는 이 두 가지가 **병합을 차단**하므로, 여기서 먼저 잡는 것이 목적이다.

`build.gradle` 에 JaCoCo 나 Sonar 설정이 없으면 그 사실 자체를 보고한다. `BLD-1-*` 과 `BLD-2-*` 10건이 이 설정을 점검 대상으로 삼는다.

## 2. 판정 범위 산출

```bash
git diff --name-only HEAD~1..HEAD
git diff HEAD~1..HEAD
```

CI 의 G-PR 과 범위가 다르다. G-PR 은 PR 누적 diff(`base...HEAD`)를 보고, 여기서는 **마지막 커밋 한 개**만 본다.
누적 판정은 CI 소관이므로 여기서 넓히지 않는다.

## 3. 앵커 규칙 매칭

`.github/llm-verify/anchors.yml` 을 읽는다.

변경 파일을 각 규칙의 `trigger` 글롭과 대조해 매칭된 규칙을 모두 모은다. 한 파일이 여러 규칙에 걸릴 수 있고, 그때는 전부 적용한다.

어떤 규칙도 안 걸리면 `defaults.on_no_match` 를 쓴다 (`levels: [코드]`, `prefixes: [EJ]`).

매칭 결과에서 셋을 얻는다.

| 산출 | 쓰임 |
|------|------|
| `anchors` | 5단계에서 함께 읽을 파일 |
| `activate` | 4단계 필터 조건 |
| `needs_baseline_values` | 6단계 확정값 로드 여부 |

## 4. 점검 항목 필터

세 레지스트리를 읽는다.

```
.github/llm-verify/items.yml              250건
../common/.github/llm-verify/items.yml    217건
../infra/.github/llm-verify/items.yml     100건
```

각 항목의 접두사(ID 의 첫 토큰)를 기준으로 세 조건을 **모두** 만족하는 것만 켠다.

1. `activate.prefixes` 에 접두사가 있는가
2. `activate.levels` 에 `level` 이 있는가 (비어 있으면 전부 통과)
3. `activate.chapters` 에 그 접두사가 있으면 `ch` 가 목록에 있는가 (없으면 전 장 통과)

`ci_stage` 는 무시한다. **로컬에서는 단계를 나누지 않고 활성 항목을 전부 판정한다.**

## 5. 앵커 파일 로드

3단계가 정한 `anchors` 글롭에 맞는 파일을 **diff 에 없어도** 읽는다.

이것이 부재 판정의 근거다. "타임아웃 설정이 없다" 를 말하려면 설정이 있을 법한 파일을 봐야 한다.
읽지 못한 앵커가 있으면 그 앵커에 의존하는 항목은 `INSUFFICIENT_EVIDENCE` 로 둔다. **통과시키지 않는다.**

## 6. 확정값 로드

매칭된 규칙 중 하나라도 `needs_baseline_values: true` 면 `../infra/docs/system-design/` 의 문서를 읽는다.
아니면 읽지 않는다.

## 7. 알려진 모순 로드

`../common/.github/llm-verify/known-conflicts.yml` 을 읽는다.

* `status: unresolved` 인 모순의 `affects` 에 있는 항목은 `CONFLICTING_BASELINE` 으로 두고 **양쪽 값을 함께 표기**한다. 한쪽을 골라 판정하지 않는다.
* `status: intentional` 은 모순이 아니다. `sources` 의 뒤쪽(확정값)을 기준으로 판정한다.
* 목록에 없는 모순을 발견하면 **새로 발견된 것이므로 보고**한다.

## 8. 판정 기준 본문 로드

활성 항목이 속한 문서만 읽는다. 각 항목의 `doc` 필드가 파일명이다.

```
../common/docs/software-quality/qa-*.md
docs/code-architecture/*-guideline.md
../infra/docs/infra-review/code-guideline.md
```

ID 와 제목만으로 판정하지 않는다. **본문에 판정 기준과 예외가 적혀 있다.**

## 9. 판정

활성 항목 하나하나에 대해 `verdict` 를 정한다.

| 값 | 언제 |
|----|------|
| `VIOLATION` | 위반을 확인했다 |
| `OK` | 충족을 확인했다 |
| `NOT_APPLICABLE` | 이 변경과 무관하다 |
| `INSUFFICIENT_EVIDENCE` | 판정에 필요한 증거가 입력에 없다 |
| `CONFLICTING_BASELINE` | 확정값이 문서마다 다르다 |

`UNJUDGED` 는 쓰지 않는다. 로컬에서는 활성 항목을 전부 판정하므로 이 값이 나올 자리가 없다.

**추측으로 `OK` 를 내지 않는다.** 근거 파일을 못 봤으면 `INSUFFICIENT_EVIDENCE` 다.
이 구분이 무너지면 게이트가 통과시킨 것과 안 본 것이 뒤섞여 지표가 무의미해진다.

### 설명은 한국어로 쓴다

점검 항목과 판정 기준이 한국어이므로 지적도 한국어여야 대조하기 쉽다.

**다만 아래는 원문 그대로 둔다.** 번역하면 검색과 대조가 깨진다.

```
항목 ID          SEC-1-01,  BLD-1-03
verdict          VIOLATION,  OK,  NOT_APPLICABLE,  INSUFFICIENT_EVIDENCE,  CONFLICTING_BASELINE
파일 경로         src/main/java/com/x/domain/service/OrderService.java
클래스와 메서드    OrderService.pay
설정 키와 값      maximum-pool-size: 10,  @Transactional
점검 항목 제목     문서에 적힌 문장 그대로
```

예를 들면 이렇게 쓴다.

```
SEC-1-01  리소스 접근 시 소유권 또는 권한을 검증하는가
  OrderService.java:4
  id 로 조회만 하고 호출자가 소유자인지 확인하지 않는다
  인증 주체의 식별자를 조회 조건에 포함한다
```

### 중복 지적 억제

항목에 `defers_to` 가 있으면, 그 대상 항목이 같은 코드에 대해 `VIOLATION` 이면 **이쪽은 발화하지 않는다.**
같은 문제를 두 번 지적하면 리뷰 신뢰도가 떨어진다.

## 10. 출력

```
G-LOCAL  <커밋 SHA 앞 7자리>  <메시지>

빌드 게이트
  커버리지   <통과 또는 미달 목록>
  정적 분석  <통과 또는 Blocker 목록>

매칭된 규칙  <규칙 id 나열>
활성 항목    <n>건  (backend <a>, common <b>, infra <c>)

VIOLATION <n>건
  <ID>  <제목>
    파일:줄
    무엇이 문제인가 한 줄
    어떻게 고치는가 한 줄

CONFLICTING_BASELINE <n>건
  <ID>  <제목>
    <문서 A>: <값>
    <문서 B>: <값>
    -> 결정 필요

INSUFFICIENT_EVIDENCE <n>건
  <ID>  <제목>  못 읽은 앵커: <경로>

OK <n>  NOT_APPLICABLE <n>
```

`VIOLATION` 만 자세히 쓰고 나머지는 건수와 ID 만 낸다.
`INSUFFICIENT_EVIDENCE` 가 계속 같은 항목에서 나오면 `anchors.yml` 의 앵커 목록이 부족한 것이므로 그 사실을 함께 말한다.

## 11. 기록 저장

화면에 낸 것과 **같은 내용**을 파일로 남긴다. 요약해서 저장하지 않는다.

```bash
mkdir -p docs/llm-review
LOGIN=$(gh api user -q .login 2>/dev/null || git config user.name)
STAMP=$(date +%Y%m%d-%H%M%S)
echo "docs/llm-review/${LOGIN}_${STAMP}_llm-review.md"
```

파일명은 `<깃허브 계정명>_<YYYYMMDD-HHMMSS>_llm-review.md` 다.
계정명을 넣는 이유는 팀원이 각자 로컬에서 돌리기 때문이고,
초 단위까지 넣는 이유는 한 커밋을 고쳐 가며 여러 번 돌리는 것이 정상 사용이기 때문이다.

`gh` 인증이 없으면 `git config user.name` 으로 떨어진다. 둘 다 없으면 `unknown` 을 쓰고 그 사실을 알린다.

파일 첫머리에 다시 만들 수 있는 정보를 넣는다. 없으면 나중에 이 기록이 무엇을 판정한 것인지 알 수 없다.

```markdown
---
검증: G-LOCAL
계정: <계정명>
시각: <ISO 8601>
저장소: <origin URL>
브랜치: <브랜치명>
커밋: <전체 SHA>
범위: <base>..<head>
기준 저장소:
  common: <커밋 SHA>  <경로>   # 옆 저장소인지 캐시인지 적는다
  infra: <커밋 SHA>  <경로>
매칭 규칙: [<규칙 id>]
활성 항목: <n> (backend <a>, common <b>, infra <c>)
---
```

**기준 저장소의 SHA 를 남기는 것이 핵심이다.**
점검 항목은 계속 바뀌므로, 어느 시점의 기준으로 판정했는지 모르면 과거 기록을 다시 읽을 수 없다.

경로도 함께 적는다. 옆 저장소를 썼는지 캐시를 썼는지에 따라 기준이 다를 수 있고,
캐시가 낡아 있었다면 그 기록만 보고도 알 수 있어야 한다.

저장 후 경로를 한 줄로 알린다.

```
기록: docs/llm-review/devjohnpark_20260806-174500_llm-review.md
```

### 이 디렉터리를 커밋하는가

**커밋한다.** 두 가지 이유다.

* 계정명으로 파일을 나누는 것은 여러 사람이 볼 때만 의미가 있다
* 로컬 판정은 재량이라 안 돌려도 아무 일이 없다. 기록이 남아야 돌렸는지가 구분된다

두 번째가 G-AUDIT 으로 이어질 자리이지만, **아직 이것을 점검하는 항목은 등록되어 있지 않다.**
넣으려면 `docs/software-quality/` 에 항목을 추가하고 `items.yml` 을 다시 생성해야 한다.

파일명이 계정과 초 단위 시각을 포함하므로 충돌하지 않는다.
쌓이는 속도가 부담스러워지면 분기별로 정리하되, **지우기 전에 G-AUDIT 주기를 확인한다.**

## 이 명령이 CI 와 다른 점

| | G-LOCAL (이 명령) | G-PR (CI) |
|---|---|---|
| 판정 주체 | Claude | gemini-2.5-flash |
| 범위 | 마지막 커밋 | PR 누적 diff |
| 단계 | 없음. 활성 항목 전부 | 1단계 backend, 2단계 common+infra 조건부 |
| 차단 | 안 함 | 안 함 |

**로컬을 건너뛰고 CI 2단계가 실패하면 common 과 infra 기준은 아무도 보지 않는다.**
그 조합이 실제로 일어나므로 커밋 후 이 명령을 돌리는 것이 권장된다.
