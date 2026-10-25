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
| `data/remote/api` | Retrofit `PedometerApi` (`docs/api.md`의 코드 표현), 토큰 갱신용 동기 `AuthApi` | M3 ✅ |
| `data/remote/auth` | `AuthInterceptor`(토큰 첨부 + 401 갱신), `AccessTokenRenewer`(리프레시 → 자동 재가입) | M3 ✅ |
| `data/remote/ApiCall.kt` | Retrofit 예외 → `AppError` 정규화 | M3 ✅ |
| `data/remote/dto` | `@Serializable` DTO (+ M3부터 `toDomain()` 매퍼) | M1 ✅ |
| `data/remote/mock` | Mock 서버 (Phase A 전용) | M1 ✅ |
| `data/di` | Hilt 모듈: `NetworkModule`, `TimeModule`, `MockApiModule`, `DatabaseModule`, `SensorModule`, `RepositoryModule` | M2 ✅ |
| `data/local` | `WorkingAppDatabase`, `dao/StepDao`, `entity/*` (+ 매퍼), `SessionStore`(토큰·닉네임·동네) | M3 ✅ |
| `data/sensor` | `StepDataSource` + `AndroidStepDataSource`(STEP_COUNTER) / `SimulatedStepDataSource`(에뮬레이터) | M2 ✅ |
| `data/repository` | `StepRepositoryImpl`(측정·동기화), `AccountRepositoryImpl`(가입·동네) | M3 ✅ |
| `domain/model`, `domain/repository`, `domain/step` | `StepReading`·`DailySteps`·`Region`·`AppError`, `StepRepository`·`AccountRepository`, `StepDeltaCalculator` | M3 ✅ |
| `presentation/onboarding`, `presentation/region` | 닉네임 → 가입 → 동네 선택(결과는 `ResultEventBus`) | M3 ✅ |
| `presentation/nav` | `AppNavViewModel`(온보딩 여부로 시작 화면 결정), `RegionResult` | M3 ✅ |
| `presentation/text/AppErrorText.kt` | `AppError` → `UiText` 매핑 (한 곳에서만) | M3 ✅ |
| `presentation/home` | 1차 홈 (오늘 걸음, 권한·센서 미지원 상태) — M4에서 확장 | M2 ✅ |
| `presentation/nav` | `NavKeys.kt`, `AppNavHost.kt` (Navigation 3) | M1 ✅ |
| `presentation/text` | `UiText` + `asString()` (i18n-safe 메시지) | M1 ✅ |
| `presentation/placeholder` | 화면 골격용 임시 화면 — 실제 화면이 생기면 삭제 | M1 (임시) |
| `service` | `StepCounterService`(FGS type=health), `BootCompletedReceiver`, `StepNotificationFactory` | M2 ✅ |
| `worker` | `StepSnapshotWorker` (15분 주기), `StepSyncWorker` (15분 주기 + 앱 시작·온보딩 완료 시, 네트워크 조건, 지수 백오프) | M3 ✅ |

### 지침에 없는 패키지를 추가한 이유

`.agents/AGENTS.md`의 기본 구조는 "원격 API → 화면" 앱을 가정한다. 만보기는 기기에서 데이터를 만들어 내므로 다음을 추가한다.

- `data/local`: 오프라인 우선 — 걸음 원천 데이터를 기기에 보관 (PRD Q5)
- `data/sensor`: 센서를 인터페이스 뒤에 두어 테스트용 Fake로 교체 (PRD Q8)
- `domain/step`: 재부팅·자정 처리 같은 핵심 규칙을 Android 의존 없이 단위 테스트
- `service`, `worker`: 앱이 꺼져 있어도 측정·동기화
- `data/remote/mock`: 서버 없이 앱을 먼저 완성 (PRD Q17·Q18)

## 3. 걸음 측정 (M2)

