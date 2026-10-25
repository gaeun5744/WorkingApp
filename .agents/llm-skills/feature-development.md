---
type: skill
name: feature-development
description: "신규 기능 구현 시 5단계 인지 사고 흐름 (인터뷰→계획→구현)."
---

# 신규 기능 개발 워크플로우 (Feature Development Workflow)

이 문서는 AI 에이전트가 새로운 기능을 구현할 때 반드시 따라야 하는 인지적 사고 흐름입니다.
에이전트는 즉시 코딩을 시작하지 말고 아래 5단계를 거쳐야 합니다.
대표 기능 흐름 예시는 딥링크/Intent 수신 → 원격 API 호출 → 아이템 목록 UI입니다.

## 1. Interview

- 사용자를 **한 번에 한 질문씩** 인터뷰합니다.
- 의사결정 트리의 모든 주요 분기를 따라가며 확인합니다.
- 각 질문에는 에이전트의 **권장 답변**을 함께 제시합니다.
- 코드베이스로 답할 수 있는 질문은 사용자에게 묻기 전에 먼저 탐색합니다.
- 다음 항목에 대해 공유된 이해에 도달할 때까지 계속합니다:
  - scope
  - acceptance criteria
  - edge cases
  - non-goals
  - dependencies
  - constraints
- 아주 작은 작업은 단일 확인 질문으로 축약할 수 있지만, 기능 작업은 충분히 확장해 질문합니다.
- 인터뷰 결과는 PRD에 `PRD Interview` 섹션으로 기록합니다.

## 2. Plan (계획 수립 및 브레인스토밍)
- **Goal Clarity**: 목적을 한 문장으로 정의하고 핵심 엔티티를 파악합니다.
- **Constraint Clarity**: 스코프의 경계, 제약 조건, Non-goal(하지 않을 것)을 명시합니다.
- **Success Criteria**: 기능 구현이 완료되었음을 검증할 수 있는 조건을 설정합니다.
- 불확실한 점이 있다면 사용자에게 먼저 질문하여 모호도를 낮춥니다.

## 3. Analyze & Deepsearch (코드 분석)
- 기존 코드베이스(Brownfield)에서 작업하는 경우, 수정할 파일과 영향 범위를 깊이 탐색(Deepsearch)합니다.
- 단일 모듈 앱이므로 `data/`, `domain/`, `presentation/` 패키지 구조와 기존 패턴을 먼저 파악합니다.
- `llm-rules/` 하위의 코딩 규칙(layer-contract, compose-rules 등)을 읽고 패턴을 확인합니다.
- ViewModel, UiState, Flow, one-off effect, i18n-safe message를 건드리면 `.agents/llm-skills/android-state-and-side-effects.md`를 함께 적용합니다.
- API 실패, repository 오류 매핑, UI 실패 상태, retry/recovery를 건드리면 `.agents/llm-skills/android-error-handling.md`를 함께 적용합니다.
- deep link, 외부 intent, release build 동작을 건드리면 실제 기기/`adb`로 동작을 확인합니다.

## 4. Execute (실행 및 구현)

수립된 계획과 기존 아키텍처에 맞추어 코드를 작성합니다.

### 4-A. API/데이터 로직 작업 시 — 구현 순서 (반드시 이 순서대로)

```
Step 1. data/dto/             ← @Serializable DTO 정의
Step 2. data/dto/mapper/      ← DTO → Domain model 변환 확장 함수
Step 3. domain/model/         ← 순수 Kotlin 도메인 모델 (nullable 제거)
Step 4. domain/repository/    ← Repository 인터페이스
Step 5. data/repository/      ← Repository 구현체
Step 6. data/di/              ← Hilt 모듈 바인딩
Step 7. presentation/ViewModel ← StateFlow + Repository 주입
Step 8. presentation/Screen   ← UI (Compose or View)
```

규칙 상세는 `.agents/llm-rules/data/layer-contract.md` 참고.

### 4-B. UI 전용 작업 시
- Compose UI 구현/수정은 `.agents/llm-skills/compose-ui-development.md` 절차를 따릅니다.
- Reusable component 추출이 필요하면 caller가 product policy, copy, route를 계속 소유하는지 확인합니다.

### 4-C. 버그 수정 / 리팩토링 시
재현 → 원인 가설 → 최소 수정 → 검증 순서로 디버깅하고, 동작 변경 없는 리팩터링은 기존 동작 등가성을 확인합니다.

## 5. Verify (검증)
- 컴파일 에러가 없는지, Success Criteria를 만족하는지 검증합니다.
- `./gradlew testDebugUnitTest` 로 단위 테스트 통과 확인.
- 작업 완료 후 반드시 `.agents/llm-skills/code-simplification.md` 를 거쳐 코드를 최적화합니다.
- shared contract, API/DTO, error handling, persistence/cache, permission/security, background work, memory/lifecycle, runtime performance를 건드린 경우 컴파일만으로 완료를 주장하지 않고 해당 경계의 focused test 또는 manual scenario를 함께 보고합니다.
