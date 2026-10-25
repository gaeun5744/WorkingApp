---
type: rule
description: "단일 모듈(:app) 레이어 계약 — DTO→Mapper→Domain→Repository→Presentation 흐름과 DTO 유출 금지 규칙."
---

# Layer Contract

단일 모듈(`:app`, 패키지 `com.example.app`) 내 패키지 레이어 계약. 신규 코드 작성 시 반드시 준수.

## 레이어 흐름

```
원격 API 응답
  ↓
data/remote/dto/        — @Serializable DTO (네트워크 JSON 형태 그대로)
  ↓
data/remote/dto (mapper) — DTO → Domain 변환 확장 함수 (toDomain)
  ↓
domain/model/           — 순수 Kotlin 도메인 모델 (Android/Compose 의존 없음)
  ↓
domain/repository/      — Repository 인터페이스 (domain이 소유)
  ↓
data/repository/        — Repository 구현체 (data 레이어, Api 호출)
  ↓
presentation/           — ViewModel이 Repository 직접 호출 (UseCase 레이어 없음)
```

레이어는 모두 같은 모듈 안의 패키지다. 의존 방향은 단방향으로 유지한다:
`presentation → domain ← data`. domain은 어떤 레이어도 import하지 않는다.

## 절대 금지 규칙

### ❌ DTO를 presentation에 직접 넘기기

```kotlin
// 금지 — ViewModel이 data.remote.dto를 import
import com.example.app.data.remote.dto.ItemResponse

class ItemsViewModel : ViewModel() {
    val items = MutableStateFlow<List<ItemResponse>>(emptyList()) // ❌
}
```

```kotlin
// 올바름 — domain.model 사용 + ImmutableList
import com.example.app.domain.model.Item
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

class ItemsViewModel : ViewModel() {
    val items = MutableStateFlow<ImmutableList<Item>>(persistentListOf()) // ✅
}
```

### ❌ Compose 의존성을 data/domain 레이어에 두기

```kotlin
// 금지 — data 레이어에서 Compose import
import androidx.compose.runtime.Immutable  // ❌ data/remote/dto 의 mapper에 있으면 안 됨
```

`@Immutable`, `@Stable` 같은 Compose 어노테이션은 `presentation/` 레이어에만 둔다.
`domain/model/`은 순수 Kotlin data class로만 구성한다.

### DTO ↔ Domain 매핑 예시

```kotlin
// data/remote/dto/ItemResponse.kt
@Serializable
data class ItemResponse(
    val id: Long,
    val title: String,
)

// data/remote/dto/ItemMapper.kt
fun ItemResponse.toDomain(): Item = Item(
    id = id,
    title = title,
)
```

```kotlin
// domain/model/Item.kt — 순수 Kotlin
data class Item(
    val id: Long,
    val title: String,
)
```

### Repository 다중 주입

ViewModel에 여러 Repository를 주입하는 것은 허용. 연관 없는 도메인이 과도하게 섞인다고 판단될 때만 기능 분리를 검토할 것.

## 패키지별 소유 규칙

| 패키지 | 가질 수 있는 것 | 가질 수 없는 것 |
|--------|---------------|----------------|
| `data/remote/dto/` | `@Serializable` DTO, enum, `toDomain()` mapper | 도메인 모델 정의, UI 모델, Compose 어노테이션 |
| `data/remote/api/` | Retrofit `interface` (suspend 함수) | DTO→Domain 변환 로직 |
| `domain/model/` | 순수 Kotlin data class, sealed class | DTO, Android/Compose API |
| `domain/repository/` | 인터페이스만 | 구현체 |
| `data/repository/` | Repository 구현체 (Api 호출 + mapper 경유) | 도메인 모델 직접 생성(반드시 mapper 경유) |
| `presentation/` | ViewModel, Composable, UiState | DTO import |
