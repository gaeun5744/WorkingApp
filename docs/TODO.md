# 만보기 앱 클론 — 구현 TODO

> 당근 만보기를 참고해 **앱 시스템 디자인**을 연습하기 위한 프로젝트.
> 요구사항과 모든 결정의 근거(선택하지 않은 대안 포함)는 `docs/PRD.md`가 기준이다. 이 문서는 "무엇을 어떤 순서로 만들지"만 다룬다.
> Android 작업은 `.agents/AGENTS.md` 지침을, 서버 작업은 `.agents/llm-rules/server/`(Phase B에서 작성) 지침을 따른다.

- 구현 순서 (PRD Q17): **Phase A — 앱을 Mock 서버로 완성** → **Phase B — 실제 서버 구현 후 전환**
- 브랜치 전략: `main`(릴리스 가능한 상태) ← `develop`(통합) ← `feature/*`(마일스톤 단위). 각 마일스톤이 끝나면 `develop`에 `--no-ff` 머지 후 다음 브랜치를 딴다.
- 커밋 메시지 설명은 한국어로 작성하고, 커밋/푸시는 명시적으로 요청할 때만 한다.
- 저장소 구조: 루트 = Android 앱(`:app`), `server/` = 독립 Gradle 프로젝트(Phase B). DTO는 공유하지 않고 `docs/api.md`가 계약의 기준.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 앱 | 단일 모듈 `:app`, Kotlin, Compose(Material3), Hilt, Retrofit + OkHttp + kotlinx.serialization, Navigation 3 + `ResultEventBus`, Timber, Room, SharedPreferences(세션), DataStore(설정), WorkManager, Foreground Service |
| Mock 서버 (Phase A) | 앱 내부 OkHttp `MockApiInterceptor` (PRD Q18) |
| 서버 (Phase B) | Spring Boot (Kotlin), Spring Data JPA + 네이티브 upsert, Flyway, PostgreSQL, Redis, JWT |
| 인프라 (Phase B) | Docker Compose(로컬·운영 동일), VM 1대, Caddy(HTTPS), GitHub Actions |
| 외부 (Phase B) | Play Integrity API (Play Console 내부 테스트 트랙 + Google Cloud 프로젝트) |

---

## M0. Interview & PRD ✅

- [x] 범위 / 비범위, 수용 기준, 엣지 케이스, 의존성 확정 → `docs/PRD.md` §1~7
- [x] 되돌리기 어려운 결정 grilling (Q1~Q18) → `docs/PRD.md` PRD Interview

---

# Phase A — 앱 (Mock 서버)

## M1. 앱 기반 & API 계약 & Mock 서버 (`feature/app-setup`, `feature/mock-api`)

### 1-1. 프로젝트 기반 설정
- [x] 의존성 추가 (`gradle/libs.versions.toml`)
  - Hilt + `androidx.hilt:hilt-lifecycle-viewmodel-compose`
  - Navigation 3, Retrofit, OkHttp, kotlinx.serialization
  - Timber, `kotlinx-collections-immutable`, `lifecycle-runtime-compose`
  - 테스트: `kotlinx-coroutines-test`
  - Room·WorkManager는 M2, DataStore·Turbine·room-testing은 M4에서 실제로 쓸 때 추가 (최소 수정 원칙). MockWebServer는 쓰지 않음 (M3 변경 참고)
- [x] `@HiltAndroidApp` Application(Timber 초기화 포함), `MainActivity`에 `@AndroidEntryPoint`
- [x] `presentation/nav/AppNavHost.kt`에 `NavDisplay` + `rememberResultEventBusNavEntryDecorator()`
- [x] `presentation/nav/NavKeys.kt`에 화면 키 정의 (온보딩 / 홈 / 기록 / 랭킹 / 지갑 / 동네 선택) — 각 화면은 `PlaceholderScreen`으로 임시 연결
- [x] 공통 `UiText`(i18n-safe 메시지 래퍼) 정의
- [x] 검증: `./gradlew :app:assembleDebug`
- [x] 에뮬레이터 실행 확인 (Pixel 8 API 37: 5개 화면 이동 + 뒤로가기, 크래시 없음)

