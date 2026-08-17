package io.cherry.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Singleton DataStore attached to the application context. Defined at file
 * scope so the lazy delegate is created exactly once per process.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cherry_settings",
)

/**
 * Typed accessors over the global settings DataStore. Suspending reads +
 * Flow-based observation. DataStore's preferences API returns its own
 * `Flow<Preferences>`; we `.map` it into a typed `Flow<CherrySettings>`
 * so callers don't touch the raw key/value bag.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<CherrySettings> = context.settingsDataStore.data.map { prefs ->
        CherrySettings(
            playbackSpeed = clampSpeed(prefs[KEY_SPEED] ?: DEFAULT_SPEED),
            lowPriorityOnBattery = prefs[KEY_BATTERY] ?: false,
        )
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        val clamped = clampSpeed(speed)
        context.settingsDataStore.edit { prefs -> prefs[KEY_SPEED] = clamped }
    }

    suspend fun setLowPriorityOnBattery(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_BATTERY] = enabled }
    }

    suspend fun resetSpeedToDefault() {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SPEED] = DEFAULT_SPEED }
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4.0f
        const val DEFAULT_SPEED = 1.0f
        const val SPEED_STEP = 0.05f

        private val KEY_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_BATTERY = booleanPreferencesKey("low_priority_on_battery")

        /**
         * Clamp a raw speed value into the legal [MIN_SPEED, MAX_SPEED]
         * range, snapped to the nearest [SPEED_STEP]. Pure function so it
         * can be unit-tested without Android.
         */
        fun clampSpeed(raw: Float): Float {
            val coerced = raw.coerceIn(MIN_SPEED, MAX_SPEED)
            val steps = (coerced / SPEED_STEP).toInt()
            return (steps * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)
        }
    }
}

/** Typed snapshot of all persisted settings. */
data class CherrySettings(
    val playbackSpeed: Float,
    val lowPriorityOnBattery: Boolean,
)