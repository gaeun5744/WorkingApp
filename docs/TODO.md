# 만보기 앱 클론 — 구현 TODO

> 당근 만보기를 참고해 **앱 시스템 디자인**을 연습하기 위한 프로젝트.
> 화면을 똑같이 만드는 것보다 "걸음 데이터가 센서에서 서버까지 어떻게 정확하고 안전하게 흘러가는가"를 설계하는 데 집중한다.
> 모든 작업은 `.agents/AGENTS.md` 지침과 `llm-rules/`, `llm-skills/` 문서를 따른다.

- 브랜치 전략: `main`(릴리스 가능한 상태) ← `develop`(통합) ← `feature/*`(기능 단위)
- 커밋 메시지 설명은 한국어로 작성하고, 커밋/푸시는 명시적으로 요청할 때만 한다.
- 기술 스택 (AGENTS.md §1 기준)
  - 단일 모듈 `:app`, Kotlin, Jetpack Compose(Material3), minSdk 30 / targetSdk 37
  - DI: Hilt / 네트워크: Retrofit + OkHttp + kotlinx.serialization
  - 네비게이션: Navigation 3 (`NavDisplay` + `NavKey`), 화면 간 결과 전달은 `ResultEventBus`
  - 로깅: Timber (`Log.d/e` 금지)
  - 지침 외 추가 스택(만보기 특성상 필요): Room, DataStore, WorkManager, Foreground Service

---

## 0. Interview & PRD (`llm-skills/feature-development.md` 1~2단계, `llm-skills/grilling.md`)

구현 전에 사용자와 **한 번에 한 질문씩, 추천안과 함께** 정렬한다. 결과는 `docs/PRD.md`의 `PRD Interview` 섹션에 기록한다.

- [ ] 범위 / 비범위(non-goals) 확정
- [ ] 수용 기준(acceptance criteria) 정의
- [ ] 엣지 케이스 목록 확정 (재부팅, 자정, 시간 변경, 권한 거부, 센서 미지원)
- [ ] 의존성 / 제약 조건 정리
- [ ] 되돌리기 어려운 결정 grilling
  - [ ] 걸음 데이터 소스: STEP_COUNTER 단독 vs Health Connect 병행
  - [ ] Room 스키마
  - [ ] 서버 API 계약 (요청/응답 형태)
  - [ ] 로컬 vs 서버 중 진실의 원천(source of truth)
  - [ ] 원본 앱의 리워드 구간·지급 방식 확인

### 기능 요구사항 (원본 앱 기준 추정, 인터뷰에서 보정)
- [ ] 오늘 걸음 수 실시간 표시
- [ ] 일일 목표 걸음 수 설정 및 달성률 표시
- [ ] 일/주/월 걸음 기록 조회 (그래프)
- [ ] 걸음 수에 따른 리워드(포인트) 적립 — 구간별 버튼을 눌러 받는 방식
- [ ] 동네 단위 랭킹 / 이웃과 비교
- [ ] 목표 달성·리워드 수령 가능 알림

### 비기능 요구사항 (이 프로젝트의 핵심 학습 포인트)
- [ ] **정확성**: 앱이 꺼져 있거나 재부팅되어도 걸음 수가 유실되지 않을 것
- [ ] **배터리**: 상시 측정하되 배터리 소모 최소화
- [ ] **오프라인 우선**: 네트워크 없이도 동작, 연결 시 동기화
- [ ] **무결성**: 리워드 어뷰징(폰 흔들기, 값 조작, 중복 수령) 방어
- [ ] **날짜 경계**: 자정 리셋, 시간대 변경, 기기 시간 조작 처리

---

## 1. 프로젝트 기반 설정 (`feature/setup`)

- [ ] 의존성 추가 (`gradle/libs.versions.toml`)
  - Hilt + `androidx.hilt:hilt-lifecycle-viewmodel-compose`
  - Navigation 3, Retrofit, OkHttp, kotlinx.serialization
  - Room, DataStore, WorkManager
  - Timber, `kotlinx-collections-immutable`, `lifecycle-runtime-compose`
  - 테스트: Turbine, MockWebServer
- [ ] `@HiltAndroidApp` Application(Timber 초기화 포함), `MainActivity`에 `@AndroidEntryPoint`
- [ ] `presentation/nav/AppNavHost.kt`에 `NavDisplay` + `rememberResultEventBusNavEntryDecorator()` 설정
- [ ] `presentation/nav/NavKeys.kt`에 화면 키 정의 (홈 / 기록 / 랭킹 / 설정)
- [ ] 공통 `UiText`(i18n-safe 메시지 래퍼) 정의
- [ ] 검증: `./gradlew :app:assembleDebug`

### 패키지 구조 (`llm-rules/data/layer-contract.md` + 로컬 저장소 확장)

