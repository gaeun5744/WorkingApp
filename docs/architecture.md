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
| `data/local` | `WorkingAppDatabase`(v2), `dao/StepDao`, `entity/*` (+ 매퍼), `SessionStore`(토큰·닉네임·동네), `GoalStore`(목표, DataStore) | M4 ✅ |
| `data/sensor` | `StepDataSource` + `AndroidStepDataSource`(STEP_COUNTER) / `SimulatedStepDataSource`(에뮬레이터) | M2 ✅ |
| `data/repository` | `StepRepositoryImpl`(측정·동기화), `AccountRepositoryImpl`(가입·동네), `GoalRepositoryImpl`, `RewardRepositoryImpl`(리워드·지갑) | M5 ✅ |
| `domain/model`, `domain/repository`, `domain/step` | `StepReading`·`DailySteps`·`HourlySteps`·`Region`·`AppError`, `StepRepository`·`AccountRepository`·`GoalRepository`, `StepDeltaCalculator`·`StepMetrics` | M4 ✅ |
| `domain/history` | `HistoryPeriod`·`HistoryRange`·`HistorySummary` (기간 계산·통계, 순수 Kotlin) | M4 ✅ |
| `presentation/onboarding`, `presentation/region` | 닉네임 → 가입 → 동네 선택(결과는 `ResultEventBus`) | M3 ✅ |
| `presentation/nav` | `AppNavViewModel`(온보딩 여부로 시작 화면 결정), `RegionResult` | M3 ✅ |
| `presentation/text/AppErrorText.kt` | `AppError` → `UiText` 매핑 (한 곳에서만) | M3 ✅ |
| `presentation/home` | 홈: 목표 진행 링(`StepProgressRing`), 환산값, 목표 변경(`GoalDialog`), 권한·센서 미지원 상태 | M4 ✅ |
| `presentation/home/components/RewardTiersCard` | 리워드 구간 버튼 (잠김/받기/받음) | M5 ✅ |
| `presentation/wallet` | 지갑: 잔액 + 적립 내역 (리워드 구간 / 보물상자 / 기타) | M6 ✅ |
| `presentation/ranking` | 동네 랭킹: 일간/주간, 내 순위 고정, 상위 50, 당겨서 새로고침, 동네 변경 | M7 ✅ |
| `presentation/walkmap` | 동네걷기 지도: 네이버 지도(`NaverMapView`), 보물상자 열기, 위치 권한 상태 | M6 ✅ |
| `data/location` | `FusedLocationRepository` (구독 중에만 위치 요청) | M6 ✅ |
| `domain/geo` | 하버사인 거리, 미터 단위 좌표 이동 | M6 ✅ |
| `presentation/history` | 기록: 일/주/월 탭, 기간 이동, 합계·평균·달성 일수, Canvas 막대 그래프(`StepBarChart`) | M4 ✅ |
| `presentation/nav` | `NavKeys.kt`, `AppNavHost.kt` (Navigation 3) | M1 ✅ |
| `presentation/text` | `UiText` + `asString()` (i18n-safe 메시지) | M1 ✅ |
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

