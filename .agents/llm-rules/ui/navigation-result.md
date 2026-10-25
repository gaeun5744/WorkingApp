# 화면 간 데이터 전달 규칙 (Navigation 3 ResultEventBus)

> **핵심 규칙: 화면 → 화면 으로 데이터(결과)를 되돌려 보낼 때는 반드시 Navigation 3 의 `ResultEventBus` 를 사용한다.**
> 공유 ViewModel, `SavedStateHandle` 키 주고받기, 싱글톤 전역 변수, 콜백을 backStack 에 직접 끼워 넣는 방식 등은 사용하지 않는다.

참고 문서: https://developer.android.com/guide/navigation/navigation-3/recipes/results-event

## 언제 쓰나

- B 화면(입력/선택 화면)에서 작업을 마치고 A 화면(호출한 화면)으로 **결과 값**을 돌려줄 때.
- 예: 주소 검색 → 선택한 주소 반환, 금액 입력 → 입력값 반환, 연락처 선택 → 선택 대상 반환.
- 결과는 **1회성 이벤트**로 소비된다(재구성/회전 시 중복 처리되지 않음).

## 사용법

### 1. 호스트에 데코레이터 등록 (앱당 1회)

`NavDisplay` 의 `entryDecorators` 에 `rememberResultEventBusNavEntryDecorator()` 를 추가한다.
이 프로젝트에서는 `presentation/nav/AppNavHost.kt` 에 이미 설정되어 있다.

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(rememberResultEventBusNavEntryDecorator()),
    entryProvider = entryProvider { /* entry<...> { } */ },
)
```

### 2. 보내는 화면 (결과 반환)

```kotlin
entry<EditProfileKey> {
    val resultBus = LocalResultEventBus.current
    EditProfileScreen(
        onSubmit = { profile ->
            resultBus.sendResult(profile)   // 결과 전송
            backStack.removeLastOrNull()     // 뒤로
        },
    )
}
```

### 3. 받는 화면 (결과 수신)

```kotlin
entry<HomeKey> {
    var result by remember { mutableStateOf<ProfileResult?>(null) }
    ResultEffect<ProfileResult> { received -> result = received }   // 타입으로 구독
    HomeScreen(result = result, onEditProfile = { backStack.add(EditProfileKey) })
}
```

## 규칙 세부

- `sendResult<T>` 의 `T` 는 Parcelable/Serializable 일 필요 없다(메모리 내 1회성 이벤트). 화면 간 전달 데이터는 `presentation/nav` 의 일반 data class 로 정의한다.
- 같은 타입을 여러 화면이 동시에 구독해 충돌할 수 있으면 `sendResult(key, result)` / `ResultEffect(key) { }` 의 **key 오버로드**로 구분한다.
- `NavKey` 자체에 결과를 담아 전달하지 않는다. `NavKey` 는 목적지 식별/입력 인자용이고, 되돌려주는 결과는 ResultEventBus 로 보낸다.
- 새 결과 타입을 추가하면 `presentation/nav/NavKeys.kt`(또는 인접 파일)에 data class 로 선언한다.