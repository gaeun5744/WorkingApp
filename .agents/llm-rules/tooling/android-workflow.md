---
type: rule
description: "Android 작업 시 LLM 에이전트 공통 행동 규칙 (구현 전 확인·최소 수정·검증)."
---

# Android Agent Workflow Guidelines

Android 작업에서 LLM agent가 따라야 할 공통 행동 규칙입니다. Kotlin/Compose/ViewModel/Retrofit/Hilt/Gradle 작업 전반에 적용합니다.

## 1. 구현 전에 확인한다

가정이 있으면 먼저 드러냅니다. 모호한 요구를 조용히 한 방향으로 해석하지 않습니다.

- 화면 문제는 `Compose UI`, `ViewModel state`, `Intent/launch args`, `lifecycle` 중 어디인지 나눠서 확인합니다.
- 딥링크 문제는 `Intent action/data 파싱`, `launchMode/onNewIntent`, `파라미터 추출` 중 어디인지 분리합니다.
- API 문제는 `Retrofit endpoint`, `Repository`, `DTO -> domain 매핑`, `에러/빈 응답 처리`를 분리해서 확인합니다.
- 명세와 코드가 다르면 명세 기준, 현재 코드 기준, 필요한 수정 범위를 명확히 말합니다.

### 1-1. 병렬 서브에이전트 판단

단일 모듈·소규모 작업이면 대부분 single-agent serial로 진행합니다. 다만 서로 독립적인 read-only 탐색면이 넓을 때는 lead agent가 병렬 탐색을 검토할 수 있습니다. 예: "딥링크 파싱 코드 후보 탐색"과 "API/Retrofit 설정 탐색"을 동시에.

병렬 구현은 두 작업자가 같은 Activity, ViewModel, Gradle file, DI module을 고치거나, 한 작업자가 미확정 model/contract를 바꾸고 다른 작업자가 그 shape를 소비해야 하면 쓰지 않습니다. 이 경우 serial로 진행합니다.

## 2. 가장 작은 수정으로 해결한다

요청받은 문제를 해결하는 최소 변경을 우선합니다.

- Retrofit path/응답 변경은 `ApiService`와 필요한 DTO만 수정합니다.
- ViewModel 상태 전이는 해당 ViewModel에 한정합니다.
- Compose UI 수정은 해당 Composable 또는 가까운 UI model까지만 건드립니다.
- 단일 화면 전용 로직에 새 Manager/UseCase/공통 abstraction을 만들지 않습니다.
- Gradle dependency, Hilt module, shared utility 추가는 기존 구조로 해결할 수 없을 때만 합니다.
- 새 파일을 만들거나 기존 파일에 책임을 추가할 때는 `.agents/llm-rules/tooling/kotlin-rules.md`의 파일/타입 분리 기준을 따릅니다.
- 딥링크 파싱, API 호출, UI 렌더링 경계 작업은 parser, repository, Composable을 같은 파일에 몰아넣지 않습니다.

## 3. 외과적으로 수정한다

모든 변경 라인은 현재 요청과 직접 연결되어야 합니다.

- 주변 코드 formatting, deprecated warning, unrelated lint warning은 정리하지 않습니다.
- 기존 dead code가 보이면 언급만 하고, 요청받지 않았으면 삭제하지 않습니다.
- 내 변경으로 생긴 unused import/변수/함수만 제거합니다.
- 기존 프로젝트 스타일을 우선합니다. 마음에 들지 않아도 임의로 패턴을 바꾸지 않습니다.
- 기존 동작 영향이 큰 변경은 먼저 영향 범위를 설명하고 승인받습니다.
- 파일을 쪼갤 때는 package depth를 늘리는 게 목적이 아니라 변경 이유를 분리하는 게 목적입니다. 세부 fail 기준은 Kotlin 파일/타입 분리 규칙을 source of truth로 봅니다.

## 4. 검증 기준을 정하고 반복한다

작업을 검증 가능한 목표로 바꾼 뒤 끝까지 확인합니다.

| 작업 | 권장 검증 |
| --- | --- |
| Retrofit endpoint / DTO 변경 | `:app:compileDebugKotlin` + 응답 매핑 수동 확인 |
| ViewModel state 변경 | state 전이 경로 코드 확인, `:app:compileDebugKotlin` |
| 딥링크/Intent 파싱 변경 | `:app:assembleDebug` 후 `adb` 딥링크로 실행 확인 |
| Compose UI 변경 | `@Preview` 확인, 필요 시 기기/에뮬레이터 manual smoke |
| Hilt/KSP/Gradle 변경 | `:app:compileDebugKotlin` 또는 `:app:assembleDebug` |

## 5. Android에서 자주 하는 실수

- 딥링크가 여러 번 와도 Activity 인스턴스가 하나만 유지되는지(`singleTask`/`singleTop` + `onNewIntent`) 확인합니다. `onCreate`만 보고 끝내지 않습니다.
- `onNewIntent`에서 `setIntent()`를 호출하지 않아 이후 `getIntent()`가 과거 Intent를 반환하는 실수를 주의합니다.
- 네트워크 호출 성공과 빈 목록/에러 응답을 구분합니다. 200이어도 빈 배열일 수 있습니다.
- `LaunchedEffect`, lifecycle-compose effect, `collectAsStateWithLifecycle` 변경은 recomposition과 lifecycle start/stop 영향을 확인합니다.
- `runBlocking`, `GlobalScope.launch`, `!!`, 메인 스레드 네트워크 호출을 사용하지 않습니다.

## 6. 작업 계획 형식

복잡한 작업은 아래처럼 짧게 계획하고 진행합니다.

```text
1. 원인 분리 -> verify: Intent/코드 경로/API 계약 확인
2. 최소 수정 -> verify: 관련 코드 경로 확인
3. 회귀 확인 -> verify: :app:compileDebugKotlin 또는 :app:assembleDebug 실행
```

---
*참조: 이 문서는 Android 작업 방식에 대한 공통 규칙입니다. 더 구체적인 코드 규칙은 Kotlin, Compose, Hilt 문서를 함께 따르세요.*