의존 방향: `presentation → domain ← data`. **UseCase 레이어 없이 ViewModel이 Repository를 직접 호출한다.**

```
data/remote/api      Retrofit API 인터페이스
data/remote/dto      @Serializable DTO + toDomain() 매퍼
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

```
[Sensor] → [StepCounterService] → [StepRepository] → [Room DB]
                                          ↓                ↑
                                   [SyncWorker] ──→ [Remote API(mock)]
                                          ↓
                                   [ViewModel] → [Compose UI]
```

- [ ] `docs/architecture.md`에 다이어그램과 지침 확장 근거 기록

---

## 2. 걸음 수 측정 (`feature/step-sensor`) ★ 가장 중요

### 2-1. 데이터 소스
- [ ] `TYPE_STEP_COUNTER` vs `TYPE_STEP_DETECTOR` vs Health Connect 비교를 `docs/architecture.md`에 정리
  - STEP_COUNTER: 부팅 이후 누적값, 저전력(하드웨어 배칭), 재부팅 시 0으로 리셋
  - STEP_DETECTOR: 걸음마다 이벤트, 실시간성 좋지만 배터리 소모
  - Health Connect: 타 앱/워치 데이터 통합 가능, 권한·가용성 이슈
- [ ] 1차 구현은 STEP_COUNTER (`data/sensor/StepDataSource` 인터페이스 + 구현체 분리 — 테스트용 Fake 교체 경계)

### 2-2. 누적값 → 일별 걸음 수 변환 (`domain/step`)
- [ ] 센서 누적값과 "오늘 0시 기준값(baseline)"의 차이로 오늘 걸음 계산
- [ ] **재부팅 감지**: 현재 누적값 < 마지막 저장값이면 재부팅으로 판단 → baseline 보정
- [ ] **자정 경계**: 날짜가 바뀌면 이전 날짜 기록 확정 후 새 baseline 저장
- [ ] 시간대/기기 시간 변경 (`ACTION_TIMEZONE_CHANGED`, `ACTION_TIME_CHANGED`) 처리
- [ ] 현재 시간은 `Clock` 주입으로 받아 테스트에서 제어
- [ ] **단위 테스트로 엣지 케이스 먼저 검증** (`./gradlew testDebugUnitTest`)

### 2-3. 백그라운드 측정 (`service/`, `worker/`)
- [ ] Foreground Service(`foregroundServiceType="health"`) + 상시 알림에 오늘 걸음 표시
- [ ] `BOOT_COMPLETED` 수신 시 서비스 재시작
- [ ] 서비스가 죽었을 때를 대비한 WorkManager 주기 작업(15분)으로 센서값 스냅샷 저장
- [ ] 센서 배칭(`maxReportLatencyUs`) 활용해 배터리 절약
- [ ] 권한: `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_HEALTH`
  - 권한 요청은 UiState에 섞지 않고 별도 effect로 분리 (`llm-skills/android-state-and-side-effects.md`)
- [ ] 검증: 실제 기기에서 재부팅 / 앱 강제 종료 후 걸음 유지 시나리오 수동 확인

---

## 3. 로컬 저장소 (`feature/local-db`)

- [ ] Room 스키마 (0단계 grilling에서 확정한 안 반영)
  - `daily_steps(date PK, steps, goal, updated_at, synced)`
  - `hourly_steps(date, hour, steps)` — 그래프용
  - `sensor_snapshot(timestamp, raw_value, boot_count)` — 재계산/디버깅용
  - `reward_claim(id, date, tier, claimed_at, status)`
- [ ] Entity → Domain은 `toDomain()` 매퍼 경유 (Entity를 presentation에 유출 금지)
- [ ] `data/di/DatabaseModule`: Database/DAO `@Provides` (`@Singleton`)
- [ ] DataStore: 목표 걸음 수, 마지막 baseline, 설정값
- [ ] 오래된 스냅샷 정리 정책 (예: 7일 이후 삭제)
- [ ] 테스트: Room in-memory DB로 DAO / Repository 검증

---

## 4. 홈 화면 (`feature/home`)

`llm-rules/ui/compose-rules.md` + `llm-skills/compose-ui-development.md` 절차를 따른다.

- [ ] `HomeUiState`(sealed interface) + `HomeViewModel`(`@HiltViewModel`, Repository 직접 주입)
- [ ] 오늘 걸음 Flow + 목표 + 리워드 상태를 `combine` → `stateIn`으로 노출
- [ ] 오늘 걸음 수 + 원형 진행 바 (목표 대비)
- [ ] 거리(km) / 칼로리 / 걸은 시간 환산 표시
- [ ] 리워드 구간 버튼 (예: 1천/3천/5천/7천/1만 보) — 상태: 잠김 / 수령 가능 / 수령 완료
- [ ] 권한 미허용 / 센서 미지원 상태 UI
- [ ] Compose에서는 `collectAsStateWithLifecycle()`로 수집
- [ ] `@Preview`로 상태별 화면 확인

---

## 5. 기록 화면 (`feature/history`)

- [ ] 일/주/월 탭 + 막대 그래프 (Compose Canvas로 직접 그려보기)
- [ ] 기록 목록 상태는 `ImmutableList`로 노출
- [ ] 목표 달성 일수, 평균 걸음 수 통계
- [ ] 월 단위 이동

---

## 6. 리워드 시스템 (`feature/reward`) ★ 시스템 디자인 포인트

- [ ] API 구현 순서 준수: DTO → Mapper → Domain → Repository 인터페이스 → 구현체 → DI → ViewModel → Screen
- [ ] 리워드 수령 플로우
  - 클라이언트: 수령 요청 → 낙관적 UI 업데이트 → 실패 시 롤백
  - **멱등성 키**(`date + tier + userId`)로 중복 수령 방지
- [ ] 오류 처리: Repository에서 예외를 `Result`로 변환, UI에는 `UiText` 메시지로 전달 (`llm-skills/android-error-handling.md`)
- [ ] 서버 검증 시나리오 (mock으로 구현)
  - 하루 최대 걸음 수 상한, 시간당 비정상 증가량 탐지
  - 서버 시간 기준 날짜 판정 (기기 시간 신뢰하지 않음)
- [ ] 어뷰징 방지 아이디어 문서화: Play Integrity API, 가속도 패턴 분석 등 (구현은 선택)

---

## 7. 서버 동기화 (`feature/sync`)

- [ ] 실제 서버 대신 Retrofit + MockWebServer (테스트) / 가짜 응답 Interceptor (앱 실행)
- [ ] API 계약 문서 `docs/api.md`
  - `POST /steps/sync` — 일별 걸음 배치 업로드
  - `POST /rewards/claim` — 리워드 수령
  - `GET /ranking?region=` — 동네 랭킹
- [ ] `data/di/NetworkModule`: `Json`, `OkHttpClient`, `Retrofit`, Api `@Provides` (`@Singleton`)
- [ ] `worker/SyncWorker`(Hilt Worker): 네트워크 연결 조건, 지수 백오프 재시도
- [ ] `synced` 플래그 기반 증분 동기화, 충돌 정책은 0단계 결정 따름
- [ ] 성공 응답이어도 빈 목록/에러 바디 구분

---

## 8. 랭킹 / 동네 (`feature/ranking`)

- [ ] 동네 설정 (위치 기반 대신 목록에서 선택하는 간단한 방식)
- [ ] 동네 선택 화면 → 설정 화면으로 결과 반환은 `ResultEventBus` 사용
- [ ] 동네 랭킹 리스트 + 내 순위 고정 표시
- [ ] 캐싱: 랭킹은 실시간일 필요 없음 → 일정 주기 갱신 + 로컬 캐시

---

## 9. 알림 (`feature/notification`)

- [ ] 리워드 구간 도달 시 알림 (중복 발송 방지)
- [ ] 목표 달성 알림
- [ ] 알림 채널 분리 (상시 측정 / 리워드 / 마케팅)

---

## 10. 검증 & 품질 (각 기능 브랜치 공통)

- [ ] 걸음 계산 로직 단위 테스트 (재부팅, 자정, 시간 변경, 센서 값 튐)
- [ ] Repository 테스트 (Room in-memory DB, MockWebServer)
- [ ] ViewModel 테스트 (Turbine, Dispatcher 주입)
- [ ] `FakeStepDataSource`로 에뮬레이터에서 걸음 시뮬레이션
- [ ] 배터리 사용량 측정 (Android Studio Energy Profiler)
- [ ] 매 기능 완료 시
  - `./gradlew :app:compileDebugKotlin` (또는 `:app:assembleDebug`)
  - `./gradlew testDebugUnitTest`
  - background work / 권한 / 영속성 변경은 컴파일만으로 완료 처리하지 않고 수동 시나리오 결과 함께 기록
  - `llm-skills/code-simplification.md`로 자가 점검 (`Manager`/`Helper` 이름 금지, 불필요한 추상화 제거, `internal`/`private` 가시성)

---

## 진행 순서 (마일스톤)

| 단계 | 목표 | 포함 항목 |
|---|---|---|
| M0 | 요구사항과 설계가 합의된다 | 0 |
| M1 | 걸음이 정확히 세진다 | 1, 2, 3 |
| M2 | 화면에서 확인할 수 있다 | 4, 5 |
| M3 | 리워드를 받을 수 있다 | 6, 7 |
| M4 | 이웃과 비교한다 | 8, 9 |
| 상시 | 검증 & 문서화 | 10, `docs/` |

각 마일스톤 완료 시 `develop` → `main` 머지.
