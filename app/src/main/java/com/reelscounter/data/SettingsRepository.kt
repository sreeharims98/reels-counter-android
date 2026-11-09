package com.reelscounter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reelscounter.TrackedApp
import com.reelscounter.limits.LimitScope
import com.reelscounter.limits.Limits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class OverlayMode { OFF, IN_REELS, ALWAYS }

data class AppSettings(
    val trackInstagram: Boolean = true,
    val trackYoutube: Boolean = true,
    val countLimitEnabled: Boolean = false,
    val countLimit: Int = 50,
    val timeLimitEnabled: Boolean = false,
    val timeLimitMinutes: Int = 30,
    val scope: LimitScope = LimitScope.COMBINED,
    val showNotification: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.IN_REELS,
    val overlayX: Int = 0,
    val overlayY: Int = 0,
    val extraInstagramIds: String = "",
    val extraYoutubeIds: String = "",
) {
    val limits: Limits
        get() = Limits(countLimitEnabled, countLimit, timeLimitEnabled, timeLimitMinutes, scope)

    fun isTracked(app: TrackedApp) = when (app) {
        TrackedApp.INSTAGRAM -> trackInstagram
        TrackedApp.YOUTUBE -> trackYoutube
    }

    /** User-supplied view IDs (comma/space/newline separated) that also mark the reels feed. */
    fun extraIds(app: TrackedApp): List<String> = when (app) {
        TrackedApp.INSTAGRAM -> extraInstagramIds
        TrackedApp.YOUTUBE -> extraYoutubeIds
    }.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val trackIg = booleanPreferencesKey("track_ig")
        val trackYt = booleanPreferencesKey("track_yt")
        val countEnabled = booleanPreferencesKey("count_enabled")
        val countLimit = intPreferencesKey("count_limit")
        val timeEnabled = booleanPreferencesKey("time_enabled")
        val timeMinutes = intPreferencesKey("time_minutes")
        val scope = stringPreferencesKey("scope")
        val notification = booleanPreferencesKey("notification")
        val overlayMode = stringPreferencesKey("overlay_mode")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val extraIg = stringPreferencesKey("extra_ig_ids")
        val extraYt = stringPreferencesKey("extra_yt_ids")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            trackInstagram = p[K.trackIg] ?: d.trackInstagram,
            trackYoutube = p[K.trackYt] ?: d.trackYoutube,
            countLimitEnabled = p[K.countEnabled] ?: d.countLimitEnabled,
            countLimit = p[K.countLimit] ?: d.countLimit,
            timeLimitEnabled = p[K.timeEnabled] ?: d.timeLimitEnabled,
            timeLimitMinutes = p[K.timeMinutes] ?: d.timeLimitMinutes,
            scope = p[K.scope]?.let { runCatching { LimitScope.valueOf(it) }.getOrNull() } ?: d.scope,
            showNotification = p[K.notification] ?: d.showNotification,
            overlayMode = p[K.overlayMode]?.let { runCatching { OverlayMode.valueOf(it) }.getOrNull() } ?: d.overlayMode,
            overlayX = p[K.overlayX] ?: d.overlayX,
            overlayY = p[K.overlayY] ?: d.overlayY,
            extraInstagramIds = p[K.extraIg] ?: d.extraInstagramIds,
            extraYoutubeIds = p[K.extraYt] ?: d.extraYoutubeIds,
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setTracked(app: TrackedApp, on: Boolean) =
        set(if (app == TrackedApp.INSTAGRAM) K.trackIg else K.trackYt, on)
    suspend fun setCountLimitEnabled(on: Boolean) = set(K.countEnabled, on)
    suspend fun setCountLimit(n: Int) = set(K.countLimit, n.coerceIn(1, 9999))
    suspend fun setTimeLimitEnabled(on: Boolean) = set(K.timeEnabled, on)
    suspend fun setTimeLimitMinutes(m: Int) = set(K.timeMinutes, m.coerceIn(1, 1440))
    suspend fun setScope(scope: LimitScope) = set(K.scope, scope.name)
    suspend fun setShowNotification(on: Boolean) = set(K.notification, on)
    suspend fun setOverlayMode(mode: OverlayMode) = set(K.overlayMode, mode.name)
    suspend fun setOverlayPosition(x: Int, y: Int) = context.dataStore.edit {
        it[K.overlayX] = x
        it[K.overlayY] = y
    }
    suspend fun setExtraIds(app: TrackedApp, ids: String) =
        set(if (app == TrackedApp.INSTAGRAM) K.extraIg else K.extraYt, ids)
}
