---
type: skill
name: android-state-and-side-effects
description: "ViewModel UiState·StateFlow·Intent 갱신·one-off event 설계 스킬."
---

# Android State와 Side Effect 스킬

ViewModel, `UiState`, `StateFlow`, one-off event를 만들거나 수정할 때 사용합니다.
원격 API에서 아이템 목록을 불러와 화면에 보여주는 흐름을 기준으로 설명합니다. API/Repository 실패 매핑이 포함되면 `.agents/llm-skills/android-error-handling.md`를 함께 적용합니다.

이 문서는 **상태 설계 절차**만 다룹니다. 세부 규칙은 `.agents/llm-rules/data/state-management.md`, `.agents/llm-rules/data/layer-contract.md`, `.agents/llm-rules/tooling/kotlin-rules.md`를 우선합니다.

## 1. State owner 선택

| 소유자 | 사용 조건 |
|--------|----------|
| `remember` local state | scroll, expanded처럼 한 composable에 닫힌 UI 상태 |
| ViewModel | 화면 상태가 data/domain(API/Repository)에서 오거나 configuration change 이후 유지되어야 함 |

아이템 목록 상태는 Repository 결과이므로 ViewModel이 소유합니다. Composable에서 Repository를 직접 호출하지 않습니다.

## 2. UiState 계약

- 화면이 도달할 수 있는 상태를 명시합니다: `loading`, `content`(아이템 목록), `error`.
- `MutableStateFlow`는 private으로 두고 `asStateFlow()`로 노출합니다.
- Compose가 관찰하는 list는 `ImmutableList`를 사용합니다.
- DTO, `Context`, `Activity`를 `UiState`에 넣지 않습니다. domain `Item` 모델만 담습니다.
- `data class` + nullable/boolean 조합으로 시작하고, 분기가 늘어나면 `sealed interface`로 정리합니다.

```kotlin
// 방식 A: data class
@Immutable
data class ItemsUiState(
    val isLoading: Boolean = false,
    val items: ImmutableList<Item> = persistentListOf(),
    val errorMessage: String? = null,
)

// 방식 B: sealed interface (상태가 상호 배타적일 때 더 명확)
sealed interface ItemsUiState {
    data object Loading : ItemsUiState
    data class Success(val items: ImmutableList<Item>) : ItemsUiState
    data class Error(val message: String) : ItemsUiState
}
```

## 3. ViewModel

- `@HiltViewModel` + `@Inject constructor`로 Repository를 주입합니다.
- `viewModelScope.launch`로 단발 API 호출을 실행하고 `runCatching`/`Result`로 성공·실패를 분기합니다.
- 재요청은 `load(query)` 한 함수로 받아 상태를 다시 갱신합니다.

```kotlin
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: ItemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemsUiState())
    val uiState: StateFlow<ItemsUiState> = _uiState.asStateFlow()

    fun load(query: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getItems(query) }
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(isLoading = false, items = items.toImmutableList())
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "불러오기 실패")
                    }
                }
        }
    }
}
```

## 4. 새 Intent 재요청 처리

Activity가 `singleTask`/`singleTop`이면 Intent가 여러 번 와도 단일 인스턴스입니다. 새 Intent는 `onNewIntent`로 들어오므로, 거기서 파라미터를 파싱해 동일 ViewModel의 `load()`로 전달하면 상태만 갱신됩니다(인스턴스 재생성 없음). 딥링크로 들어온 경우도 같은 방식으로 처리합니다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)
    setContent { /* ItemsRoute(viewModel) */ }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent) // 같은 viewModel.load(query) 호출
}

private fun handleIntent(intent: Intent) {
    val query = intent.data?.let { parseQuery(it) } ?: return
    viewModel.load(query)
}
```

## 5. Compose 수집

`collectAsStateWithLifecycle()`로 lifecycle-aware하게 수집합니다.

```kotlin
@Composable
fun ItemsRoute(viewModel: ItemsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ItemsScreen(state = uiState)
}
```

## 6. One-off effect (필요 시에만)

화면 상태로 표현 가능한 흐름이면 별도 effect stream이 필요 없습니다. 한 번 소비되는 명령(예: 일회성 toast/snackbar)이 생기면 `UiState`에 섞지 말고 `SharedFlow`/`Channel`로 분리합니다.

```kotlin
private val _events = Channel<String>(Channel.BUFFERED)
val events = _events.receiveAsFlow()
// 수집 측: LaunchedEffect { events.collect { /* show snackbar */ } }
```

## 7. 검증

- ViewModel transition: `load()` 호출 시 loading → success/error 전이를 unit test로 확인.
- 새 Intent: 여러 Intent가 `onNewIntent`로 들어와도 인스턴스 1개를 유지하며 상태만 갱신되는지 확인.
- Flow: 재요청 시 이전 error가 초기화되는지(stale state) 확인.
- 자동 테스트가 없으면 manual scenario와 residual risk를 보고합니다.

참고: `.agents/llm-rules/ui/compose-rules.md`, `.agents/llm-rules/data/state-management.md`,
`.agents/llm-rules/tooling/hilt-di.md`, `.agents/llm-skills/android-error-handling.md`,
`.agents/llm-skills/feature-development.md`, `.agents/llm-skills/compose-ui-development.md`,
`.agents/llm-skills/code-simplification.md`, `.agents/llm-rules/tooling/android-workflow.md`.
