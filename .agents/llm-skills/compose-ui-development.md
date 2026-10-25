---
type: skill
name: compose-ui-development
description: "Compose 화면/섹션/컴포넌트 구현·수정 공통 워크플로우."
---

# Compose UI 개발 워크플로우

Compose 화면, 섹션, 재사용 컴포넌트를 새로 만들거나 수정할 때 적용하는 공통 스킬입니다. 예를 들어 원격 API에서 아이템 목록을 불러와 `LazyColumn`으로 보여주는 화면을 구현/수정할 때 이 절차를 적용합니다.

## Step 1. 작업 범위와 경계 확인
- 수정 대상이 `Activity`, `Route`, `Screen`, `Section`, 재사용 컴포넌트 중 어디인지 먼저 분리합니다.
- 기존 화면의 네비게이션, 스크롤, 키보드, 시스템바 동작이 바뀔 수 있으면 영향 범위를 설명하고 사용자 승인을 받습니다.
- Compose 화면/host/component에서 처리하는 값은 Compose API로 해결합니다. 문자열은 `stringResource`/`pluralStringResource`, 상태는 `remember`/state hoisting, effect는 `LaunchedEffect`/`LifecycleEventEffect`/`LifecycleStartEffect`/`LifecycleResumeEffect`/`DisposableEffect`를 상황에 맞게 선택합니다.

## Step 2. Route-Screen 구조 설계
- `Route`는 ViewModel 주입, `collectAsStateWithLifecycle()`, 일회성 effect 수집, 네비게이션 콜백 연결만 담당합니다. UI state를 Flow/StateFlow에서 Compose `State`로 바꿀 때는 `collectAsStateWithLifecycle()`을 우선하고, `collectAsState()`는 lifecycle을 인식하지 못하므로 신규 Android Compose 코드에서 사용하지 않습니다.
- lifecycle 단일 이벤트만 필요하면 `LifecycleEventEffect`, suspend 수집이 없는 `ON_START`/`ON_STOP` 쌍과 cleanup은 `LifecycleStartEffect`, suspend 수집이 없는 `ON_RESUME`/`ON_PAUSE` 쌍과 cleanup은 `LifecycleResumeEffect`를 우선합니다.
- lifecycle observer, receiver, listener처럼 직접 등록/해제가 필요한 외부 callback은 `DisposableEffect`로 등록하고 `onDispose`에서 반드시 해제합니다.
- `LaunchedEffect`는 composition lifetime에 묶인 coroutine, animation/focus/scroll 같은 Compose-local one-off 작업에 사용합니다.
- lifecycle `STARTED` 이상에서만 suspend Flow를 collect하고 `STOPPED`에서 cancel/restart해야 하는 일회성 effect stream은 `LaunchedEffect` 안에서 `lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)`를 사용합니다. `try/finally`로 `stopHost()`, transient state clear 같은 cleanup을 보장합니다.
- 같은 lifecycle-aware suspend Flow collect 패턴이 반복되면 `Flow<T>.collectStartedEffect(minActiveState = Lifecycle.State.STARTED, ...)` 같은 좁은 확장 helper로 감쌀 수 있습니다. 기본 lifecycle 기준은 `STARTED`로 두고, 카메라/센서/foreground-only host처럼 실제로 resume 상태에서만 처리해야 하는 surface만 호출부에서 `RESUMED`로 올립니다.
- helper 내부는 `LaunchedEffect + repeatOnLifecycle(minActiveState)` 의미를 유지합니다. state 표현 목적이면 helper를 만들지 말고 `collectAsStateWithLifecycle()`을 사용합니다.
- `Screen`은 상태와 이벤트 람다만 받는 stateless Composable로 유지합니다.
- 하위 컴포넌트는 ViewModel이나 Repository에 직접 의존하지 않고, 필요한 값과 콜백만 파라미터로 받습니다.
- 콜백이 4개 이상으로 늘어나면 sealed event와 단일 `onEvent: (Event) -> Unit` 구조를 검토합니다.
- Route/holder 아래의 Section, Item, leaf component에는 전체 화면 `UiState` 대신 필요한 slice 또는 plain value를 전달합니다.
- Navigation, permission launcher, snackbar, focus effect, external activity launch는 Route/holder boundary에서 처리하고, Screen에는 상태와 user intent callback만 전달합니다.

