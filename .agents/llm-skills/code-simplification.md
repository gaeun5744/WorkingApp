---
type: skill
name: code-simplification
description: "작업 직후 최소 가치 버전으로 복잡도 낮추는 단순화 검증 절차."
---

# 코드 단순화 검증 로직 (Code Simplification)

코드 작업 직후, 복잡도를 낮추고 유지보수성을 극대화하기 위해 수행하는 "최소 가치 버전(Minimum Valuable Version)" 리팩토링 검증 절차입니다 (기존 `/simplify` 명령어 내재화).

이 문서는 작업 직후 코드 단순화 관점에서 복잡도와 과설계를 다시 점검하는 기준입니다.

## 1. YAGNI (You Aren't Gonna Need It) 점검
- 요구사항에 없는 '미래를 대비한' 불필요한 유연성, 인터페이스 추상화가 들어가지 않았는지 점검합니다.
- 오버엔지니어링된 코드가 있다면 즉시 쳐냅니다.
- 신규 UseCase, Repository, reducer, module, shared component가 실제 product rule, side effect, cache/platform boundary, 테스트 경계를 보호하는지 확인합니다.
- 호출자가 하나뿐인 abstraction은 이름/책임이 명확하더라도 일단 local helper나 feature-local component로 유지합니다.
- 성능/메모리 개선 명목으로 cache, pool, singleton, module split, dependency를 추가했다면 측정 근거와 해제/무효화 정책이 있는지 확인합니다.
- public API에 내부 pipeline 단계(`Parser`, `Resolver`, `Planner`, `Policy`, `Executor`)를 노출했다면 caller가 직접 조립해야 하는 실제 요구가 있는지 확인합니다.
- module 내부에서만 쓰는 parser, mapper, policy, factory, default helper가 visibility 없이 public으로 남아 있지 않은지 확인합니다. 외부 caller가 필요 없으면 `internal`, 파일 전용이면 `private`로 닫습니다.
- route/deep link/result/feedback을 모두 받는 범용 router 모델은 현재 사용 경로를 단순하게 표현하는지, 아니면 미래 가능성을 담은 과설계인지 확인합니다.

## 2. 함수 및 클래스 책임 분리 (Single Responsibility)
- 하나의 함수나 컴포저블이 너무 많은 일을 하고 있다면, 의미 단위(기능 블록)로 분리합니다.
- 분리된 헬퍼 함수는 가능하면 `private`으로 캡슐화합니다.
- top-level helper/type으로 분리했지만 같은 module 내부에서만 쓰이면 public 대신 `internal`로 캡슐화합니다.
- 분리는 재사용 목적이 아니라 읽기 쉬운 책임 경계 목적일 수 있습니다. 이 경우 shared로 올리지 말고 feature-local/private로 둡니다.
- 함수는 원칙적으로 300줄 이내를 목표로 합니다. 300줄을 넘으면 목적이 하나인지, side effect와 UI 조립이 섞였는지, mapping/resolving/dispatch가 한곳에 뭉쳤는지 먼저 점검합니다.
- 길이를 줄이기 위해 `setupX`, `handleX`, `renderX` 같은 모호한 helper를 만들지 않습니다. 분리된 함수 이름은 분리 이유를 설명해야 합니다.
- 선언형 UI 조립이나 단순 mapping처럼 300줄을 넘겨도 한 목적을 유지하는 경우에는 파일/함수 분리보다 readability와 검증 가능성을 우선합니다.
- 한 파일에 parser, mapper, route plan, executor, result callback, alert/toast feedback이 섞였다면 “단순한 파일”이 아니라 책임이 뭉친 것입니다. `.agents/llm-rules/tooling/kotlin-rules.md` 기준으로 나눕니다.

## 3. Kotlin/Compose 관용구 점검
- 불필요한 제어문(`if-else`)을 `when`이나 `?.let` 등으로 간결하게 줄일 수 있는지 확인합니다.
- Modifier 체이닝이 길다면 커스텀 Modifier 확장 함수로 분리할 수 있는지 고려합니다.
- Compose 내부에서 resource, state, effect를 다룰 때 레거시 `Context`/View helper로 우회하지 않았는지 확인합니다.

## 4. 자가 진단 질문
- "주니어/미들 레벨 개발자가 이 코드를 읽었을 때 의도를 한 번에 파악할 수 있는가?"
- 아니라면, 주석을 달기 전에 네이밍이나 로직 흐름을 직관적으로 개선합니다.
- "요구사항을 해결하는 데 필요한 코드만 남아 있는가?"
- "방향성이 애매한 구조 변경을 사용자 검토 없이 진행하지 않았는가?"
- "이 공통화가 caller별 boolean flag나 nullable option을 늘리고 있지 않은가?"
- "product copy, route, analytics, permission policy가 shared component 안으로 숨어 들어가지 않았는가?"
- "함수, 파일, 패키지 분리가 목적/owner/dependency direction을 드러내는가?"
- "한 package에 API contract, default implementation, DI, fake, assertion이 모두 들어가 있지 않은가?"
- "test double이나 assertion helper가 production adapter/import와 섞여 테스트 경계를 흐리지 않는가?"
- "동작 변경 없는 리팩터라면 최소 한 caller 또는 기존 focused test로 등가성을 확인했는가?"
- "module split이나 dependency 추가가 실제 boundary/cost를 줄이는가, 아니면 ceremony를 늘리는가?"