```
STEP_COUNTER ─(배칭 없음)→ AndroidStepDataSource ─┐
                                                   ├→ StepRepository.record() ─ withTransaction {
StepCounterService (FGS, 상시 구독) ───────────────┤      체크포인트 읽기 → StepDeltaCalculator → 일별·시간대별 += 증분, 체크포인트 갱신 }
StepSnapshotWorker (15분, 1회 읽기) ───────────────┘
BootCompletedReceiver → 재부팅·앱 업데이트 후 StepCounterService 재시작
```

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 누적값 → 걸음 변환 | **측정마다 증분(현재 − 직전)을 측정 시각의 (날짜, 시간대)에 더함** | 0시 baseline 차이: 자정마다 baseline을 다시 잡아야 하고, 시간대별 집계가 따로 필요하며, 재부팅 시 baseline 보정이 복잡하다. 증분 방식은 자정 처리와 hourly 집계가 저절로 해결된다. |
| 재부팅 판정 | **`Settings.Global.BOOT_COUNT` 비교 우선, 읽을 수 없으면 값 감소로 판정** | 값 감소만 보기: 재부팅 후 첫 측정 전에 직전 값보다 많이 걸으면 재부팅을 놓쳐 걸음이 덜 세진다. |
| 늦게 도착한 측정 | **같은 부팅인데 값이 줄면 무시** (기준도 유지) | 재부팅으로 처리: 서비스·워커의 측정 순서가 뒤바뀌면 누적값 전체가 다시 더해져 수천 보가 중복된다. |
| 체크포인트 저장 위치 | **Room, 증분과 같은 트랜잭션** | DataStore(TODO 초안): 걸음 저장과 체크포인트 저장 사이에 앱이 죽으면 다음 측정에서 같은 증분이 또 더해진다. |
| 동시 기록 보호 | **`withTransaction` 안에서 읽기→계산→쓰기** | Repository 안 `Mutex`: 같은 프로세스만 보호하고 DB 일관성은 보장하지 않는다. |
| 센서 배칭 | **걸지 않음 (`maxReportLatency` 미지정)** — 실기기 디버깅으로 변경 | 30초 배칭(초기 구현): 삼성 HAL 이 걸음 변화를 모아 띄엄띄엄 보고해 화면이 실시간으로 갱신되지 않았다. 누적 non-wakeup 센서라 배칭으로 아끼는 배터리도 거의 없다. 화면이 보일 때만 0으로 재등록: 등록 전환 로직이 늘고 알림은 여전히 느리다. 0 + DB 저장 묶기: 지금 규모에는 과하다. |
| 측정 시각 | **`SensorEvent.timestamp`(부팅 후 ns)로 전달 지연을 보정** | 콜백 받은 시각: 센서 등록 시 즉시 재전달되는 직전 값(실측 239초 전 값)이 지금 시각으로 잘못 기록된다. |
| 시간 변경 브로드캐스트 | **처리하지 않음** — 모든 날짜 계산이 주입된 KST `Clock` 기준 | `ACTION_TIME_CHANGED`/`TIMEZONE_CHANGED` 수신: 기기 시간대와 무관하게 KST로 계산하므로 필요 없다. 기기 시계 조작 자체는 서버가 판정한다 (PRD Q6). |
| 에뮬레이터 센서 | **`SimulatedStepDataSource`** (부팅 후 경과 시간 = 누적값, `-PfakeStepSensor=true`) | 항상 Fake 사용: 센서 미지원 UI를 확인할 수 없다. 프로세스 메모리 카운터: 재부팅·강제 종료 시나리오를 실제 센서처럼 재현하지 못한다. |
| 센서 스냅샷 테이블 | **두지 않음** — 체크포인트 1행으로 충분 | TODO 초안의 `sensor_snapshot`: 증분 방식에서는 재계산에 쓰이지 않는다. |

### 알려진 한계
- 두 측정 사이에 자정이 끼면 증분 전체가 뒤 측정 날짜로 들어간다. 걸음마다 오는 이벤트와 15분 워커로 간격을 짧게 유지해 오차를 줄인다.
- 재부팅 직전 마지막 측정 이후의 몇 걸음은 잃을 수 있다 (실제 센서도 종료 직전 값의 전달을 보장하지 않음).
- 서비스·워커가 모두 멈춘 채 오래 지나면 그동안의 걸음이 다음 측정 시각 하나에 몰린다. 서버의 시간당 상한(PRD Q9)이 비정상치로 표시한다.

