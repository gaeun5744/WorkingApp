# 만보기 앱 클론 — 구현 TODO

> 당근 만보기를 참고해 **앱 시스템 디자인**을 연습하기 위한 프로젝트.
> 화면을 똑같이 만드는 것보다 "걸음 데이터가 센서에서 서버까지 어떻게 정확하고 안전하게 흘러가는가"를 설계하는 데 집중한다.

- 브랜치 전략: `main`(릴리스 가능한 상태) ← `develop`(통합) ← `feature/*`(기능 단위)
- 기술 스택: Kotlin, Jetpack Compose, minSdk 30 / targetSdk 37 (현재 프로젝트 설정 기준)

---

## 0. 요구사항 정리

### 기능 요구사항 (원본 앱 기준 추정, 실제 앱 보며 보정할 것)
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

## 1. 아키텍처 뼈대 (`feature/architecture`)

- [ ] 레이어 구조 결정: `ui` → `domain` → `data` (Clean Architecture 라이트 버전)
- [ ] 모듈화 여부 결정: 처음엔 단일 모듈 + 패키지 분리, 이후 `:core:data`, `:feature:home` 등으로 분리 연습
- [ ] DI: Hilt 도입
- [ ] 비동기: Coroutines + Flow
- [ ] 상태 관리: ViewModel + `StateFlow<UiState>` (UDF 패턴)
- [ ] Navigation Compose로 화면 구성 (홈 / 기록 / 랭킹 / 설정)
- [ ] `docs/architecture.md`에 다이어그램과 의사결정 기록(ADR) 남기기

```
[Sensor] → [StepCounterService] → [StepRepository] → [Room DB]
                                          ↓                ↑
                                   [SyncWorker] ──→ [Remote API(mock)]
                                          ↓
                                   [ViewModel] → [Compose UI]
```

---

## 2. 걸음 수 측정 (`feature/step-sensor`) ★ 가장 중요

### 2-1. 데이터 소스 선택
- [ ] `Sensor.TYPE_STEP_COUNTER` vs `TYPE_STEP_DETECTOR` vs Health Connect 비교 정리
  - STEP_COUNTER: 부팅 이후 누적값, 저전력(하드웨어 배칭), 재부팅 시 0으로 리셋
  - STEP_DETECTOR: 걸음마다 이벤트, 실시간성 좋지만 배터리 소모
  - Health Connect: 타 앱/워치 데이터 통합 가능, 권한·가용성 이슈
- [ ] 1차 구현은 STEP_COUNTER, 이후 Health Connect를 추가 소스로 붙이는 구조로 설계 (`StepDataSource` 인터페이스)

### 2-2. 누적값 → 일별 걸음 수 변환 로직
- [ ] 센서 누적값과 "오늘 0시 기준값(baseline)"의 차이로 오늘 걸음 계산
- [ ] **재부팅 감지**: 현재 누적값 < 마지막 저장값이면 재부팅으로 판단 → baseline 보정
- [ ] **자정 경계**: 날짜가 바뀌면 이전 날짜 기록 확정 후 새 baseline 저장
- [ ] 시간대/기기 시간 변경 (`ACTION_TIMEZONE_CHANGED`, `ACTION_TIME_CHANGED`) 처리
- [ ] 위 로직은 Android 의존성 없는 순수 Kotlin 클래스로 분리 → **단위 테스트로 엣지 케이스 검증**

### 2-3. 백그라운드 측정
- [ ] Foreground Service(`foregroundServiceType="health"`) + 상시 알림에 오늘 걸음 표시
- [ ] `BOOT_COMPLETED` 수신 시 서비스 재시작
- [ ] 서비스가 죽었을 때를 대비한 WorkManager 주기 작업(15분)으로 센서값 스냅샷 저장
- [ ] 센서 배칭(`maxReportLatencyUs`) 활용해 배터리 절약
- [ ] 권한: `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_HEALTH` 런타임 요청 플로우

---

## 3. 로컬 저장소 (`feature/local-db`)

- [ ] Room 스키마 설계
  - `daily_steps(date PK, steps, goal, updated_at, synced)`
  - `hourly_steps(date, hour, steps)` — 그래프용
  - `sensor_snapshot(timestamp, raw_value, boot_count)` — 재계산/디버깅용
  - `reward_claim(id, date, tier, claimed_at, status)`
