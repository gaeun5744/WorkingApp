# 2주차 시나리오 확인 기록

## 시나리오 B. 인터넷이 끊겼다가 복구됐다 (2026-11-03)

환경: 에뮬레이터 Pixel 8 API 37, 시뮬레이션 센서(`-PfakeStepSensor=true`, 1초에 1걸음), Mock 서버(앱 내부 `MockApiInterceptor`)

### 전제: Mock 서버에서 "오프라인"이 의미하는 것

Mock 서버는 앱 안의 OkHttp Interceptor라 실제 네트워크를 쓰지 않는다. 그래서 비행기 모드에서도 HTTP 요청 자체는 실패하지 않는다.
오프라인의 효과는 **WorkManager의 `NetworkType.CONNECTED` 제약**으로만 나타난다. 이번 확인은 이 제약이 동기화를 막고, 복구 시 바로 풀어 주는지를 본다.
요청 도중 연결이 끊기는 경우(`IOException` → `AppError.Network` → `Result.retry()` + 지수 백오프)는 Mock으로 재현할 수 없어 실제 서버 전환(M11)에서 확인한다.

### 진행과 결과

| 시각 | 동작 | 관찰 (근거) |
|---|---|---|
| 20:56 | 설치 → 온보딩(닉네임, 역삼동) | `POST /auth/anonymous` 201, `PUT /me/region` 200 |
| 20:57 | 온보딩 완료 시 즉시 동기화 | Worker SUCCESS, 요청 없음 — 걸음 서비스가 홈 화면에서 시작돼 아직 기록이 없었음 |
| 20:58 | 주기 작업 강제 실행 시도 (`cmd jobscheduler run`) | WorkManager가 "before schedule"로 거부 → 주기 작업은 예정 시각 전에는 실행되지 않음 |
| 20:58:51 | **비행기 모드 켬** | 동기화 작업 제약: `Unsatisfied: TIMING_DELAY CONNECTIVITY` |
| 21:13:00 | 예정 시각(약 21:11:45) 지남 | `Unsatisfied: CONNECTIVITY`만 남음, `earliest=-1m14s`. HTTP 요청 0건. Room `daily_steps` 975보 `synced=0` |
| 21:13:12 | **비행기 모드 끔** | |
| 21:13:13 | 약 1.5초 뒤 작업 시작 | `POST /steps/sync` → **401** (오프라인 동안 액세스 토큰 만료) → `POST /auth/refresh` 200 → `POST /steps/sync` **200**, Worker SUCCESS |
| 21:13:14 | | `synced=0` 유지 — 업로드 중 새 걸음이 기록돼(`updated_at` 21:13:14) 의도대로 다음 동기화로 넘김 |

### 결론

- 오프라인 동안 걸음은 Room에 계속 쌓이고, 동기화는 시도조차 하지 않는다 (배터리·요청 낭비 없음)
- 네트워크가 돌아오면 예정 시각이 지난 작업이 즉시 실행된다
- 오프라인이 길어 토큰이 만료돼도 401 → 갱신 → 재시도가 한 번의 작업 안에서 처리된다
- 업로드 중 바뀐 날짜는 미동기화로 남아 다음에 다시 보낸다. 같은 날짜를 다시 보내도 서버는 `max(기존, 새 값)`이라 결과가 같다 (멱등, `MockApiTest`로 검증)

### 확인하지 못한 것

- 요청 도중 연결 끊김과 지수 백오프 재시도 (Mock 한계, M11)
- 시뮬레이션 센서는 5초마다 걸음이 늘어 `synced=1`이 되는 순간을 기기에서 보지 못함. 걸음이 멈춘 상태의 표시 갱신은 계측 테스트(`StepRepositoryImplTest`)로 검증
