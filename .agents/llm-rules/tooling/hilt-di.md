---
type: rule
description: "단일 모듈(:app) Hilt DI 규칙 — 모듈 위치/스코프 배치와 생성자 주입 원칙."
---

# Hilt DI 규칙

단일 모듈의 Hilt 사용 규칙.

## 모듈 위치 기준

| 위치 | 용도 |
|------|------|
| `data/di/NetworkModule` | 네트워크 그래프 `@Provides`: `Json`, `OkHttpClient`(logging interceptor), `Retrofit`, Retrofit `Api` 인터페이스. 모두 `@Singleton`, `@InstallIn(SingletonComponent::class)` |
| `data/di/RepositoryModule` | Repository 인터페이스 → 구현체 `@Binds`. `@InstallIn(SingletonComponent::class)` |

`Application`에 `@HiltAndroidApp`, `MainActivity`에 `@AndroidEntryPoint`를 단다.

## NetworkModule 예시

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    @Singleton
    @Provides
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Singleton
    @Provides
    fun provideItemApi(retrofit: Retrofit): ItemApi = retrofit.create(ItemApi::class.java)
}
```

## RepositoryModule 예시

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository
}
```

## ViewModel 주입

```kotlin
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
) : ViewModel()
```

`@HiltViewModel`이 ViewModel 생명주기를 자동 처리하므로 별도 스코프 어노테이션을 붙이지 않는다.

## Compose에서 `hiltViewModel` import

Compose에서 `@HiltViewModel`을 주입할 때는 **`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`**을 import한다. androidx.hilt 1.3.0부터 `hiltViewModel()`이 Navigation 비의존 artifact(`androidx.hilt:hilt-lifecycle-viewmodel-compose`)로 이동했고, 기존 `androidx.hilt.navigation.compose.hiltViewModel`은 `@Deprecated`("Moved to package: androidx.hilt.lifecycle.viewmodel.compose")다.

```kotlin
// ❌ deprecated (navigation 의존)
import androidx.hilt.navigation.compose.hiltViewModel

// ✅ 올바름
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
```

의존성도 `androidx.hilt:hilt-lifecycle-viewmodel-compose`를 사용한다. Navigation Compose를 따로 쓰지 않으면 `hilt-navigation-compose`는 필요 없다.

## 스코프 규칙

| 스코프 | 생명주기 | 사용 시점 |
|--------|----------|-----------|
| `@Singleton` | 앱 전체 | 네트워크 클라이언트(`Json`/`OkHttpClient`/`Retrofit`/`Api`), Repository |
| `@HiltViewModel` | ViewModel | 화면 단위 ViewModel (스코프 어노테이션 불필요) |

화면이 적은 앱이라면 `@ActivityScoped`/`@ActivityRetainedScoped`/`@ViewModelScoped`는
필요할 때만 도입한다. 기본은 `@Singleton`과 `@HiltViewModel` 두 가지로 충분하다.

## 금지 패턴

- Repository를 `@ActivityScoped`로 바인딩 — Repository는 항상 `@Singleton`.
- Module 내부에서 `Dispatchers.IO`를 하드코딩하지 않는다 — Repository에서 `withContext(Dispatchers.IO)`로 명시적 처리.
- ViewModel에서 `@Inject lateinit var` 필드 주입 금지 — 생성자 주입만 허용.
