---
type: rule
description: "Kotlin 관용 패턴/파일 분리 규칙 (null safety, immutability, sealed class, visibility)."
---

# Kotlin Code Guidelines & Migration Rules

Kotlin 관용 패턴 및 규칙. 새 코드 작성 시 반드시 적용. 기존 코드는 최소 수정 원칙.

## 1. 핵심 원칙
- **Null Safety**: `!!` 강제 언랩 금지. `?.` safe call, `?:` Elvis 우선.
- **Immutability by Default**: `val` 우선, `var` 최소화. 컬렉션은 불변형 선호.
- **Sealed Class / Interface**: 제한된 상태 계층 표현(`Result`, `UiState` 등). `when`에서 exhaustive하게 매칭.
- **Structured Concurrency**: `GlobalScope.launch` 금지. `viewModelScope`, `coroutineScope` 등 생명주기가 관리되는 스코프 사용.
- **Flow**: 리액티브 스트림을 위해 Flow 활용. `StateFlow`와 `SharedFlow`를 적절히 구분.
- **Error Handling**: 예외를 흐름 제어에 사용하지 않음. `Result<T>` 패턴 선호.
- **File Responsibility**: 파일은 primary top-level type과 변경 이유 기준으로 나눕니다. 모든 Kotlin 코드에 적용합니다.
- **Visibility by Boundary**: 파일/패키지 밖의 caller가 import해야 하는 contract만 public으로 둡니다. 내부 구현 세부사항은 `internal`, 파일 owner 전용 helper는 `private`을 기본값으로 둡니다.

## 2. 파일/타입 분리 기준

이 문서가 Kotlin 파일/타입 분리 기준의 source of truth입니다.
다른 Android 규칙/스킬 문서는 파일 분리 세부 기준을 반복하지 말고 이 섹션을 참조합니다.

파일 분리는 줄 수를 줄이는 작업이 아니라, 서로 다른 변경 이유가 한 파일에 섞이지 않도록 하는 구조 규칙입니다. 파일은 기본적으로 **독립적으로 import 가능한 owner 1개**를 가져야 합니다.

- public/internal top-level `class`, `interface`, `object`, `annotation class`는 기본적으로 1파일 1개입니다.
- 파일/패키지 밖에서 import될 이유가 없는 top-level type/function/property는 `internal` 또는 `private`로 선언합니다. Kotlin의 기본 visibility가 public이라는 이유만으로 impl 세부사항을 public ABI에 올리지 않습니다.
- 같은 파일에는 primary type 전용 `private` helper만 둡니다.
- "1파일 1 owner"는 importable **type**(`class`/`interface`/`object` 등) 기준입니다. top-level `@Composable` 함수는 type이 아니므로 개수가 아니라 책임 경계로 분리합니다. 화면 전용 `private` 헬퍼 Composable은 같은 파일에 둘 수 있습니다(세부 기준은 `.agents/llm-skills/compose-ui-development.md` Step 3). 단, UI와 state owner(ViewModel/Route)는 같은 파일에 두지 않습니다.
- `Contract + Impl`, `Parser + Mapper`, `Handler + Renderer`, `DI Module + business logic`, `DTO + mapper`를 한 파일에 넣지 않습니다.
- sealed hierarchy의 작은 value type은 한 파일에 둘 수 있지만, extension, parser, mapper, factory, renderer가 붙으면 분리합니다.
- extension은 receiver와 목적 기준으로 파일을 나눕니다. 예: `IssueDtoMapper.kt`, `ResultExtensions.kt`.
- DI module은 binding만 둡니다. qualifier가 여러 binding에서 쓰이거나 public binding key이면 별도 파일로 둡니다.
- `Manager`, `Helper`, `Util`, `Common` 같은 이름으로 여러 책임을 모으지 않습니다.
- 파일 경로와 package 선언은 반드시 일치해야 합니다.

다음은 review fail 기준입니다.

- 한 runtime 파일에 여러 importable owner를 유지합니다: class, object, interface, ViewModel, repository, service, mapper, validator, DI binding, platform adapter.
- parser, policy, mapper, factory, default implementation helper처럼 해당 패키지 안에서만 쓰는 타입을 visibility 없이 public으로 둡니다.
- nested class, companion object, extension 파일, catch-all `types`, `models`, `services`, `helpers`, `utils` 파일로 unrelated 책임을 숨깁니다.
- contract와 implementation, UI와 state owner, DTO와 mapper, route와 rendering, handler와 repository를 한 파일에 둡니다.
- 파일 이름이 `Manager`, `Helper`, `Util`, `Common`, `Base`처럼 넓은데 owner, allowed imports, verification path가 명확하지 않습니다.

새 파일을 만들 때는 다음을 만족해야 합니다.

- 책임 이름으로 파일명을 설명할 수 있습니다.
- public/internal/private visibility가 caller 범위와 맞습니다.
- allowed imports와 forbidden imports가 주변 package 규칙과 충돌하지 않습니다.
- preview, compile check 등 가까운 검증 경로가 있습니다.
- 단순 줄 수 줄이기나 취향 정리가 아니라 review scope, 변경 이유, dependency direction을 더 명확하게 합니다.

## 3. 안티패턴 금지 목록
- `!!` 강제 언랩
- 무분별한 `var` 및 가변 전역 상태
- `GlobalScope.launch` 사용
- 예외를 제어 흐름에 활용
- 깊게 중첩된 scope function (`apply { also { let { ... } } }`)
- 한 파일에 contract, implementation, parser, mapper, renderer, DI binding을 누적

## 4. 마이그레이션 규칙

### Java -> Kotlin 변환
- Java 코드를 Kotlin으로 변환할 때 단순히 문법만 바꾸지 말고, Kotlin의 Idiomatic 패턴(data class, null safety, 확장 함수 등)을 적극 적용합니다.
- 변환 후 반드시 린트(Lint)를 체크하고 불필요한 보일러플레이트 코드를 제거합니다.

### AGP 9.x 및 빌드 스크립트
- `build.gradle.kts` (Kotlin DSL)에서 타입 안전한 의존성 선언을 사용합니다.
- `libs.versions.toml` (Version Catalog)을 적극 활용합니다.

---
*참조: 이 문서는 도메인 지식(Fact)입니다. 실제 작업 시 이 룰들을 지키며 코드를 작성하세요.*
