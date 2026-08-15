---
description: G-LOCAL. 마지막 커밋의 변경분을 세 저장소의 점검 항목으로 판정한다
allowed-tools: Bash(git *), Bash(./gradlew *), Bash(*/verify.sh*), Bash(mkdir *), Bash(ls *), Bash(test *), Bash(find *), Bash(sed *), Read, Glob, Grep, Write
---

# G-LOCAL

절차는 `docs/verification/g-local.md` 에 있다. **그 문서를 읽고 따른다.**

이 파일은 Claude Code 진입점일 뿐이다. 절차 본문을 여기 두지 않는 이유는,
다른 CLI 에이전트를 쓰는 팀원도 같은 절차를 써야 하는데 이 디렉터리와 이 형식은
Claude Code 규약이기 때문이다.

범위 인자를 받으면 `verify.sh` 에 그대로 넘긴다. 없으면 스크립트 기본값(`HEAD~1..HEAD`): $ARGUMENTS

## 시작 전에 할 일

절차 1장이 부르는 `verify.sh` 의 위치를 찾는다. 그 스크립트가 들어 있는 저장소가 common 이다.

저장소는 **이름으로 찾지 않는다.** 조직명, 저장소명, clone 디렉터리 이름은 전부 바뀐다.
`.github/llm-verify/items.yml` 첫머리의 `source` 필드가 그 저장소가 어느 갈래인지 스스로 말한다.

```bash
# 글롭 대신 find 를 쓰는 이유가 둘이다.
#   common 저장소의 기본 clone 이름이 .github 라 숨김 디렉터리가 되는데 ../*/ 가 건너뛴다.
#   ../.*/ 를 더하면 zsh 에서 매칭이 없을 때 no matches found 로 죽는다.
# 파이프 대신 프로세스 치환을 쓰는 이유는, 파이프로 while 을 돌리면 서브셸이라
# COMMON 이 바깥으로 전달되지 않기 때문이다.
while IFS= read -r f; do
  if [ "$(sed -n 's/^source: *//p' "$f" | head -1)" = "common" ]; then
    COMMON=$(cd "$(dirname "$f")/../.." && pwd)
  fi
done < <(find .. -maxdepth 4 -path '*/.github/llm-verify/items.yml' 2>/dev/null)
: "${COMMON:=$HOME/.cache/llm-verify/common}"
```

옆에 clone 해 둔 것은 **그대로 쓰고 건드리지 않는다.** 사용자가 관리하는 저장소이므로
네트워크를 쓰지 않고 갱신하지도 않는다. 기준을 바꾸고 싶으면 사용자가 직접 `git pull` 한다.

## 옆에 없으면

처음이면 사용자에게 묻는다. 네트워크를 쓰고 디스크에 쓰는 동작이므로 말없이 하지 않는다.

```
common 과 infra 를 찾지 못했습니다.
~/.cache/llm-verify 에 받을까요? (약 1.3MB, 공개 저장소라 인증 불필요)
```

승인하면 받는다.

```bash
# 조직은 현재 저장소의 origin 에서 유도한다. 조직 이름이 바뀌어도 따라간다.
ORG=$(git remote get-url origin | sed -E 's#.*[/:]([^/]+)/[^/]+$#\1#')

# 저장소 이름이 나오는 유일한 자리다. 옆에 clone 이 없을 때만 쓰는 폴백이다.
# .github 는 GitHub 이 조직 단위로 예약한 이름이라 바뀌지 않는다.
INFRA_REPO=fm-infra

mkdir -p ~/.cache/llm-verify
git clone --depth 1 "https://github.com/$ORG/.github.git"      ~/.cache/llm-verify/common
git clone --depth 1 "https://github.com/$ORG/$INFRA_REPO.git"  ~/.cache/llm-verify/infra
```

이미 있으면 묻지 않고 최신으로 맞춘다.

```bash
git -C ~/.cache/llm-verify/common fetch --depth 1 -q origin HEAD && \
git -C ~/.cache/llm-verify/common reset --hard -q FETCH_HEAD
git -C ~/.cache/llm-verify/infra  fetch --depth 1 -q origin HEAD && \
git -C ~/.cache/llm-verify/infra  reset --hard -q FETCH_HEAD
```