- 화면이 켜진 채 걸으면 걸음마다(약 0.5초) DB 트랜잭션이 일어난다. 배터리 측정(공통 검증 항목)에서 문제가 되면 저장을 묶는다.

### 디버깅 기록: "처음 걸을 때 걸음이 실시간으로 안 올라간다" (실기기 SM-S916N)

| 단계 | 내용 |
|---|---|
| 증거 수집 | `dumpsys sensorservice`로 등록 파라미터(`batchingPeriod=30000000us`) 확인, `run-as`로 앱 DB 확인, 이벤트별 원시값·전달 지연을 남기는 진단 로그 추가 |
| 가설 1 (첫 이벤트 손실) | **기각** — 센서 등록 즉시 직전 값이 재전달되어 기준이 바로 잡힌다 |
| 가설 2 (앱 전달 지연) | **기각** — 앱 전달 지연 2~10ms, 앱은 HAL 이벤트를 모두 즉시 받았다 |
| 가설 3 (배칭 요청으로 HAL 보고가 드문드문) | **확인** — 같은 조건으로 걸었을 때 배칭 30초: 이벤트 3번(첫 갱신은 등록 약 30초 후) / 배칭 0: 걸음마다 17번(약 0.5초 간격) |
| 수정 | 배칭 제거, 진단 로그 제거 |
| 참고 | 걷기 시작 직후 약 6걸음이 한 번에 오르는 것은 삼성 센서의 보행 감지 동작이며 걸음 손실은 없다 |

### 검증 (에뮬레이터 Pixel 8 API 37, 시뮬레이션 센서)
- 권한 허용 → FGS(type=health) 실행, 상시 알림 "오늘 N걸음" 갱신
- 강제 종료 20초 후 재실행: 종료 동안의 걸음까지 반영 (92 → 117)
- 재부팅(BOOT_COUNT 2→3): 앱을 열지 않아도 서비스 자동 재시작, 133 → 161로 중복·누락 없이 이어짐
- `StepSnapshotWorker` 예정 실행: `Worker result SUCCESS` (HiltWorkerFactory 연결 확인)
- 단위 테스트 `StepDeltaCalculatorTest` 8개, 계측 테스트 `StepRepositoryImplTest` 4개 (동시 기록 20회 → 1회만 반영 포함)

## 4. 인증 & 동기화 (M3)