- [ ] DataStore: 목표 걸음 수, 마지막 baseline, 설정값
- [ ] 오래된 스냅샷 정리 정책 (예: 7일 이후 삭제)
- [ ] Room 마이그레이션 테스트

---

## 4. 홈 화면 (`feature/home`)

- [ ] 오늘 걸음 수 + 원형 진행 바 (목표 대비)
- [ ] 거리(km) / 칼로리 / 걸은 시간 환산 표시
- [ ] 리워드 구간 버튼 (예: 1천/3천/5천/7천/1만 보) — 상태: 잠김 / 수령 가능 / 수령 완료
- [ ] Room Flow 구독으로 걸음 수 실시간 갱신
- [ ] 권한 미허용 / 센서 미지원 기기 상태 UI

---

## 5. 기록 화면 (`feature/history`)

- [ ] 일/주/월 탭 + 막대 그래프 (Compose Canvas로 직접 그려보기)
- [ ] 목표 달성 일수, 평균 걸음 수 통계
- [ ] 페이징 (월 단위 이동)

---

## 6. 리워드 시스템 (`feature/reward`) ★ 시스템 디자인 포인트

- [ ] 리워드 수령 플로우 설계
  - 클라이언트: 수령 요청 → 낙관적 UI 업데이트 → 실패 시 롤백
  - **멱등성 키**(`date + tier + userId`)로 중복 수령 방지
- [ ] 서버 검증 시나리오 정리 (mock 서버로 구현)
  - 하루 최대 걸음 수 상한, 시간당 비정상 증가량 탐지
  - 서버 시간 기준 날짜 판정 (기기 시간 신뢰하지 않음)
- [ ] 어뷰징 방지 아이디어 문서화: Play Integrity API, 가속도 패턴 분석 등 (구현은 선택)

---

## 7. 서버 동기화 (`feature/sync`)

- [ ] 실제 서버 대신 Ktor mock server 또는 Retrofit + MockWebServer/가짜 Repository
- [ ] API 설계 문서 작성 (`docs/api.md`)
  - `POST /steps/sync` — 일별 걸음 배치 업로드
  - `POST /rewards/claim` — 리워드 수령
  - `GET /ranking?region=` — 동네 랭킹
- [ ] WorkManager `SyncWorker`: 네트워크 연결 조건, 지수 백오프 재시도
- [ ] `synced` 플래그 기반 증분 동기화, 충돌 시 정책(서버 값 vs 큰 값 우선) 결정

---

## 8. 랭킹 / 동네 (`feature/ranking`)

- [ ] 동네 설정 (위치 기반 대신 목록에서 선택하는 간단한 방식)
- [ ] 동네 랭킹 리스트 + 내 순위 고정 표시
- [ ] 캐싱 전략: 랭킹은 실시간일 필요 없음 → 일정 주기 갱신 + 로컬 캐시

---

## 9. 알림 (`feature/notification`)

- [ ] 리워드 구간 도달 시 알림 (중복 발송 방지)
- [ ] 목표 달성 알림
- [ ] 알림 채널 분리 (상시 측정 / 리워드 / 마케팅)

---

## 10. 테스트 & 품질

- [ ] 걸음 계산 로직 단위 테스트 (재부팅, 자정, 시간 변경, 센서 값 튐)
- [ ] Repository 테스트 (Room in-memory DB)
- [ ] ViewModel 테스트 (Turbine)
- [ ] 가짜 센서(`FakeStepDataSource`)로 에뮬레이터에서 걸음 시뮬레이션
- [ ] 배터리 사용량 측정 (Battery Historian / Android Studio Energy Profiler)

---

## 진행 순서 (마일스톤)

| 단계 | 목표 | 포함 항목 |
|---|---|---|
| M1 | 걸음이 정확히 세진다 | 1, 2, 3 |
| M2 | 화면에서 확인할 수 있다 | 4, 5 |
| M3 | 리워드를 받을 수 있다 | 6, 7 |
| M4 | 이웃과 비교한다 | 8, 9 |
| 상시 | 테스트 & 문서화 | 10, `docs/` |

각 마일스톤 완료 시 `develop` → `main` 머지.

---

## 열린 질문 (설계하며 결정할 것)
- [ ] 원본 앱의 리워드 구간·지급 방식 실제 확인
- [ ] Health Connect를 주 데이터 소스로 쓸지, 보조로 쓸지
- [ ] 서버 값과 로컬 값이 다를 때 무엇을 진실의 원천(source of truth)으로 볼지
- [ ] 멀티 모듈 분리 시점