### 1-2. 앱 패키지 구조 (`llm-rules/data/layer-contract.md` + 확장)

의존 방향: `presentation → domain ← data`. **UseCase 레이어 없이 ViewModel이 Repository를 직접 호출한다.**

```
data/remote/api      Retrofit API 인터페이스
data/remote/dto      @Serializable DTO + toDomain() 매퍼
data/remote/mock     MockApiInterceptor + Mock 상태/응답    ← Phase A 전용
data/local/entity    Room Entity + toDomain() 매퍼        ← 지침 확장
data/local/dao       Room DAO                              ← 지침 확장
data/sensor          센서 데이터 소스 (StepDataSource)     ← 지침 확장
data/repository      Repository 구현체
data/di              Hilt 모듈 (NetworkModule, DatabaseModule, RepositoryModule)
domain/model         순수 Kotlin 도메인 모델
domain/repository    Repository 인터페이스
domain/step          걸음 계산 로직 (순수 Kotlin)          ← 지침 확장
presentation         Activity, ViewModel, UiState, Composable
presentation/nav     NavKey, AppNavHost, 화면 간 전달 데이터
service              Foreground Service, BootReceiver       ← 지침 확장
worker               WorkManager Worker                     ← 지침 확장
```

- [x] `docs/architecture.md`에 구성도, 지침 확장 근거, Mock → 실제 서버 전환 방법 기록

### 1-3. API 계약 `docs/api.md` ★ (앱·Mock·서버 공통 기준)
- [x] 공통: 인증 헤더(`Authorization: Bearer`), 에러 응답 형식, 날짜 형식(KST `yyyy-MM-dd`)
- [x] 엔드포인트별 요청/응답·에러 코드 + DTO·`PedometerApi`로 코드화
  - `POST /auth/anonymous` — 익명 가입 + 토큰 발급
  - `POST /auth/refresh` — 토큰 재발급
  - `GET /me` — 내 정보 (동네 설정 여부 확인용, 작성 중 추가)
  - `GET /regions?query=` — 동네 검색
  - `PUT /me/region` — 내 동네 설정
  - `POST /steps/sync` — 날짜별 누적 + 시간대별 걸음 배치 업로드
  - `GET /rewards/today` — 오늘 구간별 리워드 상태
  - `POST /rewards/claim` — 리워드 수령 (`integrityToken`은 선택 필드, Phase B에서 필수화)
  - `GET /wallet` — 잔액 + 적립 내역
  - `GET /ranking?period=daily|weekly` — 내 동네 랭킹 (상위 50 + 내 순위)

### 1-4. Mock 서버 (`data/remote/mock`)
- [x] `MockApiInterceptor`: 경로·메서드별로 `docs/api.md` 형식의 JSON 응답 생성
- [x] `BuildConfig.USE_MOCK_API` 플래그로 `NetworkModule`에서 Interceptor 장착 여부 결정 (Phase A는 모든 빌드 true)
- [x] 메모리 상태로 서버 규칙 흉내
  - 걸음: 날짜별 `max(기존, 새 값)` 반영, 하루/시간당 상한 자르기
  - 리워드: `(date, tier)` 중복 수령 시 기존 결과 반환, 잔액·내역 갱신
  - 랭킹: 시드된 가짜 이웃 + 내 걸음으로 일간/주간 순위 계산
  - 토큰: 만료 시간을 짧게 설정해 401 → 재발급 흐름 재현
- [x] 디버그용 지연(예: 300~800ms)·오류 주입 설정 (오류 UI·재시도 확인용)
- [x] 단위 테스트: Mock 규칙 (`MockApiTest` 13개 — 인증·멱등 upsert·상한·중복 수령·랭킹·오류 주입)

---