## 5. 홈 & 기록 (M4)

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 달성 판정 기준 | **날짜별 목표를 `daily_steps.goal`에 저장** (그날 첫 기록 시 현재 목표, 목표 변경 시 오늘 행만 갱신) | 현재 목표로 모든 날 판정: 목표를 바꾸면 지난 기록의 달성 여부가 바뀐다. |
| 스키마 변경 | **Room AutoMigration v1→v2** (`goal` 기본값 10000) + `MigrationTestHelper` 테스트 | 수동 `Migration`: 열 하나 추가는 AutoMigration으로 충분. `fallbackToDestructiveMigration`: 걸음 기록이 지워진다. |
| 목표 저장소 | **DataStore** (`GoalStore`) | SharedPreferences: 동기 읽기가 필요 없고, Flow로 변경이 바로 화면에 반영되는 DataStore가 설정값에 맞다 (토큰은 동기 읽기 때문에 SharedPreferences, §4). |
| 기본 목표 | **10,000보** | 6,000~8,000보: 앱이 "만보기"이고 리워드 마지막 구간도 1만 보라 맞춘다. |
| 환산값 | **평균값 고정**: 보폭 0.7m, 0.04kcal/보, 100보/분 | 키·몸무게 입력: 비범위. |
| 일 평균 | **기간 시작~오늘까지 지난 날수로 나눔** (기록 없는 날 포함) | 기록 있는 날만으로 나눔: 걷지 않은 날이 빠져 평균이 부풀려진다. |
| 그래프 | **Compose Canvas 직접 구현** | 차트 라이브러리: 막대 하나 그리는 데 의존성을 늘릴 필요가 없고 직접 그려 보는 것이 학습 목표 (TODO). |
| 그래프 라벨 | **ViewModel은 막대 값만, 요일·날짜 문구는 Screen이 리소스로 생성** | ViewModel에서 문자열 생성: 지침의 i18n 규칙 위반. |
| 리워드 구간 버튼 | **M5로 이동** | M4에서 상태만 표시: 버튼 상태(수령 가능/완료)의 원천이 서버라 리워드 API 없이는 의미가 없고 M5에서 다시 고쳐야 한다. |

### 검증
- 단위 테스트: `StepMetricsTest` 3, `HistoryRangeTest` 5, `HistorySummaryTest` 3, `HomeViewModelTest` 4, `HistoryViewModelTest` 4 (Turbine)
- 계측 테스트: `MigrationTest` (v1 데이터 → v2 유지·기본 목표), 목표 기록 테스트 (오늘만 변경, 지난날 유지)
- 에뮬레이터: 목표 3,000보로 변경 → 홈 즉시 반영, 기록 주/일/월 전환·이전/다음 이동, 오늘 포함 기간에서 다음 비활성
- 확인 못 한 것: 실제 기기에서 v1 DB가 있는 상태로의 업그레이드 (계측 테스트 실행이 에뮬레이터 앱을 지워 새 설치가 됨). 마이그레이션 자체는 `MigrationTest`가 v1 스키마로 검증.

## 6. 리워드 & 지갑 (M5)

```
[받기] 탭 → HomeViewModel.onClaim(tier)
  ① 응답 대기 중이거나 이미 받은 구간이면 무시 (연타 방지)
  ② 낙관적 반영: inFlight 에 넣어 바로 "받음" 표시
  ③ StepRepository.syncPendingSteps()  ── 실패 → 되돌림 + 안내 (수령 요청 안 함)
  ④ RewardRepository.claim(오늘, tier)  ── 실패 → 되돌림 + 코드별 안내 (NOT_REACHED / NOT_TODAY / 네트워크)
  ⑤ 성공 → claimed 에 확정, "+NP 적립! 잔액 MP" (alreadyClaimed 면 "이미 받은 리워드") → 서버 상태 새로고침
안내 메시지는 HomeEffect(Channel) → Route 가 Snackbar 로 표시 (UiState 에 섞지 않음)
```

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 버튼 상태의 기준 | **"받음"은 서버, "받기 가능"은 기기 걸음** | 서버 상태만: 서버 걸음은 동기화 주기(최대 15분)만큼 늦어 이미 걸은 구간이 한동안 잠겨 보인다. 기기 걸음만: 다른 기기·재설치 후 이미 받은 구간을 다시 받을 수 있는 것처럼 보인다. 지급 판정은 항상 서버 (PRD Q5). |
| 수령 직전 동기화 | **수령 요청 전에 미동기화 걸음을 먼저 업로드** | 그냥 요청: 기기에서는 받기 가능인데 서버가 `REWARD_NOT_REACHED`로 거절하는 일이 잦아진다. |
| 반영 방식 | **낙관적 UI** (먼저 "받음", 실패 시 되돌림) | 응답 후 반영: 동기화 + 수령 왕복(실측 약 1.5초) 동안 버튼이 그대로라 다시 누르게 된다. |
| 중복 수령 방지 | **클라이언트 연타 방지(inFlight) + 서버 멱등(`(user, date, tier)` 유니크, `alreadyClaimed`)** | 클라이언트만: 앱 재시작·네트워크 재시도에서 중복될 수 있다. 서버만: 동작은 맞지만 불필요한 요청이 나간다. |
| 일회성 안내 | **`HomeEffect` Channel → Snackbar** | UiState 에 메시지 필드: 화면 회전·재구독 때 같은 메시지가 다시 뜬다 (지침). |
| 리워드 새로고침 시점 | **날짜 변경, 화면 복귀(onResume), 수령 성공 후** | 주기적 폴링: Mock·서버에 불필요한 부하. |
| 새로고침 실패 | **이미 받은 데이터가 있으면 유지**, 처음 실패만 오류 + 재시도 | 실패마다 오류 표시: 일시적 실패로 버튼이 사라졌다 나타난다. |
| 지갑 구간 표시 | **적립 사유 `REWARD_TIER_N` → 구간 번호로 매핑, 걸음 수 문구는 Screen** | 서버 사유 문자열 그대로 노출: 사용자용 문구가 아니다. |

