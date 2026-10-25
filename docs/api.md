# API 계약

> 앱(`data/remote`), Mock 서버(`data/remote/mock`, Phase A), 실제 서버(`server/`, Phase B)가 공통으로 따르는 계약.
> **API를 바꿀 때는 이 문서를 먼저 수정**하고 Mock → 앱 → 서버 순으로 반영한다.

## 공통 규칙

| 항목 | 규칙 |
|---|---|
| 형식 | JSON (`Content-Type: application/json`), 필드명 camelCase |
| 인증 | `Authorization: Bearer {accessToken}` — `/auth/*` 외 모든 엔드포인트 필수 |
| 날짜 | `date`는 KST 기준 `yyyy-MM-dd` |
| 일시 | ISO-8601 오프셋 포함 (`2026-10-25T09:30:00+09:00`) |
| 시간대 | 서버는 자기 시계(Asia/Seoul)로 "오늘"을 판정한다 (PRD Q6) |

### 에러 응답

```json
{ "code": "TOKEN_EXPIRED", "message": "액세스 토큰이 만료되었습니다." }
```

`message`는 디버깅용이다. 앱은 `code`로 분기하고 사용자 문구는 앱 리소스로 만든다.

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 요청 형식·값 오류 |
| 400 | `REWARD_NOT_REACHED` | 리워드 구간 걸음 수 미달 |
| 400 | `REWARD_NOT_TODAY` | 오늘(KST)이 아닌 날짜의 리워드 요청 |
| 401 | `UNAUTHORIZED` | 토큰 없음 / 알 수 없는 토큰 |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료 → `/auth/refresh` 후 재시도 |
| 401 | `INVALID_REFRESH_TOKEN` | 리프레시 토큰 무효·만료 → 익명 재가입 |
| 404 | `NOT_FOUND` | 리소스 없음 (예: 존재하지 않는 동네) |
| 409 | `REGION_NOT_SET` | 동네 미설정 상태에서 랭킹 요청 |
| 500 | `INTERNAL_ERROR` | 서버 오류 (Mock 오류 주입 포함) |

---

## 인증

### `POST /auth/anonymous` — 익명 가입

요청
```json
{ "nickname": "걷는토끼" }
```
- `nickname`: 공백 제외 1~12자

응답 `201`
```json
{
  "userId": "u_8f3a...",
  "accessToken": "...",
  "refreshToken": "...",
  "accessTokenExpiresInSeconds": 3600
}
```

### `POST /auth/refresh` — 토큰 재발급 (rotation)

요청
```json
{ "refreshToken": "..." }
```
응답 `200`: `/auth/anonymous`와 같은 형식. **사용한 리프레시 토큰은 즉시 무효화**되고 새 토큰이 발급된다.
에러: `401 INVALID_REFRESH_TOKEN`

---

## 사용자 / 동네

### `GET /me`

응답 `200`
```json
{
  "userId": "u_8f3a...",
  "nickname": "걷는토끼",
  "region": { "id": 1, "name": "역삼동" }
}
```
- `region`: 미설정이면 `null`

### `GET /regions?query={검색어}`

응답 `200`
```json
{ "regions": [ { "id": 1, "name": "역삼동" } ] }
```
- `query`가 비어 있으면 전체 목록

### `PUT /me/region`

요청
```json
{ "regionId": 1 }
```
응답 `200`: `GET /me`와 같은 형식. 에러: `404 NOT_FOUND`

---

## 걸음

### `POST /steps/sync` — 날짜별 걸음 배치 업로드

요청
```json
{
  "days": [
    {
      "date": "2026-10-25",
      "steps": 5230,
      "hourly": [ { "hour": 8, "steps": 2100 }, { "hour": 9, "steps": 3130 } ]
    }
  ]
}
```
- `steps`: 그날 누적 걸음 (0 이상)
- `hourly`: 시간대별 걸음 (누적 아님, `hour` 0~23). 합계는 `steps`와 같아야 한다.