## M2. 걸음 측정 & 로컬 저장 (`feature/m2-step-sensor`) ✅

설계 결정과 대안은 `docs/architecture.md` §3.

### 2-1. 누적값 → 걸음 변환 (`domain/step`) ★
- [x] 측정마다 증분(현재 − 직전)을 측정 시각(KST)의 날짜·시간대에 더하는 `StepDeltaCalculator` (baseline 방식 대신)
- [x] **재부팅 감지**: `BOOT_COUNT` 비교 우선, 알 수 없으면 값 감소로 판정
- [x] **늦게 도착한 측정**: 같은 부팅인데 값이 줄면 무시
- [x] **자정 경계**: 측정 시각 기준으로 날짜가 정해져 별도 리셋 불필요
- [x] 시간대별(hourly) 걸음 집계
- [x] 기기 시간대와 무관하게 KST 기준 계산
- [x] 단위 테스트 먼저 작성 (`StepDeltaCalculatorTest` 8개)

### 2-2. 센서 & 백그라운드 측정
- [x] `StepDataSource` + `AndroidStepDataSource`(STEP_COUNTER, 배칭 없음 — 실기기 디버깅으로 변경, 측정 시각 보정) + `SimulatedStepDataSource`(`-PfakeStepSensor=true`)
- [x] `StepCounterService`(FGS type=health) + 상시 알림 "오늘 N걸음"
- [x] `BootCompletedReceiver`: 재부팅·앱 업데이트 후 서비스 재시작
- [x] `StepSnapshotWorker` 15분 주기 (HiltWorkerFactory)
- [x] 권한: `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE_HEALTH`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`
  - 권한 요청은 Route에서 처리하고 ViewModel에는 결과만 전달
  - 1차 홈 화면: 오늘 걸음 / 권한 필요 / 센서 미지원 / 로딩
- [x] 검증: 에뮬레이터에서 강제 종료·재부팅 후 걸음 유지 (`docs/architecture.md` §3 검증)
- [x] 실제 기기(SM-S916N, STEP_COUNTER 하드웨어)에서 사용자가 직접 설치·확인 — 권한 허용, FGS(type=health) 실행, 크래시 없음

### 2-3. 로컬 저장소
- [x] Room: `daily_steps(date, steps, updated_at, synced)`, `hourly_steps(date, hour, steps)`, `counter_checkpoint`(1행)
- [x] 증분과 체크포인트를 `withTransaction`으로 함께 저장
- [x] Entity → Domain 매퍼
- [x] `DatabaseModule` (`@Singleton`), 스키마 `app/schemas/` 내보내기
- [x] 계측 테스트: `StepRepositoryImplTest` 4개 (Room in-memory, 동시 기록 포함)
- 변경: baseline은 체크포인트로 대체되어 DataStore 불필요 (토큰은 M3에서 SharedPreferences로 결정). `sensor_snapshot` 테이블은 두지 않음.
- 목표 걸음(`goal`)은 M4에서 설정 기능과 함께 추가

---

## M3. 인증 & 동기화 (Mock) (`feature/m3-auth-sync`) ✅

설계 결정과 대안은 `docs/architecture.md` §4, 재가입 정책은 PRD Q19.

- [x] API 구현 순서 준수: DTO → Mapper → Domain → Repository 인터페이스 → 구현체 → DI → ViewModel → Screen
- [x] `NetworkModule`: 일반 클라이언트(`AuthInterceptor` → Mock) + 갱신 전용 `@AuthClient` 클라이언트, `PedometerApi`에서 인증 헤더 파라미터 제거
- [x] 온보딩: 첫 실행 시 닉네임 입력 → 익명 가입 → 동네 선택 → 홈 (중간에 껐다 켜면 동네 단계부터)
- [x] 동네 선택 화면(검색 debounce + flatMapLatest) → 결과는 `ResultEventBus`로 반환
- [x] 401 처리: `AuthInterceptor`에서 리프레시 → 거부 시 저장된 닉네임·동네로 조용히 재가입 + 걸음 재업로드 표시 (OkHttp `Authenticator` 대신 — Mock 401을 보지 못함)
- [x] `StepSyncWorker`(Hilt Worker): 15분 주기 + 앱 시작·온보딩 완료 시, 네트워크 조건, 지수 백오프
- [x] `synced` 플래그 기반 증분 업로드 (최근 7일), 업로드 중 변경 시 미표시
- [x] 오류는 Repository에서 `AppError`로 변환, UI에는 `UiText`
- [x] 검증: 계측 테스트(토큰 만료·재가입·동기화), 에뮬레이터에서 온보딩·Mock 재시작 재가입 흐름
- 변경: 토큰 저장은 DataStore 대신 SharedPreferences (인터셉터의 동기 읽기, `runBlocking` 금지). MockWebServer 의존성은 Mock 인터셉터로 충분해 추가하지 않음.
- 남음: 오류 주입(`failureRate`)으로 재시도 UI 확인은 화면별 재시도가 생기는 M4~M6에서 함께 확인

---

## M4. 화면: 홈 & 기록 (`feature/m4-home-history`) ✅

설계 결정과 대안은 `docs/architecture.md` §5.

- [x] 홈: 오늘 걸음 + 원형 진행 바, 거리/칼로리/시간 환산, 목표 변경(다이얼로그)
- [x] `HomeViewModel`: 걸음 Flow + 목표 + 권한을 `combine` → `stateIn`
- [x] 날짜별 목표 저장(`daily_steps.goal`, Room v2 AutoMigration + 마이그레이션 테스트), 목표는 DataStore
- [x] 기록: 일/주/월 탭 + 막대 그래프(Compose Canvas), 합계·일 평균·목표 달성 일수, 이전/다음 이동
- [x] 목록 상태는 `ImmutableList`, 수집은 `collectAsStateWithLifecycle()`
- [x] `@Preview`로 상태별 화면 확인 (홈 4, 기록 2, 링·그래프 컴포넌트)
- [x] ViewModel 테스트 (Turbine): 홈 4, 기록 4
- 변경: 리워드 구간 버튼(잠김/수령 가능/수령 완료)은 서버 상태가 필요해 M5로 이동

---

## M5. 리워드 & 지갑 (Mock) (`feature/m5-reward-wallet`) ✅

설계 결정과 대안은 `docs/architecture.md` §6.

- [x] 홈에 리워드 구간 버튼 (잠김 / 받기 / 받음, M4에서 이동) — "받음"은 서버, "받기 가능"은 기기 걸음 기준
- [x] 수령 전 최신 걸음 동기화 → 수령 API (Play Integrity 토큰은 Phase B에서 추가)
- [x] 낙관적 UI 업데이트 → 실패 시 롤백 + 코드별 안내(Snackbar, `HomeEffect`)
- [x] 버튼 연타·재시도에도 중복 수령 없음 (클라이언트 inFlight + Mock 멱등 응답)
- [x] 지갑 화면: 잔액 + 적립 내역, 오류 시 재시도
- [x] 테스트: 수령 흐름 8, 지갑 2, Repository(Mock 경로) 2
- [x] 에뮬레이터: 1구간 수령 — 탭 즉시 "받음"(낙관적), sync → claim → today 순 요청, 연타 추가 요청 0건, "1P 적립! 잔액 1P" 안내, 지갑 1P·내역 표시

---

## M6. 동네걷기 지도 & 보물상자 (Mock) (`feature/m6-walk-map`) ✅

원본 당근 "동네걷기"의 지도 미션을 참고한다 (PRD Q20~Q25). 설계 결정과 대안은 `docs/architecture.md` §7.

### 6-1. 사전 준비
- [x] 네이버 클라우드 플랫폼 Maps Client ID (사용자 발급) → `local.properties`의 `NAVER_MAP_CLIENT_ID` → `manifestPlaceholders` → `com.naver.maps.map.NCP_KEY_ID`
- [x] 네이버 지도 SDK 3.24.0 + `play-services-location` 21.3.0, `libnavermap.so` 16KB 정렬 확인 (LOAD·RELRO 모두 정렬)

### 6-2. API 계약 & Mock
- [x] `docs/api.md`: `GET /treasures/nearby?latitude&longitude`(주변 3×3칸), `POST /treasures/{id}/open`, 에러 코드
- [x] Mock: 위경도 0.005도 격자 칸마다 (칸, 날짜) 시드로 2개 결정적 생성, id 로 위치 재계산해 검증(날짜 → 멱등 → 하루 5개 상한 → 가짜 GPS → 정확도 → 거리 → 이동 속도), 2P 적립(`TREASURE`)
- [x] 단위 테스트 (`GeoCellTest` 4, `MockTreasureTest` 9)
- 변경: 처음에는 동네 중심 반경 1km(Q22)로 구현했으나, 동네(목록 선택)와 실제 위치가 다를 수 있어 동네와 무관한 격자 방식으로 바꿈 (Q25). 동네는 랭킹 전용

### 6-3. 앱
- [x] `LocationRepository`(`FusedLocationRepository`, 구독 중에만 요청) + 테스트용 Fake
- [x] 위치 권한 흐름: 정확 / 대략적(상자 보기만, 열기 불가) / 거부(요청·설정 이동)
- [x] `TreasureRepository`, 지갑 적립 사유 `WalletReason`(리워드 구간 / 보물상자 / 기타)
- [x] 동네걷기 지도 화면: 네이버 지도, 내 위치 중심, 상자 마커(열림 반투명), 가장 가까운 상자 거리, 오늘 N/5개
- [x] 다른 칸에 들어서면 주변 상자 재조회 (이전 요청 취소)
- [x] 50m 안이면 열기 활성화 → 서버 검증 → 결과 안내, 연타 방지
- [x] 홈에서 지도 화면 진입
- [x] 에뮬레이터: 동네(역삼동)와 다른 곳(해운대)에서 주변 상자 표시·열기 / 순간이동 거부 / 칸 이동 시 재조회 (`adb emu geo fix`)
- [x] ViewModel 테스트 (`WalkMapViewModelTest` 10)
- 남음(알려진 한계): 바다·강 등 걸어갈 수 없는 곳에도 상자가 생김 — 장소(POI)·도로 데이터가 필요해 Phase B(M9)에서 결정

---

## M7. 동네 랭킹 (Mock) (`feature/m7-ranking`) ✅

설계 결정과 대안은 `docs/architecture.md` §8.

- [x] 일간/주간 탭, 상위 50 리스트(내 행 강조) + 내 순위 고정 카드, "HH:mm 기준"
- [x] 5분 메모리 캐시(기간별), 당겨서 새로고침은 캐시 무시, 새로 받을 때 내 걸음 먼저 동기화
- [x] 오류 화면·재시도, 동네 미설정 시 동네 선택 안내, 새로고침 실패 시 목록 유지 + 안내
- [x] 랭킹 화면에서 동네 변경 (동네 선택 화면 재사용, `ResultEventBus`)
- [x] 테스트: `RankingRepositoryImplTest` 6, `RankingViewModelTest` 6
- [x] 임시 `PlaceholderScreen` 삭제 (모든 화면 구현 완료)
- 남음: 1회 발생 후 재현되지 않은 "backstack cannot be empty" 크래시 — 백스택 보호·로그로 증상 차단, 원인 미확인 (`architecture.md` §8)

### Phase A 완료 기준
- [x] 재부팅·강제 종료 후 걸음 유지 (M2 에뮬레이터)
- [ ] 자정 경계 — 단위 테스트로만 검증 (기기에서 자정을 넘겨 본 적 없음)
- [ ] 오프라인(비행기 모드) 후 동기화 — 미확인. Mock 재시작에 따른 401 → 재가입 → 재업로드 흐름만 에뮬레이터에서 확인 (M3)
- [x] 리워드 중복 수령 방지 (M5 에뮬레이터: 연타 시 추가 요청 0건, Mock 멱등)
- [x] 보물상자 열기·위치 검증 (M6 에뮬레이터: 열기 성공, 순간이동 거부)
- [x] 랭킹 조회 (M7 에뮬레이터)
- [ ] `develop` → `main` 머지

---

# Phase B — 서버 & 전환

## M8. 서버 기반: 인증 & 걸음 API (`feature/server-setup`)

- [ ] 서버용 에이전트 지침 작성: `.agents/llm-rules/server/` (레이어 구조, JPA·트랜잭션, 예외 처리, 테스트 규칙) + `AGENTS.md` 라우팅 표 갱신
- [ ] `server/` Spring Boot(Kotlin) 프로젝트 생성 (앱과 독립 Gradle 빌드)
- [ ] `docker-compose.yml`: Spring Boot + PostgreSQL + Redis
- [ ] 시간대 `Asia/Seoul` 고정, `Clock` 빈 주입
- [ ] Flyway 초기 스키마
  - `users(id, nickname, region_id, created_at)`
  - `regions(id, name)` + 시드 데이터
  - `refresh_tokens(user_id, token_hash, expires_at)`
  - `daily_steps(user_id, date, steps, suspicious, updated_at)` — PK `(user_id, date)`
  - `hourly_steps(user_id, date, hour, steps)`
  - `reward_claims(id, user_id, date, tier, points, claimed_at)` — UNIQUE `(user_id, date, tier)`
  - `wallets(user_id, balance)` / `wallet_transactions(id, user_id, amount, reason, created_at)`
- [ ] 공통 에러 응답 형식 (`docs/api.md`와 일치)
- [ ] 인증: 익명 가입 → 액세스(1시간) / 리프레시(30일) JWT, Spring Security 필터, 리프레시 토큰 해시 저장 + 교체(rotation)
- [ ] 걸음 동기화: `INSERT ... ON CONFLICT DO UPDATE SET steps = GREATEST(...)` (멱등)
- [ ] 검증 규칙: 하루 100,000 / 시간당 12,000 상한, 미래 날짜·시간대 거부, 최근 7일 밖 거부, 초과분 자르고 `suspicious` 기록
- [ ] 테스트: 같은 요청 반복, 동시 요청, 경계값 (Testcontainers PostgreSQL)

## M9. 리워드 & Play Integrity (`feature/server-reward`) ★

- [ ] `POST /rewards/claim`: 서버 `daily_steps`로만 구간 도달 판정, 오늘(KST) 것만 허용
- [ ] 한 트랜잭션에서 `reward_claims` insert(유니크 제약) + 지갑 잔액·내역 갱신, 중복 요청은 기존 결과 반환
- [ ] Play Console 앱 등록, 내부 테스트 트랙 배포, Google Cloud 프로젝트 연결
- [ ] 서버: Play Integrity 토큰 검증, 실패 시 지급 거부 / 로컬 개발용 검증 끄기 설정
- [ ] 앱: 수령 시 Play Integrity 토큰 요청 → `integrityToken` 전송 (`docs/api.md` 필수화)
- [ ] 테스트: 동시 수령 요청에도 1회만 적립
- [ ] 보물상자: (격자 칸, 날짜) 결정적 생성, 열기 서버 검증(하루 상한·거리·정확도·가짜 GPS·이동 속도), `(user, treasure)` 유니크로 멱등, 열기 위치·시각 기록
- [ ] 앱: 보물상자 열기에도 Play Integrity 토큰 첨부
- [ ] 보물상자 배치 개선 결정: 바다·강 등 걸어갈 수 없는 곳 제외 (장소(POI)·도로 데이터 기반 배치 등, M6 알려진 한계)

## M10. Redis 랭킹 (`feature/server-ranking`) ★

- [ ] Redis 키: `rank:daily:{regionId}:{date}`, `rank:weekly:{regionId}:{weekStart}` (월요일 시작, KST) + TTL
- [ ] 걸음 upsert 시 일간 `ZADD GT`, 주간은 실제 증가분만 `ZINCRBY`
- [ ] 상위 50명 + 내 순위 조회
- [ ] PostgreSQL → Redis 랭킹 재구축 기능
- [ ] 테스트: Redis 초기화 후 재구축 결과 일치

## M11. 앱 Mock → 실제 서버 전환 (`feature/switch-to-server`)

- [ ] `USE_MOCK_API = false`, 빌드 타입별 base URL (로컬 `10.0.2.2` / 운영 HTTPS)
- [ ] Mock과 실제 서버 응답 차이 점검 (`docs/api.md` 기준), 차이는 계약 문서부터 수정
- [ ] `data/remote/mock` 유지 여부 결정 (테스트·데모용으로 debug 전용 유지 vs 삭제)
- [ ] 검증: 로컬 Docker Compose 서버로 PRD 수용 기준 전체 시나리오

## M12. 클라우드 배포 (`feature/deploy`)

- [ ] VM 1대 준비(EC2/Lightsail/GCE 중 택1) + 도메인 연결
- [ ] 운영용 Docker Compose + Caddy(HTTPS 자동 발급)
- [ ] GitHub Actions: 서버 테스트 → 이미지 빌드 → VM 배포
- [ ] 비밀값(JWT 키, DB 비밀번호, Play Integrity 자격 증명) 관리
- [ ] 검증: 실제 기기로 운영 서버 접속, PRD 수용 기준 전체 시나리오 확인

---

## 공통 검증 & 품질 (모든 기능 브랜치)

- 앱
  - `./gradlew :app:compileDebugKotlin` (또는 `:app:assembleDebug`)
  - `./gradlew testDebugUnitTest`
  - background work / 권한 / 영속성 변경은 컴파일만으로 완료 처리하지 않고 수동 시나리오 결과 함께 기록
  - `llm-skills/code-simplification.md`로 자가 점검
  - API 변경 시 `docs/api.md` 먼저 갱신 → Mock 반영
- 서버 (Phase B)
  - `server/`에서 `./gradlew test` (Testcontainers로 PostgreSQL·Redis 통합 테스트)
- 배터리 사용량 측정 (Android Studio Energy Profiler)
- 16KB 페이지 호환성: `datastore-preferences` 네이티브 라이브러리 정렬 문제 확인·대응 (새 버전 확인 또는 대체), Compose graphics 라이브러리 포함 (`docs/architecture.md` 빌드 메모)

---

## 마일스톤

| 단계 | 목표 | 범위 |
|---|---|---|
| M0 ✅ | 요구사항과 설계가 합의된다 | PRD |
| **Phase A** | | |
| M1 | 앱 골격과 API 계약, Mock 서버가 준비된다 | 앱 |
| M2 | 앱이 걸음을 정확히 세고 저장한다 | 앱 |
| M3 | 걸음이 Mock 서버와 동기화된다 | 앱 + Mock |
| M4 | 화면에서 걸음과 기록을 본다 | 앱 |
| M5 | 리워드를 중복 없이 받는다 | 앱 + Mock |
| M6 | 동네를 걸어 보물상자를 연다 | 앱 + Mock + 네이버 지도 |
| M7 | 동네 랭킹으로 비교한다 | 앱 + Mock |
| **Phase B** | | |
| M8 | 서버가 인증과 걸음 업로드를 받는다 | 서버 |
| M9 | 리워드를 안전하게 지급한다 | 서버 + 앱 + Play Integrity |
| M10 | Redis로 랭킹을 집계한다 | 서버 + Redis |
| M11 | 앱이 실제 서버로 동작한다 | 앱 + 서버 |
| M12 | 운영 서버에서 전체가 동작한다 | 인프라 |

각 마일스톤 완료 시 `develop` → `main` 머지.
