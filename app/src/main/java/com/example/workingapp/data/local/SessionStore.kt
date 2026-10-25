package com.example.workingapp.data.local

import android.content.Context
import androidx.core.content.edit
import com.example.workingapp.domain.model.Region
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인증 토큰과 재가입에 필요한 프로필(닉네임·동네)을 저장한다.
 *
 * DataStore 대신 SharedPreferences 를 쓰는 이유: OkHttp 인터셉터는 동기 함수라
 * 토큰을 동기로 읽어야 하는데, DataStore 는 `runBlocking` 없이는 동기로 읽을 수 없다 (지침상 금지).
 */
@Singleton
internal class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val nickname: String? get() = prefs.getString(KEY_NICKNAME, null)

    val region: Region?
        get() {
            val id = prefs.getLong(KEY_REGION_ID, NO_REGION)
            val name = prefs.getString(KEY_REGION_NAME, null)
            return if (id == NO_REGION || name == null) null else Region(id, name)
        }

    fun saveTokens(accessToken: String, refreshToken: String) = prefs.edit {
        putString(KEY_ACCESS_TOKEN, accessToken)
        putString(KEY_REFRESH_TOKEN, refreshToken)
    }

    fun saveNickname(nickname: String) = prefs.edit { putString(KEY_NICKNAME, nickname) }

    fun saveRegion(region: Region) = prefs.edit {
        putLong(KEY_REGION_ID, region.id)
        putString(KEY_REGION_NAME, region.name)
    }

    companion object {
        internal const val PREFS_NAME = "session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_REGION_ID = "region_id"
        private const val KEY_REGION_NAME = "region_name"
        private const val NO_REGION = -1L
    }
}