## Step 3. 함수와 컴포저블 분리
- 하나의 Composable이 화면 조립, 상태 파생, 리스트 아이템, 입력 영역, 버튼 바를 동시에 담당하면 의미 단위로 분리합니다.
- 분리 기준은 재사용 가능성보다 읽기 쉬운 책임 경계입니다. `Screen`은 큰 레이아웃을 조립하고, `Section`/`Item`/`Input`/`BottomBar`는 자기 UI만 그리게 합니다.
- 파일 내부에서만 쓰는 헬퍼 Composable과 Modifier 확장 함수는 `private`으로 둡니다.
- 한 파일에 둘 수 있는 Composable 개수에는 제한이 없습니다. `@Composable` 함수는 `class`/`object` 같은 importable type이 아니므로 `.agents/llm-rules/tooling/kotlin-rules.md`의 "1파일 1 owner" 규칙 대상이 아닙니다. 응집된 `Screen`과 그 화면 전용 `private` `Section`/`Item`/`Input`/`BottomBar`는 한 파일에 함께 둘 수 있습니다. 분리는 개수가 아니라 책임 경계(다른 변경 이유, ViewModel/Route 같은 state owner와의 혼재, 재사용 범위)를 기준으로 판단합니다.
- 한두 줄짜리 단순 래핑, 호출부보다 더 모호한 이름, 아직 반복되지 않은 스타일만을 위한 분리는 만들지 않습니다.
- 함수와 Composable은 원칙적으로 300줄 이내를 목표로 하되, 길이만 줄이려고 의미 없는 wrapper를 만들지 않습니다. 화면 조립, text resolving, result dispatch, platform effect가 섞이면 목적 단위로 분리합니다.
- 동일한 UI surface를 화면, 상태, content/action 유무별로 새 Composable로 복제하지 않습니다. 변하는 축이 data/slot/callback으로 표현되는지 먼저 검토합니다.
- Composable 간 지역변수를 공유하지 말고, 공유 상태는 상위로 끌어올려 명시적으로 전달합니다.
- Feature-local block은 재사용보다 리뷰 가능성과 책임 경계를 위해 분리할 수 있습니다. 단일 모듈 앱이므로 shared component로 섣불리 승격하지 말고 feature-local로 유지합니다.
- 성능 문제를 해결하기 위해 component 경계를 바꾸는 경우, 불필요한 recomposition 측정 등 리컴포지션 근거를 먼저 확인합니다.

## Step 4. 시스템 인셋 및 키보드 처리
- target SDK 35+에서는 edge-to-edge가 기본 강제됩니다. 하위 Android 버전에서도 일관되게 동작하도록 Activity가 Compose 화면을 소유하는 경우 `onCreate()`에서 `setContent { ... }` 호출 전에 `enableEdgeToEdge()`를 적용합니다.
- 탭, 드래그, 입력, 주요 CTA처럼 사용자가 상호작용하는 요소에는 `Scaffold`의 `innerPadding`, `WindowInsets`, `safeDrawing`, `systemBarsPadding()` 등을 화면/컴포넌트 단위로 적용해 시스템 제스처 영역과 겹치지 않게 합니다.
- 텍스트 입력, 댓글, 검색, 폼처럼 IME가 올라오는 화면은 키보드 리스너를 새로 만들기 전에 입력 영역의 루트 또는 스크롤 컨테이너에 `Modifier.imePadding()` 적용을 우선 검토합니다.
- `LazyColumn`/`LazyRow`에서 마지막 입력 요소가 키보드나 네비게이션바에 가려질 수 있으면 content padding만 늘리지 말고, inset size `Spacer` 또는 `imePadding()` 조합으로 실제 inset 변화에 따라 움직이게 합니다.

