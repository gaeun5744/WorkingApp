# 아키텍처

> 요구사항과 결정 근거는 `docs/PRD.md`, API 계약은 `docs/api.md`를 따른다.
> 이 문서는 "코드가 실제로 어떻게 구성되는가"를 기록하고, 구현이 진행될 때마다 갱신한다.

## 1. 전체 구성

```
[Android 앱]                                                [Phase A]              [Phase B]
 STEP_COUNTER → StepCounterService → Room ─┐
                                            ├→ Repository → Retrofit → OkHttp ─┬→ MockApiInterceptor
 Compose UI ← ViewModel ←───────────────────┘                                   └→ 실제 서버 (Spring Boot)
```

- 걸음 측정의 원천은 기기(Room), 리워드·랭킹의 원천은 서버 (PRD Q5)
- 날짜는 Asia/Seoul 고정. 앱·Mock 모두 주입받은 `java.time.Clock`으로 "오늘"을 계산한다 (PRD Q6)

## 2. 앱 패키지 구조

의존 방향: `presentation → domain ← data`. UseCase 레이어 없이 ViewModel이 Repository를 직접 호출한다 (`.agents/llm-rules/data/layer-contract.md`).

| 패키지 | 역할 | 상태 |
|---|---|---|
| `data/remote/api` | Retrofit `PedometerApi` (`docs/api.md`의 코드 표현) | M1 ✅ |
| `data/remote/dto` | `@Serializable` DTO (+ M3부터 `toDomain()` 매퍼) | M1 ✅ |
| `data/remote/mock` | Mock 서버 (Phase A 전용) | M1 ✅ |
| `data/di` | Hilt 모듈: `NetworkModule`, `TimeModule`, `MockApiModule` (+ `DatabaseModule`, `RepositoryModule`) | M1 일부 ✅ |
| `data/local/*`, `data/sensor`, `data/repository` | Room, 센서, Repository 구현체 | M2~ |
| `domain/model`, `domain/repository`, `domain/step` | 도메인 모델, Repository 인터페이스, 걸음 계산 | M2~ |
| `presentation/nav` | `NavKeys.kt`, `AppNavHost.kt` (Navigation 3) | M1 ✅ |
| `presentation/text` | `UiText` + `asString()` (i18n-safe 메시지) | M1 ✅ |
| `presentation/placeholder` | 화면 골격용 임시 화면 — 실제 화면이 생기면 삭제 | M1 (임시) |
| `service`, `worker` | Foreground Service, WorkManager | M2~ |

### 지침에 없는 패키지를 추가한 이유

`.agents/AGENTS.md`의 기본 구조는 "원격 API → 화면" 앱을 가정한다. 만보기는 기기에서 데이터를 만들어 내므로 다음을 추가한다.

- `data/local`: 오프라인 우선 — 걸음 원천 데이터를 기기에 보관 (PRD Q5)
- `data/sensor`: 센서를 인터페이스 뒤에 두어 테스트용 Fake로 교체 (PRD Q8)
- `domain/step`: 재부팅·자정 처리 같은 핵심 규칙을 Android 의존 없이 단위 테스트
- `service`, `worker`: 앱이 꺼져 있어도 측정·동기화
- `data/remote/mock`: 서버 없이 앱을 먼저 완성 (PRD Q17·Q18)

## 3. Mock 서버 (Phase A)

```
PedometerApi(Retrofit) → OkHttpClient
                           ├ HttpLoggingInterceptor (debug, BASIC, Authorization 가림)
                           └ MockApiInterceptor ── 경로/메서드 라우팅, 지연·오류 주입
                                  └ MockServer ── 메모리 상태 + 서버 규칙
                                       └ MockNeighborSteps ── 랭킹용 가짜 이웃
```

| 파일 | 책임 |
|---|---|
| `MockApiInterceptor` | HTTP 요청을 `MockServer` 호출로 바꾸고 결과를 JSON 응답으로 돌려준다. 인증 헤더 확인, 본문 파싱 실패 시 400 |
| `MockServer` | `docs/api.md` 규칙 구현: 토큰 발급·rotation·만료, max upsert, 상한 자르기, 리워드 멱등, 랭킹 계산. 모든 상태 접근은 단일 lock 안에서 처리 |
| `MockNeighborSteps` | (이웃, 날짜)마다 결정적인 걸음 수를 만들고, 오늘은 시각에 따라 증가 |
| `MockApiConfig` | 지연 범위, 오류 확률, 토큰 수명. `MockApiModule`에서 제공 (액세스 토큰 10분 — 재발급 흐름 재현용) |
| `MockResult` | 성공/실패 결과 타입 (예외 대신 값으로 실패를 표현) |

- **켜고 끄기**: `BuildConfig.USE_MOCK_API` (Phase A는 모든 빌드 `true`). `NetworkModule`은 플래그가 켜져 있을 때만 `MockApiInterceptor`를 붙이고, 이 Interceptor는 `dagger.Lazy`로 받아 플래그가 꺼지면 생성조차 하지 않는다.
- **알려진 한계**: 상태가 메모리에만 있어 앱 프로세스가 재시작되면 사용자·지갑이 사라진다. 이때 저장된 토큰은 `401 UNAUTHORIZED` → 재발급 실패 → 재가입 흐름을 타게 되는데, 이는 M3에서 다룰 인증 실패 처리의 테스트 케이스로 활용한다.
- **검증**: `MockApiTest` — Retrofit → OkHttp → Interceptor 전체 경로로 계약 규칙 13가지를 확인한다.

### 실제 서버로 전환 (M10)

1. `app/build.gradle.kts`에서 `USE_MOCK_API = false`, `BASE_URL`을 빌드 타입별 서버 주소로 변경
2. `AndroidManifest.xml`에 `INTERNET` 권한 추가 (Mock은 실제 네트워크를 쓰지 않아 지금은 없음)
3. `docs/api.md` 기준으로 Mock과 서버 응답 차이 점검

## 4. 빌드 메모

- AGP 9 내장 Kotlin + KSP 2.0.x 조합 때문에 `gradle.properties`에 `android.disallowKotlinSourceSets=false`가 필요하다 (실험적 옵션 경고가 뜨는 것은 정상). KSP가 `android.sourceSets` 방식을 지원하면 제거한다.
- Hilt 2.60 생성 코드가 `@CanIgnoreReturnValue`를 참조해 `error_prone_annotations`를 `compileOnly`로 추가했다.
- Navigation 3의 `ResultEventBus`는 1.2.0-alpha 라인에만 있어 alpha 버전을 쓴다.
