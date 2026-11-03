# 2주차 개발 계획 — 시스템 아키텍처 설계

> 기준 가이드: `docs/study_guide/Android_Infra_Study_Week2_System_Architecture.pdf`
> 1주차까지의 결과물(PRD, TODO, architecture, api)은 `docs/week1/`에 보관했다.

## 이번 주 목표

"만보기 앱에 서버와 DB가 왜 필요하고, 데이터가 어떻게 이동하는지"를 **이 프로젝트의 실제 코드 기준으로** 설명할 수 있게 한다.
Phase A(앱 + Mock 서버)로 이미 구현된 부분이 많으므로, 새 기능보다는 **빠진 부분을 채우고 설명할 근거를 만드는 것**에 집중한다.

## 문서 운영 규칙

- `docs/week1/`은 1주차 결과물 보관용이다. 이번 주에는 수정하지 않는다.
- 1주차 문서 내용을 바꿔야 하면 해당 문서를 `docs/week2/`로 복사해서 고치고, 그때부터는 **가장 최근 주차 폴더의 문서가 기준**이 된다. 코드 주석에 있는 문서 경로도 함께 고친다.
  - 예: `GET /steps`를 추가하면 `docs/week1/api.md`를 `docs/week2/api.md`로 복사해 수정하고, 코드 주석의 `docs/week1/api.md`를 `docs/week2/api.md`로 바꾼다.
- 결정을 기록할 때는 선택하지 않은 대안과 그 이유도 함께 적는다.

## 2주차 최종 과제와 현재 상태

| 과제 | 현재 상태 (1주차 기준) | 이번 주 작업 |
|---|---|---|
| 전체 아키텍처 다이어그램 1개 | 앱 쪽 그림은 있음(PRD §3). 서버 내부 구성은 없음 | Task 3 |
| 구성 요소별 역할 설명 | 패키지 단위로만 있음(architecture §2) | Task 3: "왜 필요한지 / 없으면 어떤 문제가 생기는지" 형식으로 |
| 걸음 저장·조회 API 2개 이상 | 저장(`POST /steps/sync`)만 있고 **조회 API가 없음** | Task 1 |
| 정상 / 오프라인 / 리워드 시나리오 | 정상·리워드는 확인함. **오프라인 후 동기화는 기기에서 확인하지 못함** | Task 2 → Task 3 |
| 내 설계의 약점 2가지 | 정리된 곳 없음 | Task 3 |

## 결정 기록

### W2-Q1. Redis는 언제 도입할까? → **나중에: 필요하다는 근거를 확인한 뒤 도입** (1주차 PRD Q11 수정)

- 선택 이유: 가이드 원칙("서버 1대 + DB 1개로 시작하고, Redis·Kafka는 필요성이 확인되면 추가")을 따른다. M8은 Spring Boot + PostgreSQL만으로 시작한다. M10에서 먼저 PostgreSQL 쿼리로 랭킹을 만들고, 부하 테스트로 한계(응답 시간, DB 부하)를 측정한 뒤 Redis Sorted Set으로 바꾼다. 그러면 "Redis를 왜 썼는가, 없으면 어떤 문제가 생기는가"에 측정값으로 답할 수 있다.
- 선택하지 않은 대안
  - 처음부터 Redis 사용 (1주차 계획): 랭킹을 한 번만 구현하면 되지만, 도입 근거가 추측뿐이고 "없으면 어떤 문제가 생기는지" 보여 줄 수 없다. 운영할 구성 요소도 처음부터 하나 더 늘어난다.
  - Redis 사용 안 함: 가장 단순하지만 1주차의 목표였던 대규모 랭킹 설계 학습이 빠진다.
- 영향: M8 docker-compose에서 Redis 제외. M10은 "DB 랭킹 → 측정 → Redis 전환" 순서로 진행. Redis는 원천 데이터가 아니라 다시 만들 수 있는 파생 데이터라는 원칙(PRD Q11)은 그대로 둔다.

### W2-Q2. 걸음 조회 API의 범위는? → **계약 + Mock까지** (앱 복원은 3주차)

- 선택 이유: 2주차 과제는 "저장·조회 API 설계"이므로 계약과 Mock으로 충분하다. 앱에서 서버 기록으로 로컬을 복원하려면 로컬·서버 값을 합치는 규칙(어느 쪽이 우선인지, `synced` 플래그 처리)이 필요한데, 이는 3주차 동기화 알고리즘 주제다.
- 선택하지 않은 대안
  - 이번 주에 앱 복원까지 구현 (로컬 Room이 비어 있으면 서버에서 받아 오기): 재설치 시나리오까지 확인할 수 있지만, 병합 규칙을 3주차 설계 전에 임시로 정하게 된다.
- 세부 결정(일별만, 기록 있는 날만, 최대 31일)과 대안은 `docs/week2/api.md`의 `GET /steps` 절에 기록했다.

## 트래픽 대략 계산 (확장성 질문용)

가정: MAU 10만, DAU 3만(30%), 앱은 15분마다 동기화(`StepSyncWorker`)

