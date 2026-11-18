package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ControlMode
import com.example.model.PlayerSettings
import com.example.model.RacingCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "turbo_roar_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SELECTED_CAR = stringPreferencesKey("selected_car")
        val SELECTED_TRACK = stringPreferencesKey("selected_track")
        val LOUD_BOOST = booleanPreferencesKey("loud_boost")
        val IS_MUTED = booleanPreferencesKey("is_muted")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        val controlModeName = prefs[Keys.CONTROL_MODE] ?: ControlMode.TOUCH.name
        val controlMode = runCatching { ControlMode.valueOf(controlModeName) }
            .getOrDefault(ControlMode.TOUCH)

        PlayerSettings(
            selectedCarId = prefs[Keys.SELECTED_CAR] ?: RacingCatalog.cars.first().id,
            selectedTrackId = prefs[Keys.SELECTED_TRACK] ?: RacingCatalog.tracks.first().id,
            loudBoostEnabled = prefs[Keys.LOUD_BOOST] ?: true,
            isMuted = prefs[Keys.IS_MUTED] ?: false,
            masterVolume = prefs[Keys.MASTER_VOLUME] ?: 1.0f,
            controlMode = controlMode
        )
    }

    suspend fun setSelectedCar(carId: String) {
        context.dataStore.edit { it[Keys.SELECTED_CAR] = carId }
    }

    suspend fun setSelectedTrack(trackId: String) {
        context.dataStore.edit { it[Keys.SELECTED_TRACK] = trackId }
    }

    suspend fun setLoudBoost(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOUD_BOOST] = enabled }
    }

    suspend fun setMuted(muted: Boolean) {
        context.dataStore.edit { it[Keys.IS_MUTED] = muted }
    }

    suspend fun setMasterVolume(volume: Float) {
        context.dataStore.edit { it[Keys.MASTER_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setControlMode(mode: ControlMode) {
        context.dataStore.edit { it[Keys.CONTROL_MODE] = mode.name }
    }
}