### 검증
- 단위 테스트: `HomeRewardClaimTest` 8 (기기 걸음 기준 받기 가능, 낙관적 반영·동기화 순서, 적립 안내, 미달 거절 되돌림, 동기화 실패 시 미요청, 연타 1회, 이미 받음 안내, 서버 받음 비활성), `WalletViewModelTest` 2, `RewardRepositoryImplTest` 2 (Mock 경로 매핑·에러 코드)
- 에뮬레이터: 1구간 수령 — 탭 0.3초 뒤 "받음"(응답 전 낙관적 반영), 요청 순서 `steps/sync → rewards/claim → rewards/today`, 받은 뒤 연타 시 추가 요청 0건, Snackbar "1P 적립! 잔액 1P", 지갑 잔액 1P·내역 "1,000보 리워드"
- 에뮬레이터: 동시 401(`/rewards/today` + `/steps/sync`)에도 리프레시·재가입은 1회 (M3 잠금 확인)

## 7. 동네걷기 지도 & 보물상자 (M6)

상자는 동네와 관계없이 어디에나 있다 (PRD Q25). 동네는 랭킹에만 쓴다.

```
WalkMapRoute ── 위치 권한(정확/대략/거부) 확인·요청, Snackbar
   └ WalkMapViewModel
        permission ─flatMapLatest→ LocationRepository.locationUpdates()   (권한 있을 때만, WhileSubscribed 로 화면이 사라지면 해제)
            └ 다른 격자 칸(GeoCell, 0.005도)에 들어서면 TreasureRepository.getNearbyTreasures(내 위치)  ← 이전 요청은 취소
        combine → 상자별 거리, 가장 가까운 닫힌 상자, 열기 가능 여부(정확한 위치 + 실제 거리 ≤ 50m + 정확도 ≤ 50m + 하루 5개 미만)
        onOpenNearest → TreasureRepository.open(id, 현재 위치) → 성공 시에만 열림 반영
   └ NaverMapView (AndroidView + MapView 생명주기 직접 전달) ── 상자 마커, 내 위치 (처음 위치를 알 때 카메라 이동)
Mock: MockTreasureField((칸, 날짜) 시드 → 칸마다 2개, id = 칸_날짜_번호) + MockServer(주변 3×3칸 조회, id 로 위치 재계산해 검증 → 적립)
```

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 상자 배치 범위 | **동네와 무관, 어디에나** (Q25) | 동네 중심 반경 1km(Q22, 처음 구현): 동네는 목록 선택이라 실제 위치와 달라 다른 곳에서는 미션을 못 한다 (에뮬레이터에서 발견). |
| 공간 분할 | **위경도 0.005도 격자 칸** | 지오해시: 인코딩·이웃 계산이 필요하고 정밀도마다 칸 모양이 다르다 (서버 저장·검색이 필요해지면 재검토). H3 육각형: 네이티브 의존성. 내 위치 기준 생성: 사람마다 상자가 달라지고 제자리 재요청으로 남용된다. |
| 상자 위치 저장 | **(칸, 날짜) 시드로 매번 같은 위치 생성, 상자 id 에 칸·날짜를 담아 서버가 위치를 다시 계산** | 날마다 생성해 저장: 칸이 무한해 미리 만들 수 없고, 저장소·배치가 필요하다. |
| 조회 범위 | **내 위치 칸 + 주변 8칸(3×3, 약 1.5km)**, 다른 칸에 들어서면 다시 조회 | 지도 화면 영역(viewport) 기준: 지도를 크게 축소하면 요청 칸 수가 폭증한다. 위치가 바뀔 때마다 조회: 같은 칸 안의 이동은 결과가 같아 낭비. |
| 하루 상한 | **5개(10P)** | 상한 없음: 상자가 무한해 보상이 무한해진다. |
| 지도 Compose 연동 | **`AndroidView` + `MapView` 생명주기 직접 전달** | 비공식 Compose 래퍼: 지도 SDK 버전에 묶이는 의존성이 하나 더 생긴다. |
| 열기 반영 | **서버 응답 후 반영 (낙관적 반영 안 함)** | 낙관적 반영(M5 리워드 방식): 위치 검증으로 거절될 수 있어 되돌림이 잦고 사용자를 혼란스럽게 한다. |
| 열기 버튼 판정 | **앱도 서버와 같은 기준(실제 거리 ≤ 50m, 정확도 ≤ 50m, 상한 미만)으로 활성화, 최종 판정은 서버** | 표시용 반올림 거리로 판정: 50.4m 를 50m 로 보고 켜졌다가 서버가 거절한다 (테스트로 발견해 수정). |
| 이동 속도 기준 시각 | **서버 수신 시각** | 앱이 보낸 `measuredAt`: 기기 시계를 바꾸면 우회된다. |
| 이미 연 상자 | **위치·상한 검사보다 먼저 멱등 응답(`alreadyOpened`)** | 위치부터 검사: 재시도할 때 위치가 바뀌었으면 이미 받은 보상에 오류가 뜬다. |
| 오늘 연 개수 | **상자 id 의 날짜로 세기** (따로 저장 안 함) | 사용자별 날짜 카운터: 열기 기록과 이중으로 관리해야 한다. |
| 위치 사용 범위 | **지도 화면 구독 중에만 Fused Location 요청** (백그라운드 위치·FGS location 없음) | 백그라운드 위치: 권한·배터리·개인정보 부담 (PRD Q20). |
| 대략적 위치만 허용 | **주변 상자는 보이되 열기 불가 + 설정 안내** | 지도 자체를 막음: 상자 위치 확인은 대략적 위치로도 의미가 있다. |
| Client ID | **`local.properties` → `manifestPlaceholders` → `NCP_KEY_ID` meta-data** | 코드에 하드코딩: 키가 Git에 남는다. |