| 항목 | 계산 | 값 |
|---|---|---|
| 사용자당 하루 동기화 | 24시간 × 4회 | 최대 96회 |
| 하루 동기화 요청 | 3만 × 96 | 약 288만 건 |
| 평균 RPS | 288만 ÷ 86,400초 | 약 33 |
| 피크 RPS (평균의 3~5배로 가정) | | 약 100~170 |

→ Spring Boot 1대 + PostgreSQL 1대로 감당할 수 있는 수준이다. 이 숫자를 기준으로 "서버 한 대로 부족해지는 시점"과 그때의 확장 단계를 Task 3에서 설명한다. 확장 단계: JWT로 서버를 무상태로 유지 → 서버 수평 확장 + 로드 밸런서 → DB 읽기 복제본 → 캐시(Redis).

---

## 작업 목록

브랜치: `docs/week2-plan`(이 문서와 폴더 정리) → `feature/week2-architecture`(Task 1~4)

### Task 1. 걸음 조회 API ✅

- [x] `docs/week1/api.md`를 `docs/week2/api.md`로 복사, 코드 주석 경로 갱신
- [x] `GET /steps?from={yyyy-MM-dd}&to={yyyy-MM-dd}` 계약 작성
  - 응답: 기록 있는 날짜의 `steps`만 오름차순 (`hourly`는 주지 않음)
  - 규칙: 양 끝 포함 최대 31일, `from > to`·형식 오류는 `400 VALIDATION_ERROR`, `to`가 미래여도 허용, KST 기준
  - 필요한 이유: 재설치나 기기 변경 때 기록이 사라지지 않게 하고, 서버가 인정한 값(상한 적용 후)을 앱이 확인할 수 있게 함
- [x] DTO(`StepsResponse`, `StoredDailyStepsDto`) → `PedometerApi.getSteps` → `MockServer.getSteps` / `MockApiInterceptor` 라우트
- [x] `MockApiTest`에 테스트 4개 추가: 기간 조회(오름차순·기간 밖 제외), 상한·max가 적용된 저장값, 다른 사용자 기록 격리, 잘못된 기간 400 (31일 경계 포함)
- [x] 검증: `./gradlew :app:testDebugUnitTest` (전체 95개 통과, `MockApiTest` 17개), `./gradlew :app:assembleDebug` 성공
- 앱 쪽 Repository·화면 연결은 하지 않음 (W2-Q2). `getSteps`는 아직 Mock 테스트에서만 호출한다

### Task 2. 오프라인 시나리오 확인 (시나리오 B) ✅

- 결과: `docs/week2/scenarios.md` (오프라인 중 요청 0건, 복구 1.5초 뒤 동기화, 토큰 만료 401 → 갱신 → 성공)
- 변경: Mock은 앱 내부라 비행기 모드는 WorkManager 네트워크 제약으로만 확인 가능. 요청 도중 끊김은 M11에서


- [x] 에뮬레이터(시뮬레이션 센서 `-PfakeStepSensor=true`)에서 진행
  1. 걸음이 쌓이고 동기화되는 것 확인
  2. 네트워크 끊기 (`adb shell svc wifi disable`, `adb shell svc data disable`)
  3. 걸음 추가 → `daily_steps.synced = false`로 남는지 확인
  4. 네트워크 복구 → `StepSyncWorker`가 재시도해 `synced = true`가 되는지 확인
  5. 같은 날짜를 다시 보내도 Mock 값이 그대로인지(`max` 멱등) 확인
- [x] 결과를 `docs/week2/scenarios.md`에 기록 (로그와 DB 상태 포함)
- [ ] 실패하거나 예상과 다르게 동작하면 `fix/*` 브랜치로 따로 수정

### Task 3. 2주차 설계 문서 `docs/week2/system-architecture.md` ★ 발표 자료

- [ ] **전체 아키텍처 다이어그램 1개**
  - Android: STEP_COUNTER → `StepCounterService` → `StepRepository` → Room / Compose UI, `StepSyncWorker`
  - → HTTPS(Caddy) → API Server(인증 / 걸음 처리 / 리워드 처리 / 보물상자 / 랭킹) → PostgreSQL
  - Phase A의 Mock 위치(`MockApiInterceptor`)도 표시
- [ ] **구성 요소별 역할**: 구성 요소마다 "역할 / 왜 필요한가 / 없으면 어떤 문제가 생기는가"
- [ ] **핵심 개념 5가지 질문에 이 프로젝트 기준으로 답하기**
  - Client-Server: 걸음 수를 왜 서버에 저장하나 → 역할 분리(PRD Q5), 리워드·랭킹·기기 변경
  - REST API: 걸음 수를 어떤 API로 보내나 → `POST /steps/sync` 배치 + 멱등 `max`
  - API Server: 1만 보 달성 여부는 어디서 판단하나 → 서버가 자기가 받은 값으로 판정, 앱 판정은 버튼 표시용
  - Database: 서버가 재시작되면 데이터는 → PostgreSQL이 원천, 캐시는 다시 만들 수 있음
  - 확장성: 서버 한 대가 부족하면 → 위 트래픽 계산과 확장 단계