## Step 5. Modifier 작성
- Modifier 순서는 결과에 직접 영향을 줍니다(앞 모디파이어 결과를 다음이 변형). 단일 정답 순서는 없으니 의도에 맞게 배치합니다. `Layout(padding/size) -> Shape(clip) -> Drawing(background) -> Interaction(clickable)`는 흔한 출발점이지만, 예컨대 클릭 리플/터치 영역이 padding까지 덮어야 하면 `clickable`을 `padding` 앞에 두는 식으로 의도에 따라 바꿉니다.
- 긴 Modifier 체인은 의미 있는 단위로만 `private fun Modifier.*` 확장 함수로 분리합니다. 단일 호출부라도 이름이 레이아웃 의도를 명확히 해주면 허용합니다.
- 인라인 FQN을 쓰지 말고 필요한 Compose API는 import로 정리합니다.
- Kotlin 2.x Compose compiler의 strong skipping과 lambda memoization을 전제로 합니다. 단순 callback forwarding이나 child composable에 넘기는 람다 안정화를 위해 `rememberUpdatedState`/wrapper lambda를 만들지 않습니다.
- `LaunchedEffect`, `LifecycleEventEffect`, `LifecycleStartEffect`, `LifecycleResumeEffect`, `DisposableEffect`, animation callback처럼 오래 살아 있는 effect가 재시작 없이 callback/value를 캡처해야 할 때만 `rememberUpdatedState` 필요 여부를 확인합니다.
- 고빈도 상태(scroll/animation/gesture)는 읽는 범위를 최대한 아래로 내리고, 필요할 때만 `derivedStateOf`를 사용합니다.
- `LazyColumn`, `LazyRow`, pager/grid item이 reorder/update/local state/animation을 가질 수 있으면 stable `key`와 가능한 `contentType`을 지정합니다. 아이템 목록은 `key = { it.id }`로 두고, 이미지 항목과 일반 아이템 항목처럼 서로 다른 뷰 타입이 섞이면 `contentType`으로 구분합니다.
- 원격 이미지는 Coil3 `AsyncImage`로 로드하고, 목록 내 고정 위치에 배치할 때는 항목 크기를 안정적으로 유지해 로드 전후 레이아웃이 흔들리지 않게 합니다.
- 반복 nav item, animated surface는 동적 content 때문에 크기가 흔들리지 않도록 안정적인 dimension을 둡니다.

## Step 6. Preview와 검증
- 신규 또는 의미 있게 변경된 `Screen`, `Section`, 재사용 컴포넌트는 가능한 한 `@Preview`를 추가합니다. `Route`처럼 ViewModel/DI/Navigation이 붙은 홀더는 Preview 대상에서 제외합니다.
- Preview는 앱 Theme(`AppTheme`)로 감싸고, loading/empty/error/long text 등 변경 범위에 맞는 상태를 포함합니다.
- 입력 화면은 IME 표시 시 CTA와 마지막 입력 요소가 가려지지 않는지 수동 확인 또는 UI/screenshot 검증 계획을 남깁니다.
- 작업 후 `.agents/llm-skills/code-simplification.md`로 불필요한 분리, 과한 추상화, 범위 이탈을 점검합니다.
- Reusable component 변경은 common state뿐 아니라 long text, disabled/error/selected, 작은 container 상태를 preview 또는 대체 확인으로 남깁니다.

## 공식 참고
- Edge-to-edge 설정: https://developer.android.com/develop/ui/compose/system/setup-e2e
- Compose window insets: https://developer.android.com/develop/ui/compose/system/insets-ui
- Compose side effects: https://developer.android.com/develop/ui/compose/side-effects
- Compose strong skipping: https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Lifecycle-aware Flow collection: https://developer.android.com/topic/libraries/architecture/coroutines
- `Modifier.imePadding()`: https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/imePadding
- Lifecycle-aware Compose effects: https://developer.android.com/topic/libraries/architecture/lifecycle#lifecycleeventeffect