응답 `200`
```json
{
  "results": [
    { "date": "2026-10-25", "status": "ACCEPTED", "acceptedSteps": 5230 }
  ]
}
```

서버 처리 규칙 (PRD Q5·Q9, §6)

1. **날짜 검사**: 미래 날짜, 최근 7일(오늘 포함) 밖의 날짜는 `REJECTED`
2. **시간대 검사**: 오늘 날짜의 미래 시간대(`hour` > 현재 시)는 0으로 취급
3. **상한**: 시간당 12,000 / 하루 100,000으로 자른다. 자른 경우 `CAPPED` + 의심 플래그 기록
4. **반영**: `저장값 = max(기존 값, acceptedSteps)` → 같은 요청을 반복해도 결과가 같다 (멱등)

| status | 의미 |
|---|---|
| `ACCEPTED` | 그대로 반영 |
| `CAPPED` | 상한·미래 시간대로 잘라서 반영 |
| `REJECTED` | 반영하지 않음 (`acceptedSteps` = 0) |

`acceptedSteps`는 이번 요청에서 인정된 값이고, 저장값은 기존 값과의 max이다.

---

## 리워드

구간 (PRD Q7, §6)

| tier | requiredSteps | points |
|---|---|---|
| 1 | 1,000 | 1 |
| 2 | 3,000 | 3 |
| 3 | 5,000 | 5 |
| 4 | 7,000 | 7 |
| 5 | 10,000 | 10 |

### `GET /rewards/today`

응답 `200`
```json
{
  "date": "2026-10-25",
  "steps": 5230,
  "tiers": [
    { "tier": 1, "requiredSteps": 1000, "points": 1, "status": "CLAIMED" },
    { "tier": 3, "requiredSteps": 5000, "points": 5, "status": "CLAIMABLE" },
    { "tier": 4, "requiredSteps": 7000, "points": 7, "status": "LOCKED" }
  ]
}
```
- `steps`: **서버에 저장된** 오늘 걸음 (리워드 판정 기준)
- `status`: `LOCKED` | `CLAIMABLE` | `CLAIMED`

### `POST /rewards/claim`

요청
```json
{ "date": "2026-10-25", "tier": 3, "integrityToken": null }
```
- `integrityToken`: Phase A에서는 선택(무시됨), Phase B(M8)에서 필수

응답 `200`
```json
{ "date": "2026-10-25", "tier": 3, "points": 5, "balance": 9, "alreadyClaimed": false }
```
- **멱등**: 이미 받은 구간을 다시 요청하면 포인트를 더 주지 않고 `alreadyClaimed: true`와 현재 잔액을 돌려준다.

에러: `400 REWARD_NOT_REACHED`, `400 REWARD_NOT_TODAY`, `400 VALIDATION_ERROR`(없는 tier)

### `GET /wallet`

응답 `200`
```json
{
  "balance": 9,
  "transactions": [
    { "id": "t_12", "amount": 5, "reason": "REWARD_TIER_3", "createdAt": "2026-10-25T09:30:00+09:00" }
  ]
}
```
- `transactions`: 최신순

---

## 랭킹

### `GET /ranking?period={daily|weekly}`

응답 `200`
```json
{
  "period": "daily",
  "periodStart": "2026-10-25",
  "region": { "id": 1, "name": "역삼동" },
  "entries": [
    { "rank": 1, "userId": "u_1", "nickname": "산책왕", "steps": 15200 }
  ],
  "me": { "rank": 12, "userId": "u_8f3a...", "nickname": "걷는토끼", "steps": 5230 }
}
```
- `periodStart`: daily = 오늘, weekly = 이번 주 월요일 (KST)
- `entries`: 상위 50명. 걸음이 같으면 먼저 도달한 사용자가 위 (Mock은 userId 순)
- `me`: 기록이 없으면 `steps` 0으로 포함

에러: `409 REGION_NOT_SET`, `400 VALIDATION_ERROR`(잘못된 period)
