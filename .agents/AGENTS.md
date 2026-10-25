# Android 앱 — AI 에이전트 지침서

이 문서는 이 저장소에 투입되는 모든 AI 에이전트가 가장 먼저 읽는 **Master System Prompt**입니다.
`.agents/llm-rules`(지켜야 할 규칙)와 `.agents/llm-skills`(작업 절차)를 상황에 맞게 조합해 참고하세요.

## 핵심 규칙

- 답변은 **한국어**.
- 코드 수정 전 단계별 설명 먼저.
- `!!` / `GlobalScope.launch` / `runBlocking` 금지.
- `Log.d/e` 금지 → `Timber.d/e`.
- **화면 간 데이터 전달은 Nav3 `ResultEventBus` 사용** (공유 ViewModel/전역 변수 금지) → `llm-rules/ui/navigation-result.md`.
- 커밋/푸시는 사용자 명시 요청 시에만.
- 기존 동작 영향 시 사전 승인 필수.

명시되지 않은 사항은 합리적인 가설을 세우고 진행합니다. 화려한 방법보다 **가장 익숙하고 최소한의 방법**을 우선합니다.

## 1. 기술 스택 / 구조 (기본 가정)

이 지침이 가정하는 기본 스택입니다. 프로젝트에서 빼거나 바꿔도 되며, 그 경우 관련 문서의 해당 부분만 무시합니다.

- **모듈**: 단일 모듈 `:app` 기준 (멀티모듈이면 패키지 경계 규칙을 모듈 경계로 확장).
- **언어/UI**: Kotlin, Jetpack Compose (Material3).
- **DI**: Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`).
- **네트워크**: Retrofit + OkHttp + kotlinx.serialization.
- **이미지**: Coil (`coil3.compose.AsyncImage`).
- **네비게이션**: Navigation 3 (`NavDisplay` + `NavKey`). 화면 간 결과 전달은 `ResultEventBus`.
- **레이어 (단일 모듈 내 패키지)**:
  ```
  data/remote/api      Retrofit API 인터페이스
  data/remote/dto      @Serializable DTO + toDomain() 매퍼
  data/repository      Repository 구현체
  data/di              Hilt 모듈 (NetworkModule, RepositoryModule)
  domain/model         순수 Kotlin 도메인 모델
  domain/repository    Repository 인터페이스
  presentation         Activity, ViewModel, UiState, Composable
  presentation/nav     NavKey 정의, NavDisplay 호스트(AppNavHost), 화면 간 전달 데이터
  ```

## 2. 공통 준수 사항 (Core Rules)

- **한국어 응답**: 답변·주석·커밋 메시지 설명은 한국어로 작성합니다.
- **최소 수정 원칙**: 동작하는 코드를 무리하게 리팩토링하지 않고, 요구된 목적 달성에만 집중합니다.
- **사전 설명 의무**: 코드를 생성/수정하기 전에 어떤 파일을, 어떻게, 왜 바꾸는지 짧게 먼저 설명합니다.
- **오버엔지니어링 방지**: 단일 화면 전용 로직에 불필요한 Manager/UseCase/추상화를 만들지 않습니다. 규모가 작으면 ViewModel이 Repository를 직접 호출합니다.
- **커밋/푸시**: 사용자가 명시적으로 요청할 때만 합니다.

## 3. 작업별 라우팅 가이드

작업 성격에 맞는 문서를 먼저 읽고 코딩을 시작하세요.

| 작업 상황 | 규칙 (Rules: "무엇") | 절차 (Skills: "어떻게") |
|-----------|----------------------|--------------------------|
| 구현 전 계획 검증 / 모호한 요구 정렬 | — | `llm-skills/grilling.md` |
| 신규 기능 개발 흐름 | `llm-rules/data/layer-contract.md` | `llm-skills/feature-development.md` |
| Intent / 딥링크 / onNewIntent / 단일 인스턴스 | `llm-rules/tooling/android-workflow.md` | `llm-skills/android-state-and-side-effects.md` |
| ViewModel / UiState / StateFlow 설계 | `llm-rules/data/state-management.md`, `llm-rules/tooling/kotlin-rules.md` | `llm-skills/android-state-and-side-effects.md` |
| API / Repository / UI 오류 처리 | `llm-rules/data/layer-contract.md`, `llm-rules/tooling/kotlin-rules.md` | `llm-skills/android-error-handling.md` |
| Compose UI 구현 | `llm-rules/ui/compose-rules.md` | `llm-skills/compose-ui-development.md` |
| 화면 간 데이터 전달 / 결과 반환 (Nav3) | `llm-rules/ui/navigation-result.md` | — |
| DTO / mapper / 레이어 경계 | `llm-rules/data/layer-contract.md` | — |
| Hilt 모듈 / 스코프 배치 | `llm-rules/tooling/hilt-di.md` | — |
| Kotlin 관용 패턴 / 파일·타입 분리 | `llm-rules/tooling/kotlin-rules.md` | — |
| 작업 전반 행동 규칙 (확인→최소수정→검증) | `llm-rules/tooling/android-workflow.md` | — |
| 작업 완료 후 자가 점검 | `llm-rules/tooling/kotlin-rules.md` | `llm-skills/code-simplification.md` |

## 4. 코드 안전 체크리스트 (수정 전/후)

1. **Intent / 인스턴스**: 딥링크나 재진입 Intent를 다룬다면 `launchMode`와 `onNewIntent` 처리로 단일 Activity에서 상태가 갱신되는지 확인합니다.
2. **레이어 경계**: DTO를 presentation으로 유출하지 않고, `domain/model`로 매핑해 전달합니다. data/domain 레이어에 Compose 의존성을 두지 않습니다.
3. **상태/생명주기**: `GlobalScope.launch`·`!!`·`runBlocking` 금지. `viewModelScope`와 `collectAsStateWithLifecycle`을 사용합니다.
4. **오류 처리**: Repository에서 네트워크 예외를 `Result`/UiState로 변환하고, Composable/Activity에서 raw 예외를 직접 다루지 않습니다.
5. **빌드 검증**: 변경 후 `./gradlew :app:assembleDebug`(또는 `:app:compileDebugKotlin`)로 컴파일을 확인합니다.

## 5. 문서 목록

```
.agents/
├── AGENTS.md                              (이 문서)
├── llm-rules/
│   ├── data/layer-contract.md             레이어 흐름 / DTO·mapper / 패키지 소유 규칙
│   ├── data/state-management.md           StateFlow / UiState / ImmutableList
│   ├── tooling/kotlin-rules.md            Kotlin 관용 패턴 / 파일·타입 분리
│   ├── tooling/hilt-di.md                 Hilt 모듈 위치 / 스코프
│   ├── tooling/android-workflow.md        작업 전반 행동 규칙
│   ├── ui/compose-rules.md                Compose 패턴 / 성능
│   └── ui/navigation-result.md            화면 간 데이터 전달 = Nav3 ResultEventBus
└── llm-skills/
    ├── grilling.md                        구현 전 계획 검증 / 정렬 인터뷰
    ├── feature-development.md             신규 기능 구현 절차
    ├── android-state-and-side-effects.md  ViewModel / UiState / effect 설계
    ├── android-error-handling.md          API / Repository / UI 오류 처리
    ├── compose-ui-development.md          Compose UI 구현 절차
    └── code-simplification.md             단순화 자가 점검
```

새 문서를 추가하면 이 목록과 §3 라우팅 표를 함께 갱신하세요.
