# 빌드 게이트 (guideline)

`build.gradle`, `settings.gradle`, `lombok.config` 를 고치는 PR에 적용한다.
설계 근거는 [build-gate-rationale.md](./build-gate-rationale.md) 에 있다.

**이 문서의 항목은 LLM이 아니라 도구가 판정한다.** 결정론적이므로 병합을 차단해도 근거가 있다.

| 게이트 | 판정 주체 | 조건 | 동작 |
|--------|-----------|------|------|
| 커버리지 | Gradle `jacocoTestCoverageVerification` | `*.domain.service.*` 메서드 100% | **병합 차단** |
| 정적 분석 | SonarQube Quality Gate | **신규 Blocker 이슈 0건** | **병합 차단** |

## 1. 커버리지

점검 항목
* `BLD-1-01` JaCoCo 대상이 `*.domain.service.*`로 좁혀져 있는가
  `includes`로 좁히므로 exclude 목록이 필요 없다. config, dto, entity, Q클래스가 자동으로 빠진다.
* `BLD-1-02` 판정 단위가 클래스별(`element = 'CLASS'`), 카운터가 메서드(`counter = 'METHOD'`)인가
* `BLD-1-03` 기준이 `minimum = 1.00`인가
* `BLD-1-04` 단위 테스트와 통합 테스트의 `.exec`를 합산하는가
  각각 따로 보면 어느 쪽도 100%를 못 넘지만 합치면 넘는 경우가 대부분이다.
* `BLD-1-05` `lombok.config`에 `lombok.addLombokGeneratedAnnotation = true`가 있는가
  없으면 `@RequiredArgsConstructor`가 만든 생성자가 분모에 들어가 게이트가 Lombok 생성 코드에 좌우된다.
* `BLD-1-06` `check`가 `integrationTest`와 `jacocoTestCoverageVerification`에 의존하는가
* `BLD-1-07` `jacocoTestReport`가 `sonar` 태스크보다 먼저 도는가
  순서가 바뀌면 SonarQube에 커버리지가 0으로 표시된다.

## 2. 정적 분석

점검 항목
* `BLD-2-01` SonarQube Quality Gate에서 커버리지 조건을 제거했는가
  판정 주체가 둘이면 기본 게이트값(신규 코드 80% 등)이 Gradle 기준과 충돌한다.
* `BLD-2-02` 정적 분석 차단 조건이 신규 `Blocker` 이슈 0건인가
  `Blocker`는 프로덕션에서 애플리케이션을 망가뜨릴 높은 확률의 버그를 뜻한다. 병합을 막을 근거가 되는 것은 이 등급뿐이다.
  그 아래 등급은 차단하지 않고 경고로만 표시한다.
* `BLD-2-03` 브랜치 보호의 필수 상태 검사에 두 게이트가 등록되어 있는가
  이것이 두 게이트를 강제하는 유일한 수단이다.

```gradle
jacocoTestCoverageVerification {
    executionData.setFrom fileTree(layout.buildDirectory.dir('jacoco')).include('*.exec')
    violationRules {
        rule {
            element = 'CLASS'
            includes = ['*.domain.service.*']
            limit {
                counter = 'METHOD'
                value = 'COVEREDRATIO'
                minimum = 1.00
            }
        }
    }
}

check.dependsOn integrationTest, jacocoTestCoverageVerification
```

### 10.1 100% 기준과 "커버리지를 목표로 삼지 말라"는 원칙의 관계

backend `unit-testing-guideline.md`의 `UT-6-03`은 "커버리지 숫자 자체를 목표로 삼지 않는가"를 묻는다.
표면상 100% 강제와 충돌해 보이지만 **재는 것이 다르다.**

| | 재는 것 | 보장하는 것 |
|---|---|---|
| METHOD 100% | **범위** | 모든 메서드에 테스트가 한 번은 지나갔다 |
| `UT-6-03` | **깊이** | 그 테스트가 실제로 검증하는가 |

METHOD 카운터는 메서드가 호출되었는지만 본다. 100줄 중 1줄만 지나가도 커버된 것으로 계산된다.

```java
public void placeOrder(OrderCommand cmd) {
    validate(cmd);              // 여기서 예외 발생
    stockService.deduct(cmd);   // 실행 안 됨
    orderRepository.save(...);  // 실행 안 됨
}
```

**실패 경로만 테스트해도 이 메서드는 100%로 계산된다.**
그래서 깊이 검증은 게이트가 아니라 코드 리뷰와 테스트 작성 규칙이 맡는다. 두 항목은 역할이 겹치지 않는다.

특히 **조건부 UPDATE의 `affected rows == 0` 분기는 정합성 최종 방어선(INF-1-05)이므로 반드시 실패 경로 테스트를 함께 작성한다.**

### 10.2 로컬에서도 같은 게이트를 돌린다

push 전에 `./gradlew check`로 확인한다. CI에서 처음 알면 이미 PR을 연 뒤다.

**기준이 100%라 여유가 없다.** 새 메서드를 하나 추가하고 테스트를 빠뜨리면 그 순간부터 모든 병합이 막힌다.
의도된 엄격함이지만, 로컬에서 먼저 돌리지 않으면 CI 실패로 알게 되어 왕복이 생긴다.


## 3. 관련 문서

* 설계 근거: [build-gate-rationale.md](./build-gate-rationale.md)
* 기술 스택: [tech-stack.md](../tech-stack.md)
* 인프라 제약: `LGU-2/infra` 의 `docs/infra-review/code-guideline.md`
