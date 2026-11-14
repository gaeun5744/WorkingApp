import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// local.properties 의 개인 키 (VCS 에 커밋되지 않음)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val naverMapClientId: String = localProperties.getProperty("NAVER_MAP_CLIENT_ID").orEmpty().also {
    if (it.isBlank()) logger.warn("NAVER_MAP_CLIENT_ID 가 local.properties 에 없어 지도가 인증 실패로 표시됩니다.")
}

android {
    namespace = "com.example.workingapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.workingapp"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phase A: 모든 빌드가 앱 내부 MockApiInterceptor 로 응답한다 (PRD Q18). M11 에서 실제 서버로 전환.
        buildConfigField("boolean", "USE_MOCK_API", "true")
        buildConfigField("String", "BASE_URL", "\"https://mock.workingapp.local/\"")
        // 하드웨어 걸음 센서가 없는 에뮬레이터 검증용: ./gradlew assembleDebug -PfakeStepSensor=true
        val fakeStepSensor = providers.gradleProperty("fakeStepSensor").orNull?.toBoolean() ?: false
        buildConfigField("boolean", "USE_FAKE_STEP_SENSOR", "$fakeStepSensor")
        manifestPlaceholders["NAVER_MAP_CLIENT_ID"] = naverMapClientId
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        // Room 스키마 이력 — 마이그레이션 테스트에서 사용
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    // Hilt/Dagger 2.60 생성 코드가 참조하는 @CanIgnoreReturnValue 제공 (컴파일 타임 전용)
    compileOnly(libs.errorprone.annotations)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background: Foreground Service(LifecycleService), WorkManager + Hilt Worker
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Map & Location
    implementation(libs.naver.map.sdk)
    implementation(libs.play.services.location)

    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
