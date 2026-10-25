---
type: skill
name: android-error-handling
description: "API/Repository/UI 3계층 오류 처리·재시도·사용자 메시지 설계 스킬."
---

# Android 오류 처리 스킬

원격 API 호출 실패, Repository 오류 매핑, ViewModel 실패 상태, 재시도, 사용자 메시지를 설계하거나 리뷰할 때 사용합니다.

핵심 원칙: 오류는 한 계층에서 다 처리하지 않습니다. **API/Repository에서 예외를 `Result`로 정규화하고, ViewModel은 UiState로 변환하며, Composable/Activity는 상태만 그립니다.**

```text
Retrofit suspend 호출 (HttpException / IOException)
-> Repository: runCatching / try-catch -> Result<T> (또는 sealed 타입)
-> ViewModel: UiState.Error / errorMessage
-> Composable: 상태만 렌더링 + 재시도 버튼
```

## 1. Repository 계층: 예외를 Result로 변환

Retrofit suspend 함수는 실패 시 예외를 던집니다. Repository는 이 예외를 잡아 `Result<T>`로 정규화하고, raw `HttpException`/`IOException`/`Response`를 ViewModel로 노출하지 않습니다.

```kotlin
override suspend fun getItems(query: String): Result<List<Item>> =
    runCatching { api.getItems(query) }              // List<ItemResponse>
        .map { responses -> responses.map(ItemResponse::toDomain) } // DTO -> domain model
```

대표적인 실패 케이스:

| 상황 | 발생 형태 | 사용자 메시지 방향 |
|------|----------|------------------|
| 리소스 없음 | `HttpException` (404) | "데이터를 찾을 수 없어요" |
| 네트워크 끊김/타임아웃 | `IOException` | "네트워크 연결을 확인해주세요" |
| 요청 제한 초과 | `HttpException` (429/403) | "요청이 많아요. 잠시 후 다시 시도해주세요" |
| 잘못된 입력 파라미터 | 검증 실패 | "잘못된 요청이에요" |

원하면 `Result<T>` 대신 sealed 타입으로 케이스를 구분해도 됩니다.

```kotlin
sealed interface ItemsResult {
    data class Success(val items: List<Item>) : ItemsResult
    data object NotFound : ItemsResult         // 404
    data object RateLimited : ItemsResult       // 429/403
    data object NetworkError : ItemsResult       // IOException
}
```

HttpException의 `code()` 분기는 Repository 안에서만 처리하고, 상위로는 위 타입만 전달합니다.

입력 파라미터는 호출 전에 검증합니다. 필수 값(예: `query`)이 비어 있으면 API를 호출하지 않고 곧바로 실패로 처리합니다.

## 2. ViewModel 계층: Result -> UiState

ViewModel은 Repository의 Result를 받아 UiState로만 변환합니다. UiState는 `kotlinx.collections.immutable` 컬렉션을 사용합니다.

```kotlin
data class ItemsUiState(
    val isLoading: Boolean = false,
    val items: ImmutableList<Item> = persistentListOf(),
    val errorMessage: String? = null,
)

fun load(query: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        repository.getItems(query)
            .onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items.toImmutableList()) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) }
            }
    }
}
```

- 화면 전체가 비어 있는 실패는 `errorMessage`(또는 `UiState.Error`)로 전체 화면에 표시합니다.
- 예외 -> 메시지 매핑은 ViewModel(또는 mapper) 한 곳에서만 합니다. 화면마다 복붙하지 않습니다.
- 서버 raw 메시지를 그대로 노출하지 말고, 위 표의 사용자 친화적 문구로 바꿉니다.

## 3. Composable/Activity 계층: 상태만 렌더링

Composable과 Activity는 예외를 직접 잡지 않습니다. UiState만 보고 그립니다.

```kotlin
when {
    state.isLoading -> LoadingIndicator()
    state.errorMessage != null -> ErrorView(
        message = state.errorMessage,
        onRetry = { viewModel.load(query) },   // 재시도 = 다시 불러오기
    )
    else -> ItemList(state.items)
}
```

금지:
- Composable/Activity에서 `try/catch`로 Retrofit 예외 처리.
- ViewModel에서 `Context.getString()`, `Toast.makeText()`, dialog 직접 생성(메시지는 String 또는 message key로 전달).
- 새 Intent(예: `onNewIntent`로 들어온 딥링크) 처리 시 예외를 삼키기. 새 파라미터로 `load`를 다시 호출하면 위 흐름이 그대로 재사용됩니다.

## 4. 재시도

기본 재시도는 **버튼으로 동일한 load를 다시 호출**하는 한 가지 패턴이면 충분합니다(위 `onRetry`). 자동 backoff/루프는 꼭 필요할 때만 도입합니다.

- 재시도 시 `errorMessage`를 비우고 `isLoading`을 다시 켭니다.
- GET 목록 조회는 idempotent하므로 중복 side effect 걱정이 없습니다.

## 5. 하지 말아야 할 것

- 빈 `catch` 블록으로 오류를 삼키기.
- 실패를 성공으로 바꿔 빈 목록을 정상처럼 보여주기(에러는 에러로 표시).
- ViewModel/UI에 raw `HttpException`/`IOException`/`Response` 노출.
- 예외 -> 메시지 매핑을 여러 화면에 중복.
- `Log.d/e`에 token이나 전체 응답 body 남기기.
