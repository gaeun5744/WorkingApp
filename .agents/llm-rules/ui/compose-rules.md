---
type: rule
description: "Jetpack Compose 패턴 및 성능 규칙 (Route-Screen, state hoisting, event sink, strong skipping)."
---

# Jetpack Compose Patterns & Performance Rules

UI 패턴 및 성능 최적화 지침 (NowInAndroid, Material 3 기준).

## 1. 아키텍처 및 상태 관리
- **Route-Screen 패턴**: 화면 진입점인 `Route` 컴포저블(ViewModel 주입, 네비게이션 콜백 처리)과 순수 UI인 `Screen` 컴포저블(상태와 이벤트 람다만 받음)을 분리합니다.
- **상태 관리**: UI 상태는 Sealed Interface를 활용해 정의하고, ViewModel에서 `StateFlow`로 노출합니다. Compose에서는 `collectAsStateWithLifecycle()`로 수집합니다.
- **이벤트 핸들링 (Event Sink)**: 콜백이 많아질 경우(4개 이상), Sealed Interface로 이벤트를 묶어 단일 `onEvent: (Event) -> Unit` 람다로 전달합니다.

## 2. 컴포넌트 작성 규칙
- **Modifier Ordering**: 모디파이어 순서는 결과에 직접 영향을 줍니다(앞 모디파이어가 만든 결과를 다음 모디파이어가 변형). 단일 정답 순서는 없으며 **의도에 맞게 배치**합니다. 예를 들어 클릭 리플/터치 영역이 padding까지 덮어야 하면 `clickable`을 `padding` **앞**에 두고, padding을 터치 영역에서 빼려면 뒤에 둡니다. `Layout(padding) -> Shape(clip) -> Drawing(background) -> Interaction(clickable)`는 흔한 출발점일 뿐 강제 규칙이 아닙니다.
- **Parameter Ordering (파라미터 순서)**: 다음 순서를 강제합니다.
  1. **필수 파라미터** (기본값 없음). 필수 콜백(`onClick` 등)도 필수이므로 이 그룹에 포함하며 `modifier` **앞**에 둡니다. 중요도 순 배치.
  2. **`modifier: Modifier = Modifier`** — 선택 파라미터 중 가장 앞.
  3. **선택 파라미터** (기본값 있음).
  4. **후행 `@Composable content` slot 람다** — 항상 마지막 (trailing lambda 문법 가능하도록).
  - 기존 코드 중 위반 사례는 점진적으로 정렬하며, 신규/수정 코드는 위 표준을 강제합니다.
- **Preview**: 신규 또는 의미 있게 변경된 `Screen`, `Section`, 재사용 컴포넌트는 `@Preview` 필수입니다. `Route`처럼 ViewModel/DI/Navigation이 붙은 홀더가 아니라 상태와 콜백만 받는 stateless Composable을 대상으로 합니다.
  - Preview는 앱 Theme로 감쌉니다.
  - 영향받은 상태를 커버합니다: content, loading, empty, error, long text 중 해당되는 상태.
  - 네트워크, DB, DI container, 실제 credential, random/current time, device-only service에 의존하지 않습니다.
  - ModalBottomSheet 등 **윈도우형 컨테이너**(IDE 미렌더)는 면제. 컨테이너는 content/container 분리 후 content를 Preview 합니다.
  - Preview 작성이 불가능하면 사유와 대체 검증(Compose UI test, manual smoke path 등)을 작업 보고에 남깁니다.
- **Type-Safe Navigation**: Compose Navigation 2.8+ 의 `@Serializable` 객체를 활용합니다.

## 3. 성능 최적화 가이드라인 (Performance Rules)

이 가이드라인은 코드를 분석하거나 최적화할 때 **판단 기준(Fact)**으로 작용합니다.

- **Strong Skipping 전제**:
  - 이 프로젝트는 Kotlin 2.x Compose compiler를 사용하므로 신규 Compose 코드에서 strong skipping과 lambda memoization이 기본 전제입니다.
  - 단순 callback 전달, 람다 파라미터 forwarding, 싼 파생값을 안정화하려고 `remember`, `rememberUpdatedState`, wrapper lambda를 추가하지 않습니다.
  - `rememberUpdatedState`는 `LaunchedEffect`, `DisposableEffect`, lifecycle effect, animation callback처럼 오래 살아 있는 effect가 재시작 없이 최신 값을 참조해야 할 때만 사용합니다.
- **Stability (`@Stable`, `@Immutable`)**:
  - 상태로 전달되는 객체는 가능한 불변성(`@Immutable`)을 가져야 합니다. 리스트는 `kotlinx.collections.immutable`의 `ImmutableList` 등을 사용합니다.
  - 외부 라이브러리의 클래스나 불완전한 상태 객체에는 명시적으로 `@Stable`이나 래퍼(wrapper)를 사용합니다.
- **Lazy Layout 최적화**:
  - `LazyColumn` / `LazyRow`의 `items`에는 반드시 안정적이고 고유한 `key`를 부여합니다. (예: 아이템 목록의 `key = { it.id }`)
  - 다양한 뷰 타입이 섞여있다면(예: 일반 아이템 항목 + 이미지 항목) `contentType`을 지정하여 뷰 재사용성을 극대화합니다.
- **Derived State (`derivedStateOf`)**:
  - 스크롤 상태와 같이 빈번하게 변경되는 상태에서 특정 조건(boolean 등)을 도출할 때는 `derivedStateOf`를 사용하여 불필요한 재조합(Recomposition)을 막습니다.
- **재조합 할당 방지**:
  - Composable 파라미터로 매번 새로운 리스트, formatter, painter, mutable wrapper 같은 객체를 생성하여 전달하지 않도록 주의합니다.
  - 람다 객체는 strong skipping의 자동 memoization을 먼저 신뢰합니다. 수동 `remember`는 측정된 문제나 의도적인 identity 보존 요구가 있을 때만 사용합니다.

---
*참조: 기존 코드는 "최소 수정 원칙"을 따르며, 신규 코드 작성 시 위 규칙들을 강제합니다.*