### 알려진 한계
- **걸어갈 수 없는 곳의 상자**: 칸 안에서 무작위로 위치를 정해 바다·강·건물 안에도 상자가 생긴다 (에뮬레이터 해운대에서 확인). 원본은 가게 근처에 두는데, 그러려면 장소(POI)·도로 데이터가 필요하다.
- 위도가 높을수록 칸의 동서 폭이 줄어든다 (국내 범위에서는 차이가 작다).

### 검증
- 단위 테스트: `GeoCellTest` 4, `GeoDistanceTest` 3, `MockTreasureTest` 9 (어디서나 3×3칸 18개, 결정성·날짜별 변경, 열기·멱등, 거리/가짜 GPS/정확도 거부, 하루 5개 상한, 순간이동 거부 후 허용, 날짜·id 검사, 좌표 검증), `WalkMapViewModelTest` 10 (열기 가능 판정·반올림 경계·상한, 대략적 위치, 권한 없으면 위치·상자 미요청, 칸이 바뀔 때만 재조회, 열기 성공·거부, 연타)
- 에뮬레이터(Pixel 8 API 37, `adb emu geo fix`)
  - 역삼동 기준 구현(Q22)에서: 174m에서 열기 비활성 → 상자 옆 열기 성공 → 630m 순간이동 후 `TREASURE_TOO_FAST` 거부 안내
  - 격자 방식(Q25)에서: 동네를 역삼동으로 설정하고 **부산 해운대**에서 지도를 열자 해운대 주변 상자 표시(가장 가까운 126m) → 상자 7m 옆에서 열기 성공(1/5) → 칸 경계를 넘을 때마다 `/treasures/nearby` 재조회
  - 확인 못 함: 같은 칸 안 이동 시 재조회하지 않는 것(단위 테스트로만 검증), 가짜 GPS 거부(`geo fix`는 모의 위치로 표시되지 않음)

