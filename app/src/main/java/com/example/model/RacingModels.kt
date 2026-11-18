package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NitroOrange
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TurboGold

enum class ControlMode {
    TOUCH,
    TILT
}

enum class GameScreen {
    GARAGE,
    RACING
}

enum class RaceStatus {
    COUNTDOWN,
    RUNNING,
    PAUSED,
    GAME_OVER
}

enum class TrafficType {
    SEDAN,
    MUSCLE,
    TRUCK,
    POLICE
}

enum class PickupType {
    NITRO,
    COIN
}

data class CarSpec(
    val id: String,
    val name: String,
    val engineClass: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val topSpeedKmh: Float,
    val acceleration: Float,
    val handling: Float,
    val enginePitchMultiplier: Float,
    val speedStat: Float,
    val accelStat: Float,
    val handlingStat: Float
)

data class TrackTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    val roadColor: Color,
    val shoulderColor: Color,
    val curbColorA: Color,
    val curbColorB: Color,
    val laneLineColor: Color,
    val accentGlowColor: Color,
    val isNight: Boolean,
    val isRainy: Boolean
)

data class TrafficCar(
    val id: Long,
    val lane: Int,
    val xNormalized: Float, // -1.0 (left edge of road) to +1.0 (right edge of road)
    val yNormalized: Float, // -0.2 (top offscreen) to 1.2 (bottom offscreen)
    val speedKmh: Float,
    val type: TrafficType,
    val bodyColor: Color,
    val widthRatio: Float,
    val heightRatio: Float,
    var nearMissed: Boolean = false
)

data class PickupItem(
    val id: Long,
    val xNormalized: Float,
    val yNormalized: Float,
    val type: PickupType
)

data class Particle(
    val x: Float, // normalized road space (-1.2 .. 1.2)
    val y: Float, // normalized screen y (0 .. 1)
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Color
)

data class SkidMark(
    val xNormalized: Float,
    val yNormalized: Float,
    val alpha: Float
)

data class FloatingCallout(
    val id: Long,
    val text: String,
    val color: Color,
    val xNormalized: Float,
    val yNormalized: Float,
    val life: Float
)

data class PlayerSettings(
    val selectedCarId: String = RacingCatalog.cars.first().id,
    val selectedTrackId: String = RacingCatalog.tracks.first().id,
    val loudBoostEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val masterVolume: Float = 1.0f,
    val controlMode: ControlMode = ControlMode.TOUCH
)

data class RaceUiState(
    val status: RaceStatus = RaceStatus.COUNTDOWN,
    val countdownValue: Int = 3,
    val car: CarSpec = RacingCatalog.cars.first(),
    val track: TrackTheme = RacingCatalog.tracks.first(),
    val playerX: Float = 0f, // -0.78f to +0.78f inside road
    val playerTiltDeg: Float = 0f,
    val speedKmh: Float = 0f,
    val topSpeedReachedKmh: Float = 0f,
    val rpm: Float = 950f,
    val gear: Int = 1,
    val nitroFuel: Float = 100f,
    val isNitroActive: Boolean = false,
    val isBraking: Boolean = false,
    val isHornActive: Boolean = false,
    val shieldHits: Int = 3,
    val maxShieldHits: Int = 3,
    val invulnerableTimer: Float = 0f,
    val screenShake: Float = 0f,
    val roadOffset: Float = 0f,
    val score: Int = 0,
    val sessionBestScore: Int = 0,
    val distanceKm: Float = 0f,
    val nearMissCount: Int = 0,
    val coinsCollected: Int = 0,
    val comboMultiplier: Int = 1,
    val comboTimer: Float = 0f,
    val traffic: List<TrafficCar> = emptyList(),
    val pickups: List<PickupItem> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val skidMarks: List<SkidMark> = emptyList(),
    val callouts: List<FloatingCallout> = emptyList(),
    val rainPhase: Float = 0f,
    val policeFlashPhase: Float = 0f
)

object RacingCatalog {
    val cars = listOf(
        CarSpec(
            id = "apex_viper",
            name = "APEX VIPER",
            engineClass = "6.2L SUPERCHARGED V8",
            primaryColor = NitroOrange,
            secondaryColor = Color(0xFF1E242E),
            accentColor = TurboGold,
            topSpeedKmh = 315f,
            acceleration = 1.0f,
            handling = 1.0f,
            enginePitchMultiplier = 1.0f,
            speedStat = 0.86f,
            accelStat = 0.88f,
            handlingStat = 0.85f
        ),
        CarSpec(
            id = "cobalt_gt",
            name = "COBALT GT",
            engineClass = "5.0L TWIN-TURBO V10",
            primaryColor = ElectricCyan,
            secondaryColor = Color(0xFF0F172A),
            accentColor = Color.White,
            topSpeedKmh = 345f,
            acceleration = 0.94f,
            handling = 0.96f,
            enginePitchMultiplier = 1.18f,
            speedStat = 0.98f,
            accelStat = 0.82f,
            handlingStat = 0.82f
        ),
        CarSpec(
            id = "shadow_fury",
            name = "SHADOW FURY",
            engineClass = "6.5L NITRO V12",
            primaryColor = RedlineCrimson,
            secondaryColor = Color(0xFF111318),
            accentColor = NitroOrange,
            topSpeedKmh = 330f,
            acceleration = 1.16f,
            handling = 1.12f,
            enginePitchMultiplier = 0.90f,
            speedStat = 0.92f,
            accelStat = 0.98f,
            handlingStat = 0.95f
        )
    )

    val tracks = listOf(
        TrackTheme(
            id = "neon_highway",
            name = "NEON HIGHWAY",
            subtitle = "Midnight City Expressway",
            roadColor = Color(0xFF191F2C),
            shoulderColor = Color(0xFF0D1118),
            curbColorA = RedlineCrimson,
            curbColorB = Color(0xFFE2E8F0),
            laneLineColor = Color(0xFF64748B),
            accentGlowColor = ElectricCyan,
            isNight = true,
            isRainy = false
        ),
        TrackTheme(
            id = "desert_canyon",
            name = "DESERT CANYON",
            subtitle = "Sunset Red Rock Straight",
            roadColor = Color(0xFF282529),
            shoulderColor = Color(0xFF4A2518),
            curbColorA = TurboGold,
            curbColorB = Color(0xFF261C1A),
            laneLineColor = Color(0xFFFBBF24),
            accentGlowColor = NitroOrange,
            isNight = false,
            isRainy = false
        ),
        TrackTheme(
            id = "storm_circuit",
            name = "STORM CIRCUIT",
            subtitle = "Wet Tarmac Thunder Run",
            roadColor = Color(0xFF141B24),
            shoulderColor = Color(0xFF090D14),
            curbColorA = ElectricCyan,
            curbColorB = Color(0xFF1E293B),
            laneLineColor = Color(0xFF94A3B8),
            accentGlowColor = ElectricCyan,
            isNight = true,
            isRainy = true
        )
    )

    fun carById(id: String): CarSpec = cars.firstOrNull { it.id == id } ?: cars.first()
    fun trackById(id: String): TrackTheme = tracks.firstOrNull { it.id == id } ?: tracks.first()
}