```
PedometerApi ─ OkHttpClient: [AuthInterceptor] → (로깅) → MockApiInterceptor
                                │ 401
                                ▼
                         AccessTokenRenewer (동기, lock)
                           1) 다른 요청이 이미 갱신했으면 그 토큰
                           2) AuthApi.refresh  ─ 거부(401) ─▶ 3) 저장된 닉네임·동네로 재가입 + 걸음 전부 미동기화
                              └ 네트워크 실패 → 포기 (재가입하지 않음)
AuthApi ─ @AuthClient OkHttpClient: (로깅) → MockApiInterceptor   ← AuthInterceptor 없음: 갱신이 갱신을 부르지 않게
```

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 401 처리 위치 | **`AuthInterceptor`(애플리케이션 인터셉터)에서 갱신 후 한 번 재시도** | OkHttp `Authenticator`: 애플리케이션 인터셉터 뒤(`RetryAndFollowUpInterceptor`)에서 동작해 Mock이 바로 돌려준 401을 보지 못한다 (실측: 호출 0회, 계측 테스트 실패로 발견). 인터셉터 방식은 Mock·실제 서버 모두 같게 동작한다. |
| 토큰 저장소 | **SharedPreferences** | DataStore(TODO 초안): 인터셉터는 동기 함수라 토큰을 동기로 읽어야 하는데 DataStore는 `runBlocking` 없이 불가(지침 금지). EncryptedSharedPreferences: 지원 중단 — 토큰 암호화는 이번 범위 밖. |
| 갱신 전용 클라이언트 | **`@AuthClient` 별도 OkHttpClient + 동기 `AuthApi`** | 같은 클라이언트 사용: 갱신 요청이 다시 AuthInterceptor를 거치며 재귀가 생길 수 있다. |
| 동시 401 | **`synchronized` + "실패한 토큰과 현재 토큰이 다르면 이미 갱신됨"** | 요청마다 갱신: 동시에 401을 받은 요청들이 각각 리프레시해 rotation으로 서로의 토큰을 무효화한다. |
| 리프레시 실패 구분 | **401(거부)일 때만 재가입, 네트워크 실패는 포기** | 모든 실패에 재가입: 일시적 네트워크 문제로 멀쩡한 계정을 버린다. |
| 재가입 후 걸음 | **로컬 걸음 전부 미동기화로 되돌림** (PRD Q19) | 그대로 둠: 새 계정의 서버 걸음이 0이 되어 리워드·랭킹이 틀어진다. |
| 동기화 완료 표시 | **업로드 직전에 읽은 `updated_at`이 그대로일 때만 `synced=1`** | 무조건 표시: 업로드하는 동안 쌓인 걸음이 영영 올라가지 않는다. 같은 ms 안의 변경은 놓칠 수 있지만, 다음 걸음이 기록되면 다시 미동기화가 되어 자동 복구된다. |
| 오류 전달 | **Repository에서 `AppError`(Network/Server/Unauthorized/InvalidRequest/Unknown)로 정규화, `toUiText()` 한 곳에서 문구 매핑** | ViewModel이 `HttpException` 직접 처리: 지침 위반, 화면마다 매핑 중복. |
| 온보딩 상태 판단 | **`AppNavViewModel`이 SharedPreferences를 동기로 읽어 시작 화면 결정** | 스플래시 + 비동기 로딩: 동기 저장소라 필요 없다. |

### 검증 (에뮬레이터, 시뮬레이션 센서)
- 새 설치 → 닉네임 입력 → 가입 → 동네 목록(10개) → 성수동 선택(`ResultEventBus`) → 홈, 뒤로가기 시 온보딩으로 돌아가지 않음
- 앱 강제 종료 후 재실행(Mock 상태 소실): `sync 401 → refresh 401 → 재가입 201 → 동네 복원 200 → sync 재시도 200`, 화면 흐름 끊김 없음
- 계측 테스트: `AuthRenewalTest` 2개(토큰 만료 재발급, 재가입·동네 복원·미동기화), `StepRepositoryImplTest` +2개(업로드 후 synced, 업로드 중 변경 시 미표시)

## 5. Mock 서버 (Phase A)

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

## 6. 빌드 메모

- AGP 9 내장 Kotlin + KSP 2.0.x 조합 때문에 `gradle.properties`에 `android.disallowKotlinSourceSets=false`가 필요하다 (실험적 옵션 경고가 뜨는 것은 정상). KSP가 `android.sourceSets` 방식을 지원하면 제거한다.
- Hilt 2.60 생성 코드가 `@CanIgnoreReturnValue`를 참조해 `error_prone_annotations`를 `compileOnly`로 추가했다.
- Navigation 3의 `ResultEventBus`는 1.2.0-alpha 라인에만 있어 alpha 버전을 쓴다.
- Room 스키마는 `app/schemas/`에 내보낸다 (마이그레이션 테스트용, 커밋 대상).
- KDoc·블록 주석 안에 `/*` 를 쓰지 않는다 (예: `/auth/*`). Kotlin 블록 주석은 중첩되어 주석이 닫히지 않고 뒤의 클래스 전체가 사라진다 — KSP가 "could not be resolved"로만 알려 준다.
- `connectedDebugAndroidTest`는 연결된 **모든 기기**에서 실행된다. 에뮬레이터만 쓰려면 `ANDROID_SERIAL=emulator-5554`를 지정한다.