## 8. 동네 랭킹 (M7)

```
RankingRoute ── ResultEffect<RegionResult> (동네 변경 결과), Snackbar
   └ RankingViewModel ── 기간 전환·새로고침 시 이전 요청 취소(loadJob)
        └ RankingRepository (@Singleton, Mutex)
             캐시가 5분 안이면 그대로 → 아니면 StepRepository.syncPendingSteps() → GET /ranking → 기간별 캐시
             동네 변경 시 invalidate()
```

### 설계 결정 (선택하지 않은 대안 포함)

| 결정 | 선택 | 선택하지 않은 대안 → 이유 |
|---|---|---|
| 캐시 위치 | **메모리, 기간별, 5분** (PRD §6) | Room 저장: 랭킹은 금방 바뀌고 앱을 다시 열면 새로 받는 게 맞아 영속화 이점이 작다. 캐시 없음: 탭 전환·재진입마다 요청이 나간다. |
| 내 순위 정확도 | **새로 받을 때 내 미동기화 걸음을 먼저 업로드** (실패해도 랭킹은 표시) | 그냥 조회: 서버 걸음이 최대 15분 늦어 내 순위가 실제보다 낮게 보인다. |
| 동시 요청 | **Repository `Mutex`로 같은 요청을 직렬화** | 보호 없음: 화면 진입과 새로고침이 겹치면 같은 요청·동기화가 두 번 나간다. |
| 늦게 도착한 응답 | **ViewModel 에서 이전 요청 Job 취소** | 그대로 반영: 탭을 빠르게 바꾸면 이전 기간 응답이 현재 탭을 덮는다. |
| 새로고침 실패 | **기존 목록 유지 + Snackbar**, 처음 실패만 오류 화면 | 항상 오류 화면: 일시적 실패로 보던 목록이 사라진다. |
| 내 순위 표시 | **목록 밖 고정 카드 + 목록 안 내 행 강조** | 목록 안에서만 표시: 50위 밖이거나 스크롤하면 내 순위가 안 보인다. |
| 동네 변경 위치 | **랭킹 화면에서 변경** (동네는 랭킹 전용, PRD Q25), 기존 동네 선택 화면을 `ResultEventBus`로 재사용 | 별도 설정 화면: 동네를 쓰는 곳이 랭킹뿐이라 동선이 길어진다. |

### 해결하지 못한 문제: "NavDisplay backstack cannot be empty" 크래시 (1회)
- 상황: 에뮬레이터에서 온보딩 → 랭킹 → 일간/주간 전환 → 동네 변경 → 성수동 선택 직후 1회 크래시.
- 조사: 같은 순서, 같은 프로세스 온보딩 직후, 데이터 삭제 후, 새 설치 + 뒤로가기 등 4가지 조건으로 재현을 시도했으나 모두 정상. 스택에는 백스택이 비었다는 결과만 남아 원인 지점을 특정하지 못했고, 최초 크래시 직전 로그는 재현 과정에서 지워졌다. 백스택이 하나뿐일 때 뒤로가기는 NavDisplay 가 처리하지 않고 액티비티가 종료된다(크래시 아님)는 것은 확인했다.
- 조치(증상 차단): 백스택에서 빼는 두 곳(`onBack`, 동네 선택 결과)을 `pop()`으로 모아 마지막 화면은 빼지 않게 하고, 시도가 있으면 `Timber.w`로 호출 위치와 백스택을 기록한다. 다시 발생하면 이 로그로 원인을 추적한다.

