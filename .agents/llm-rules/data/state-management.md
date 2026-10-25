---
type: rule
description: "ViewModel StateFlow/UiState 노출 규칙 (asStateFlow, ImmutableList 등)."
---

# State Management

ViewModel StateFlow 및 UiState 패턴.

## StateFlow 노출 규칙

### 항상 asStateFlow()로 노출
```kotlin
// ❌ 금지
val myInfo: StateFlow<MyData?> = _myInfo

// ✅ 올바름
val myInfo: StateFlow<MyData?> = _myInfo.asStateFlow()
```

### ImmutableList 권장
신규 코드에서 컬렉션 타입 State는 `ImmutableList` 사용을 권장. 기존 코드에 `List<T>`가 남아 있어도 수정 대상 아님.

```kotlin
// ❌ 금지
val items = MutableStateFlow<List<Feed>>(emptyList())

// ✅ 올바름
val items = MutableStateFlow<ImmutableList<Feed>>(persistentListOf())
```

업데이트 시:
```kotlin
_items.value = newList.toImmutableList()
```

## UiState 패턴

복잡한 화면은 sealed interface로 UiState 통합:

```kotlin
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: UiText) : FeedUiState
    data class Success(
        val feeds: ImmutableList<Feed>,
        val isRefreshing: Boolean = false,
    ) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
}
```

단순 화면(상태 2~3개)은 개별 StateFlow도 허용하되 일관성 유지.

사용자에게 노출되는 오류 메시지는 raw `String` 대신 `.agents/llm-skills/android-state-and-side-effects.md`의 i18n-safe message 계약을 따릅니다. 예시의 `UiText`는 해당 계약의 canonical wrapper를 뜻합니다. 화면별 별도 message wrapper를 새로 만들지 말고 repo-local canonical 타입을 사용합니다. `R.string` resource id를 message key로 전달하는 것은 허용하지만, ViewModel에서 `Context`, `Resources`, `getString()`, `stringResource`로 문자열을 resolve하지 않습니다.

## Coroutine / Flow 수집 패턴

### 선택 기준

- **단발성 suspend/API 호출**: `viewModelScope.launch { ... }`
- **ViewModel이 소유하는 지속 구독 Flow**: `onEach { ... }.launchIn(viewModelScope)`
- **UI 상태로 노출할 Flow 변환**: `map/catch/stateIn(viewModelScope, SharingStarted.WhileSubscribed(...), initial)`
- **여러 소스 Flow를 하나의 UiState로 합치기**: `combine(...)`을 1순위로 사용합니다(예: 목록 + 새로고침 여부 + 필터). 소스가 늘어도 `combine`이 모든 최신 값을 동기화해 한 UiState로 만듭니다.
- **검색어/필터/계정 전환처럼 최신 요청만 유효한 흐름**: `flatMapLatest`, 기존 Job cancel, stale result 방지 중 하나를 명시
- **테스트에서 시간/스레드 제어가 필요한 로직**: Dispatcher, clock, scheduler 주입 우선
- **일회성 navigation/snackbar/permission/file picker 이벤트**: `UiState`에 섞지 않고 별도 effect stream 또는 Route callback으로 분리

### viewModelScope.launch vs launchIn vs stateIn

```kotlin
// 단발성 API 호출
fun loadData() {
    viewModelScope.launch {
        repository.getData().onSuccess { _uiState.value = UiState.Success(it) }
    }
}

// 지속적인 스트림 구독
init {
    eventManager.events
        .onEach { handleEvent(it) }
        .launchIn(viewModelScope)
}

// UI 상태로 노출할 Flow 변환
val uiState: StateFlow<FeedUiState> =
    repository.feeds
        .map { feeds -> FeedUiState.Success(feeds.toImmutableList()) }
        .catch { emit(FeedUiState.Error(message = UiText.Resource(R.string.feed_load_failed))) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState.Loading,
        )
```

`Flow`라고 무조건 `launchIn`을 쓰지 않습니다. 값을 UI 상태로 공개해야 하면 `stateIn`,
장기 side-effect 구독이면 `launchIn`, 단발 작업이면 `viewModelScope.launch`를 사용합니다.
여러 소스를 합쳐 노출해야 하면 `combine`으로 모은 뒤 `stateIn`으로 공개합니다.

### Compose에서 수집
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```
`collectAsState()` 대신 `collectAsStateWithLifecycle()` 사용 (lifecycle 인식).

## 금지 패턴

- `MutableStateFlow`를 `public`으로 노출 (`_` prefix + private 필수)
- `runBlocking` 사용
- `GlobalScope.launch` 사용
- `!!` 강제 언랩