- [ ] **걸음 저장·조회 API 2개**: `POST /steps/sync`, `GET /steps` 요약 (상세는 `docs/week2/api.md`)
- [ ] **시나리오 3가지** (실제 클래스 이름으로 순서 정리)
  - A. 100걸음을 걸었다: 센서 → 증분 계산 → Room → Flow → UI. 매번 서버를 부르지 않는 이유(화면 표시용 데이터와 서버 저장용 데이터 구분, 배터리, WorkManager 최소 15분)
  - B. 인터넷이 끊겼다가 복구됐다: `synced` 플래그 → WorkManager 네트워크 조건 + 지수 백오프 → 멱등 upsert (Task 2 결과 인용)
  - C. 1만 보를 달성했다: 수령 직전 동기화 → `POST /rewards/claim` → 서버 판정 → `UNIQUE(user, date, tier)`로 중복 지급 방지 → 지갑 갱신
- [ ] **내 설계의 약점 2가지** (후보 중 선택하고 보완 방법 적기)
  1. 최대 15분 동기화 지연으로 랭킹이 늦게 반영됨 (리워드는 수령 직전 동기화로 보완)
  2. 걸음 값은 결국 클라이언트가 보낸 값을 믿음. 상한과 Play Integrity로도 폰 흔들기는 못 막음
  3. VM 1대가 단일 장애 지점(SPOF)
  4. 재설치 시 기록이 사라짐 (Task 1로 해결했는지에 따라)
- [ ] **기술 선택표**: 기술마다 "왜 썼는가 / 없으면 어떤 문제가 생기는가" (Room, WorkManager, Foreground Service, Retrofit, PostgreSQL, Caddy, JWT, Play Integrity, Redis(보류))
- [ ] 가이드 참고 내용 반영: Health Connect / Recording API(Android 13 이하)를 걸음 소스로 쓰지 않은 이유 (1주차 PRD Q8 + Recording API 대안 추가)

### 버그 수정: 측정 공백 걸음이 한 시간에 몰림 (실기기 SM-S916N, 2026-11-03) ✅

- 증상·원인·수정은 `docs/week2/architecture.md` §3 디버깅 기록
- [x] 실기기 값 그대로 재현하는 실패 테스트 작성 → 공백 구간 시간 비율 분배로 수정 (`StepDeltaCalculatorTest` 11개, 전체 단위 테스트 98개 통과)
- [x] 계측 테스트 (에뮬레이터, 11개 통과)
- [x] 실기기 SM-S916N에 새 빌드 설치 (21:15) — 업데이트 브로드캐스트로 서비스 자동 재시작, 크래시 없음
- [ ] 실기기에서 며칠 사용하며 확인 (사용자)
- 커밋은 `fix/step-gap-spread` 브랜치로 분리 (버그 수정은 `fix/*` 규칙)
- Task 3 "약점"에 실제 사례로 사용: 클라이언트 측정 공백 → 서버에 잘못된 데이터

### Task 4. 1주차 계획 수정 사항 반영

- [ ] `docs/week1/TODO.md`를 `docs/week2/TODO.md`로 복사해 이후 기준으로 삼기
  - M8: docker-compose에서 Redis 제외, PostgreSQL 기반 랭킹 준비
  - M10: "DB 랭킹 구현 → 부하 테스트로 한계 측정 → Redis 전환 → 결과 비교 기록"으로 변경
  - Task 1의 `GET /steps`를 M8 서버 구현 범위에 추가
  - Phase A 완료 기준 중 오프라인 항목에 Task 2 결과 반영
- [ ] W2-Q1·W2-Q2 결정을 `docs/week2/TODO.md` 상단 또는 별도 결정 기록에서 참조

### Task 5. 마무리

- [ ] 셀프 점검 (`.agents/llm-skills/code-simplification.md`)
- [ ] `develop`에 `--no-ff` 머지
- [ ] Phase A 완료 기준(자정 경계 확인 여부 포함)을 정리하고 `develop` → `main` 머지 여부 결정

## 이번 주 범위가 아닌 것

| 주제 | 다룰 주차 | 대응 마일스톤 |
|---|---|---|
| DB 스키마 세부, 동기화 알고리즘 | 3주차 | M8 |
| 리워드 동시성, 중복 지급 방지 구현 | 4주차 | M9 |
| Redis 도입 | 필요성 측정 후 | M10 |

## 완료 기준

- [ ] 가이드 최종 과제 5개 항목이 `docs/week2/system-architecture.md`에 모두 있다
- [ ] 조회 API가 계약 문서와 Mock에 있고 테스트가 통과한다
- [ ] 오프라인 시나리오를 직접 확인한 기록이 있다
- [ ] 6분 안에 "서버와 DB가 왜 필요하고 데이터가 어떻게 이동하는지" 발표할 수 있다