### 검증
- 단위 테스트: `RankingRepositoryImplTest` 6 (Mock 경로 실제 요청 횟수로 5분 캐시·만료·강제 새로고침·기간별 분리·동네 변경 무효화·실패 미캐시 확인), `RankingViewModelTest` 6
- 린트로 발견해 수정: `LocalTime.ofInstant`는 API 31+ 라 minSdk 30(Android 11) 기기에서 랭킹 화면이 크래시 → `atZone().toLocalTime()`
- 에뮬레이터: 랭킹 진입 시 `steps/sync → ranking?period=daily` 순서, 내 순위 고정 카드·"HH:mm 기준", 이번 주 탭 → 오늘 복귀 시 캐시 사용(재요청 없음), 동네 변경(역삼동 → 성수동) 후 "성수동 랭킹" 표시

## 9. Mock 서버 (Phase A)

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

### 실제 서버로 전환 (M11)

1. `app/build.gradle.kts`에서 `USE_MOCK_API = false`, `BASE_URL`을 빌드 타입별 서버 주소로 변경
2. `AndroidManifest.xml`에 `INTERNET` 권한 추가 (Mock은 실제 네트워크를 쓰지 않아 지금은 없음)
3. `docs/api.md` 기준으로 Mock과 서버 응답 차이 점검

## 10. 빌드 메모

- AGP 9 내장 Kotlin + KSP 2.0.x 조합 때문에 `gradle.properties`에 `android.disallowKotlinSourceSets=false`가 필요하다 (실험적 옵션 경고가 뜨는 것은 정상). KSP가 `android.sourceSets` 방식을 지원하면 제거한다.
- Hilt 2.60 생성 코드가 `@CanIgnoreReturnValue`를 참조해 `error_prone_annotations`를 `compileOnly`로 추가했다.
- Navigation 3의 `ResultEventBus`는 1.2.0-alpha 라인에만 있어 alpha 버전을 쓴다.
- Room 스키마는 `app/schemas/`에 내보낸다 (마이그레이션 테스트용, 커밋 대상).
- KDoc·블록 주석 안에 `/*` 를 쓰지 않는다 (예: `/auth/*`). Kotlin 블록 주석은 중첩되어 주석이 닫히지 않고 뒤의 클래스 전체가 사라진다 — KSP가 "could not be resolved"로만 알려 준다.
- 16KB 페이지 호환성: 모든 `.so`의 LOAD 세그먼트는 16KB 정렬이지만, `libdatastore_shared_counter.so`(datastore 1.2.0)와 `libandroidx.graphics.path.so`(Compose)의 **GNU_RELRO 끝이 4KB 단위**라 API 37 에뮬레이터에서 호환 모드 경고가 뜬다. `libnavermap.so`(3.24.0)는 ELF 헤더상 RELRO까지 16KB 정렬이지만, API 37 호환성 대화상자는 이 라이브러리도 "Unknown error"로 표시한다(원인 미확인). 앱은 동작하지만 후속 처리 필요 (TODO 공통 항목).
- `connectedDebugAndroidTest`는 테스트가 끝나면 대상 기기의 앱을 **삭제**한다. 기기 데이터로 업그레이드를 확인하려면 계측 테스트 전에 한다.
- `connectedDebugAndroidTest`는 연결된 **모든 기기**에서 실행된다. 에뮬레이터만 쓰려면 `ANDROID_SERIAL=emulator-5554`를 지정한다.
