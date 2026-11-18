package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.CarSoundEngine
import com.example.data.SettingsRepository
import com.example.model.CarSpec
import com.example.model.ControlMode
import com.example.model.FloatingCallout
import com.example.model.GameScreen
import com.example.model.Particle
import com.example.model.PickupItem
import com.example.model.PickupType
import com.example.model.PlayerSettings
import com.example.model.RaceStatus
import com.example.model.RaceUiState
import com.example.model.RacingCatalog
import com.example.model.SkidMark
import com.example.model.TrafficCar
import com.example.model.TrafficType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NitroOrange
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.TurboGold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class RacingViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    val soundEngine = CarSoundEngine(application)

    val settings: StateFlow<PlayerSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerSettings()
        )

    private val _currentScreen = MutableStateFlow(GameScreen.GARAGE)
    val currentScreen: StateFlow<GameScreen> = _currentScreen.asStateFlow()

    private val _raceState = MutableStateFlow(RaceUiState())
    val raceState: StateFlow<RaceUiState> = _raceState.asStateFlow()

    // Garage engine-rev preview state
    private val _garageRevRpm = MutableStateFlow(950f)
    val garageRevRpm: StateFlow<Float> = _garageRevRpm.asStateFlow()

    private val _isGarageRevving = MutableStateFlow(false)
    val isGarageRevving: StateFlow<Boolean> = _isGarageRevving.asStateFlow()

    // Live input flags (updated smoothly by touch/tilt controls)
    @Volatile private var steerLeftPressed = false
    @Volatile private var steerRightPressed = false
    @Volatile private var tiltSteerValue = 0f
    @Volatile private var brakePressed = false
    @Volatile private var nitroPressed = false
    @Volatile private var hornPressed = false

    private var steerVelocity = 0f
    private var nextEntityId = 1L
    private var trafficSpawnTimer = 0.6f
    private var pickupSpawnTimer = 2.5f
    private var countdownAccum = 0f
    private var lastCountdownBeep = -1
    private var sessionBestScore = 0
    private val rng = Random(System.currentTimeMillis())

    companion object {
        const val PLAYER_Y = 0.74f
        const val PLAYER_HALF_W = 0.115f
        const val PLAYER_HALF_H = 0.056f
        private val LANE_CENTERS = floatArrayOf(-0.72f, -0.24f, 0.24f, 0.72f)
    }

    init {
        soundEngine.start()
        viewModelScope.launch {
            settings.collect { s ->
                soundEngine.updateSettings(
                    muted = s.isMuted,
                    loudBoost = s.loudBoostEnabled,
                    volume = s.masterVolume
                )
            }
        }
        // Keep garage idle/rev sound alive when in Garage
        viewModelScope.launch {
            var lastTime = System.nanoTime()
            while (true) {
                kotlinx.coroutines.delay(16L)
                val now = System.nanoTime()
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                if (_currentScreen.value == GameScreen.GARAGE) {
                    val car = RacingCatalog.carById(settings.value.selectedCarId)
                    val target = if (_isGarageRevving.value) 8300f else 980f
                    val rate = if (_isGarageRevving.value) 10f else 4.5f
                    val current = _garageRevRpm.value
                    val next = current + (target - current) * (rate * dt).coerceAtMost(1f)
                    _garageRevRpm.value = next

                    soundEngine.updateVehicleTelemetry(
                        engineRunning = true,
                        rpm = next,
                        throttle = if (_isGarageRevving.value) 1.0f else 0.12f,
                        pitchMultiplier = car.enginePitchMultiplier,
                        nitro = false,
                        horn = hornPressed,
                        screech = 0f
                    )
                }
            }
        }
    }

    fun onAppPause() {
        soundEngine.pause()
        if (_currentScreen.value == GameScreen.RACING &&
            _raceState.value.status == RaceStatus.RUNNING
        ) {
            pauseRace()
        }
    }

    fun onAppResume() {
        soundEngine.resume()
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }

    // --- Settings Actions ---

    fun selectCar(carId: String) {
        viewModelScope.launch { settingsRepository.setSelectedCar(carId) }
        soundEngine.triggerPickup()
    }

    fun selectTrack(trackId: String) {
        viewModelScope.launch { settingsRepository.setSelectedTrack(trackId) }
        soundEngine.triggerPickup()
    }

    fun toggleLoudBoost(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLoudBoost(enabled) }
    }

    fun toggleMute() {
        viewModelScope.launch { settingsRepository.setMuted(!settings.value.isMuted) }
    }

    fun setMasterVolume(volume: Float) {
        viewModelScope.launch { settingsRepository.setMasterVolume(volume) }
    }

    fun setControlMode(mode: ControlMode) {
        viewModelScope.launch { settingsRepository.setControlMode(mode) }
    }

    fun setGarageRevving(revving: Boolean) {
        val wasRevving = _isGarageRevving.value
        _isGarageRevving.value = revving
        if (wasRevving && !revving) {
            soundEngine.triggerGearShift() // Exhaust pop when letting off throttle!
        }
    }

    fun blastHornOnce() {
        viewModelScope.launch {
            hornPressed = true
            kotlinx.coroutines.delay(220)
            hornPressed = false
        }
    }

    // --- Navigation & Lifecycle ---

    fun startRace() {
        val currentSettings = settings.value
        val car = RacingCatalog.carById(currentSettings.selectedCarId)
        val track = RacingCatalog.trackById(currentSettings.selectedTrackId)

        clearInputs()
        steerVelocity = 0f
        trafficSpawnTimer = 0.4f
        pickupSpawnTimer = 2.0f
        countdownAccum = 0f
        lastCountdownBeep = -1

        _raceState.value = RaceUiState(
            status = RaceStatus.COUNTDOWN,
            countdownValue = 3,
            car = car,
            track = track,
            playerX = 0f,
            speedKmh = 0f,
            rpm = 1800f,
            gear = 1,
            nitroFuel = 100f,
            shieldHits = 3,
            sessionBestScore = sessionBestScore
        )
        _currentScreen.value = GameScreen.RACING
        soundEngine.triggerCountdownBeep(isGo = false)
        lastCountdownBeep = 3
    }

    fun pauseRace() {
        clearInputs()
        _raceState.update { state ->
            if (state.status == RaceStatus.RUNNING || state.status == RaceStatus.COUNTDOWN) {
                state.copy(status = RaceStatus.PAUSED, isHornActive = false, isNitroActive = false)
            } else {
                state
            }
        }
        soundEngine.updateVehicleTelemetry(
            engineRunning = false,
            rpm = 900f,
            throttle = 0f,
            pitchMultiplier = _raceState.value.car.enginePitchMultiplier,
            nitro = false,
            horn = false,
            screech = 0f
        )
    }

    fun resumeRace() {
        _raceState.update { state ->
            if (state.status == RaceStatus.PAUSED) {
                state.copy(status = RaceStatus.RUNNING)
            } else {
                state
            }
        }
    }

    fun returnToGarage() {
        clearInputs()
        _currentScreen.value = GameScreen.GARAGE
    }

    // --- Race Inputs ---

    fun setSteerLeft(pressed: Boolean) {
        steerLeftPressed = pressed
    }

    fun setSteerRight(pressed: Boolean) {
        steerRightPressed = pressed
    }

    fun setTiltSteer(value: Float) {
        tiltSteerValue = value.coerceIn(-1f, 1f)
    }

    fun setBrake(pressed: Boolean) {
        brakePressed = pressed
    }

    fun setNitro(pressed: Boolean) {
        nitroPressed = pressed
    }

    fun setHorn(pressed: Boolean) {
        hornPressed = pressed
    }

    private fun clearInputs() {
        steerLeftPressed = false
        steerRightPressed = false
        tiltSteerValue = 0f
        brakePressed = false
        nitroPressed = false
        hornPressed = false
    }

    // --- 60 FPS Simulation Tick ---

    fun tickRace(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val state = _raceState.value

        if (state.status == RaceStatus.PAUSED || state.status == RaceStatus.GAME_OVER) {
            soundEngine.updateVehicleTelemetry(
                engineRunning = false,
                rpm = 900f,
                throttle = 0f,
                pitchMultiplier = state.car.enginePitchMultiplier,
                nitro = false,
                horn = false,
                screech = 0f
            )
            return
        }

        // Countdown phase (3, 2, 1, GO!)
        if (state.status == RaceStatus.COUNTDOWN) {
            countdownAccum += dt
            val remaining = (3 - countdownAccum.toInt()).coerceAtLeast(0)
            if (remaining != lastCountdownBeep && remaining in 1..2) {
                soundEngine.triggerCountdownBeep(isGo = false)
                lastCountdownBeep = remaining
            }
            val revPulse = 2200f + abs(sin(countdownAccum * 8f)) * 2800f
            soundEngine.updateVehicleTelemetry(
                engineRunning = true,
                rpm = revPulse,
                throttle = 0.55f,
                pitchMultiplier = state.car.enginePitchMultiplier,
                nitro = false,
                horn = hornPressed,
                screech = 0f
            )

            if (countdownAccum >= 2.7f) {
                soundEngine.triggerCountdownBeep(isGo = true)
                _raceState.update {
                    it.copy(
                        status = RaceStatus.RUNNING,
                        countdownValue = 0,
                        speedKmh = 95f,
                        rpm = 4800f
                    )
                }
            } else {
                _raceState.update {
                    it.copy(
                        countdownValue = remaining.coerceAtLeast(1),
                        rpm = revPulse,
                        isHornActive = hornPressed,
                        policeFlashPhase = it.policeFlashPhase + dt * 8f
                    )
                }
            }
            return
        }

        // Running simulation
        val car = state.car
        val nitroActive = nitroPressed && state.nitroFuel > 1f && !brakePressed
        val nextNitroFuel = if (nitroActive) {
            (state.nitroFuel - 28f * dt).coerceAtLeast(0f)
        } else {
            (state.nitroFuel + 4.5f * dt).coerceAtMost(100f)
        }

        val maxSpeed = if (nitroActive) car.topSpeedKmh * 1.18f else car.topSpeedKmh
        val targetSpeed = when {
            brakePressed -> 70f
            nitroActive -> maxSpeed
            else -> maxSpeed * (0.82f + (state.distanceKm * 0.04f).coerceAtMost(0.18f))
        }

        val accelRate = when {
            brakePressed -> 280f
            nitroActive -> 195f * car.acceleration
            else -> 95f * car.acceleration
        }

        val nextSpeed = if (state.speedKmh < targetSpeed) {
            (state.speedKmh + accelRate * dt).coerceAtMost(targetSpeed)
        } else {
            (state.speedKmh - accelRate * 1.1f * dt).coerceAtLeast(targetSpeed)
        }

        // Gear & RPM calculation (6-speed sequential simulation)
        val speedNorm = (nextSpeed / (car.topSpeedKmh * 1.18f)).coerceIn(0f, 1f)
        val nextGear = when {
            nextSpeed < 55f -> 1
            nextSpeed < 105f -> 2
            nextSpeed < 160f -> 3
            nextSpeed < 220f -> 4
            nextSpeed < 280f -> 5
            else -> 6
        }
        if (nextGear > state.gear) {
            soundEngine.triggerGearShift()
        }

        val gearMinSpeed = when (nextGear) {
            1 -> 0f; 2 -> 50f; 3 -> 100f; 4 -> 155f; 5 -> 215f; else -> 275f
        }
        val gearMaxSpeed = when (nextGear) {
            1 -> 65f; 2 -> 115f; 3 -> 170f; 4 -> 230f; 5 -> 290f; else -> car.topSpeedKmh * 1.18f
        }
        val gearProgress = ((nextSpeed - gearMinSpeed) / (gearMaxSpeed - gearMinSpeed).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val nextRpm = (2400f + gearProgress * 6100f + if (nitroActive) 450f else 0f).coerceIn(1100f, 8900f)

        // Steering physics
        val rawSteerInput = when {
            steerLeftPressed && !steerRightPressed -> -1f
            steerRightPressed && !steerLeftPressed -> 1f
            abs(tiltSteerValue) > 0.05f -> tiltSteerValue
            else -> 0f
        }

        val maxSteerSpeed = (1.65f + 0.55f * speedNorm) * car.handling
        val targetSteerVel = rawSteerInput * maxSteerSpeed
        steerVelocity += (targetSteerVel - steerVelocity) * (13f * dt).coerceAtMost(1f)
        val nextPlayerX = (state.playerX + steerVelocity * dt).coerceIn(-0.82f, 0.82f)
        val nextTiltDeg = (steerVelocity * 9.5f).coerceIn(-16f, 16f)

        // Tire screech intensity when steering hard at high speed
        val screechLevel = if (abs(steerVelocity) > 1.15f && nextSpeed > 130f) {
            ((abs(steerVelocity) - 1.0f) * speedNorm).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Scroll speed in normalized screen-heights per second
        val scrollRate = (nextSpeed / 200f) * 0.95f
        val nextRoadOffset = (state.roadOffset + scrollRate * dt) % 1f
        val deltaKm = (nextSpeed / 3600f) * dt * 2.2f // Arcade-scaled km
        val nextDistanceKm = state.distanceKm + deltaKm

        // Update sound engine
        soundEngine.updateVehicleTelemetry(
            engineRunning = true,
            rpm = nextRpm,
            throttle = if (brakePressed) 0.1f else if (nitroActive) 1.0f else 0.78f,
            pitchMultiplier = car.enginePitchMultiplier,
            nitro = nitroActive,
            horn = hornPressed,
            screech = screechLevel
        )

        // Spawn Traffic
        trafficSpawnTimer -= dt
        val updatedTraffic = state.traffic.mapNotNull { t ->
            val relativeScroll = ((nextSpeed - t.speedKmh) / 200f) * 0.95f
            val newY = t.yNormalized + relativeScroll * dt
            if (newY > 1.25f || newY < -0.5f) null else t.copy(yNormalized = newY)
        }.toMutableList()

        if (trafficSpawnTimer <= 0f) {
            spawnTrafficCar(updatedTraffic, nextDistanceKm)
            val difficultyFactor = (1f - (nextDistanceKm * 0.06f)).coerceIn(0.42f, 1f)
            trafficSpawnTimer = (0.55f + rng.nextFloat() * 0.55f) * difficultyFactor
        }

        // Spawn Pickups (Nitro & Coins)
        pickupSpawnTimer -= dt
        val updatedPickups = state.pickups.mapNotNull { p ->
            val newY = p.yNormalized + scrollRate * dt
            if (newY > 1.15f) null else p.copy(yNormalized = newY)
        }.toMutableList()

        if (pickupSpawnTimer <= 0f) {
            val laneX = LANE_CENTERS[rng.nextInt(LANE_CENTERS.size)]
            val type = if (rng.nextFloat() < 0.35f) PickupType.NITRO else PickupType.COIN
            updatedPickups.add(
                PickupItem(
                    id = nextEntityId++,
                    xNormalized = laneX,
                    yNormalized = -0.1f,
                    type = type
                )
            )
            pickupSpawnTimer = 1.8f + rng.nextFloat() * 1.8f
        }

        // Particles (exhaust flames + sparks + skid marks)
        val updatedParticles = state.particles.mapNotNull { pt ->
            val newLife = pt.life - dt
            if (newLife <= 0f) null
            else pt.copy(
                x = pt.x + pt.vx * dt,
                y = pt.y + (pt.vy + scrollRate * 0.45f) * dt,
                life = newLife
            )
        }.toMutableList()

        // Exhaust flame particles when Nitro or high RPM
        if (nitroActive || (nextRpm > 7200f && rng.nextFloat() < 0.5f)) {
            val count = if (nitroActive) 3 else 1
            repeat(count) {
                val pipeOffset = if (rng.nextBoolean()) -0.055f else 0.055f
                updatedParticles.add(
                    Particle(
                        x = nextPlayerX + pipeOffset + (rng.nextFloat() - 0.5f) * 0.02f,
                        y = PLAYER_Y + PLAYER_HALF_H + 0.01f,
                        vx = (rng.nextFloat() - 0.5f) * 0.15f,
                        vy = 0.25f + rng.nextFloat() * 0.25f,
                        life = if (nitroActive) 0.22f else 0.12f,
                        maxLife = if (nitroActive) 0.22f else 0.12f,
                        size = if (nitroActive) 1.3f else 0.8f,
                        color = if (nitroActive) ElectricCyan else NitroOrange
                    )
                )
            }
        }

        // Tire skid marks on sharp turns
        val updatedSkids = state.skidMarks.mapNotNull { skid ->
            val newY = skid.yNormalized + scrollRate * dt
            val newAlpha = skid.alpha - dt * 0.65f
            if (newY > 1.1f || newAlpha <= 0f) null else skid.copy(yNormalized = newY, alpha = newAlpha)
        }.toMutableList()

        if (screechLevel > 0.25f || brakePressed && nextSpeed > 120f) {
            updatedSkids.add(SkidMark(nextPlayerX - 0.08f, PLAYER_Y + 0.03f, 0.55f))
            updatedSkids.add(SkidMark(nextPlayerX + 0.08f, PLAYER_Y + 0.03f, 0.55f))
        }

        // Callouts update
        val updatedCallouts = state.callouts.mapNotNull { c ->
            val newLife = c.life - dt
            if (newLife <= 0f) null else c.copy(yNormalized = c.yNormalized - 0.08f * dt, life = newLife)
        }.toMutableList()

        // Combo decay
        var comboMult = state.comboMultiplier
        var comboTimer = (state.comboTimer - dt).coerceAtLeast(0f)
        if (comboTimer <= 0f) comboMult = 1

        var nextScore = state.score + ((nextSpeed * dt * 0.45f) * comboMult).toInt()
        var nextNearMisses = state.nearMissCount
        var nextCoins = state.coinsCollected
        var nextShield = state.shieldHits
        var nextInvuln = (state.invulnerableTimer - dt).coerceAtLeast(0f)
        var nextShake = (state.screenShake - dt * 2.5f).coerceAtLeast(0f)
        var finalNitroFuel = nextNitroFuel
        var finalSpeed = nextSpeed
        var finalStatus = RaceStatus.RUNNING

        // Pickup collisions
        val pickupIter = updatedPickups.iterator()
        while (pickupIter.hasNext()) {
            val p = pickupIter.next()
            if (abs(nextPlayerX - p.xNormalized) < (PLAYER_HALF_W + 0.09f) &&
                abs(PLAYER_Y - p.yNormalized) < (PLAYER_HALF_H + 0.05f)
            ) {
                pickupIter.remove()
                soundEngine.triggerPickup()
                when (p.type) {
                    PickupType.NITRO -> {
                        finalNitroFuel = (finalNitroFuel + 38f).coerceAtMost(100f)
                        nextScore += 100
                        updatedCallouts.add(
                            FloatingCallout(
                                id = nextEntityId++,
                                text = "+NITRO!",
                                color = ElectricCyan,
                                xNormalized = p.xNormalized,
                                yNormalized = PLAYER_Y - 0.08f,
                                life = 0.85f
                            )
                        )
                    }
                    PickupType.COIN -> {
                        nextCoins += 1
                        val pts = 120 * comboMult
                        nextScore += pts
                        updatedCallouts.add(
                            FloatingCallout(
                                id = nextEntityId++,
                                text = "+$pts",
                                color = TurboGold,
                                xNormalized = p.xNormalized,
                                yNormalized = PLAYER_Y - 0.08f,
                                life = 0.85f
                            )
                        )
                    }
                }
            }
        }

        // Traffic Collisions & Near-Misses
        val trafficIter = updatedTraffic.iterator()
        while (trafficIter.hasNext()) {
            val t = trafficIter.next()
            val halfW = t.widthRatio * 0.5f
            val halfH = t.heightRatio * 0.5f

            val dx = abs(nextPlayerX - t.xNormalized)
            val dy = abs(PLAYER_Y - t.yNormalized)

            val hitX = dx < (PLAYER_HALF_W + halfW) * 0.82f
            val hitY = dy < (PLAYER_HALF_H + halfH) * 0.82f

            if (hitX && hitY && nextInvuln <= 0f) {
                soundEngine.triggerCrash()
                nextShake = 1.0f
                nextInvuln = 1.35f
                nextShield -= 1
                comboMult = 1
                comboTimer = 0f
                finalSpeed = (finalSpeed * 0.48f).coerceAtLeast(65f)
                trafficIter.remove()

                // Emit fiery crash sparks
                repeat(22) {
                    val angle = rng.nextFloat() * 6.28f
                    val spd = 0.35f + rng.nextFloat() * 0.85f
                    updatedParticles.add(
                        Particle(
                            x = nextPlayerX,
                            y = PLAYER_Y,
                            vx = kotlin.math.cos(angle) * spd,
                            vy = kotlin.math.sin(angle) * spd * 0.5f,
                            life = 0.45f,
                            maxLife = 0.45f,
                            size = 1.4f,
                            color = if (rng.nextBoolean()) NitroOrange else TurboGold
                        )
                    )
                }

                if (nextShield <= 0) {
                    finalStatus = RaceStatus.GAME_OVER
                } else {
                    updatedCallouts.add(
                        FloatingCallout(
                            id = nextEntityId++,
                            text = "IMPACT! $nextShield LEFT",
                            color = RedlineCrimson,
                            xNormalized = nextPlayerX,
                            yNormalized = PLAYER_Y - 0.1f,
                            life = 1.1f
                        )
                    )
                }
            } else if (!t.nearMissed && t.yNormalized > PLAYER_Y + 0.02f &&
                dx < (PLAYER_HALF_W + halfW + 0.16f) && dy < 0.12f && finalSpeed > 135f
            ) {
                t.nearMissed = true
                nextNearMisses += 1
                comboMult = (comboMult + 1).coerceAtMost(5)
                comboTimer = 3.2f
                val bonus = 150 * comboMult
                nextScore += bonus
                soundEngine.triggerNearMiss()
                updatedCallouts.add(
                    FloatingCallout(
                        id = nextEntityId++,
                        text = "NEAR MISS +$bonus (${comboMult}x)",
                        color = ShieldEmerald,
                        xNormalized = nextPlayerX,
                        yNormalized = PLAYER_Y - 0.1f,
                        life = 0.95f
                    )
                )
            }
        }

        if (nextScore > sessionBestScore) {
            sessionBestScore = nextScore
        }

        val topSpeed = maxOf(state.topSpeedReachedKmh, finalSpeed)

        _raceState.value = state.copy(
            status = finalStatus,
            playerX = nextPlayerX,
            playerTiltDeg = nextTiltDeg,
            speedKmh = finalSpeed,
            topSpeedReachedKmh = topSpeed,
            rpm = nextRpm,
            gear = nextGear,
            nitroFuel = finalNitroFuel,
            isNitroActive = nitroActive,
            isBraking = brakePressed,
            isHornActive = hornPressed,
            shieldHits = nextShield.coerceAtLeast(0),
            invulnerableTimer = nextInvuln,
            screenShake = nextShake,
            roadOffset = nextRoadOffset,
            score = nextScore,
            sessionBestScore = sessionBestScore,
            distanceKm = nextDistanceKm,
            nearMissCount = nextNearMisses,
            coinsCollected = nextCoins,
            comboMultiplier = comboMult,
            comboTimer = comboTimer,
            traffic = updatedTraffic,
            pickups = updatedPickups,
            particles = updatedParticles,
            skidMarks = updatedSkids,
            callouts = updatedCallouts,
            rainPhase = (state.rainPhase + dt * 3.5f) % 1f,
            policeFlashPhase = state.policeFlashPhase + dt * 10f
        )
    }

    private fun spawnTrafficCar(list: MutableList<TrafficCar>, distanceKm: Float) {
        val blockedNearTop = list.filter { it.yNormalized < 0.25f }.map { it.lane }.toSet()
        // Always leave at least 1 lane free for the player!
        if (blockedNearTop.size >= 3) return

        val freeLanes = (0..3).filter { it !in blockedNearTop }
        if (freeLanes.isEmpty()) return
        val lane = freeLanes[rng.nextInt(freeLanes.size)]

        val roll = rng.nextFloat()
        val type = when {
            roll < 0.14f && distanceKm > 0.25f -> TrafficType.POLICE
            roll < 0.34f -> TrafficType.TRUCK
            roll < 0.64f -> TrafficType.MUSCLE
            else -> TrafficType.SEDAN
        }

        val bodyColor = when (type) {
            TrafficType.POLICE -> Color(0xFF1E293B)
            TrafficType.TRUCK -> listOf(Color(0xFF64748B), Color(0xFF475569), Color(0xFF9A3412))[rng.nextInt(3)]
            TrafficType.MUSCLE -> listOf(Color(0xFF2563EB), Color(0xFFDC2626), Color(0xFFF59E0B), Color(0xFF10B981))[rng.nextInt(4)]
            TrafficType.SEDAN -> listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFF38BDF8), Color(0xFFA855F7))[rng.nextInt(4)]
        }

        val baseTrafficSpeed = when (type) {
            TrafficType.TRUCK -> 75f + rng.nextFloat() * 25f
            TrafficType.POLICE -> 130f + rng.nextFloat() * 35f
            TrafficType.MUSCLE -> 115f + rng.nextFloat() * 35f
            TrafficType.SEDAN -> 95f + rng.nextFloat() * 30f
        }

        val (wRatio, hRatio) = when (type) {
            TrafficType.TRUCK -> 0.27f to 0.165f
            TrafficType.POLICE -> 0.23f to 0.112f
            TrafficType.MUSCLE -> 0.235f to 0.112f
            TrafficType.SEDAN -> 0.225f to 0.105f
        }

        list.add(
            TrafficCar(
                id = nextEntityId++,
                lane = lane,
                xNormalized = LANE_CENTERS[lane],
                yNormalized = -0.18f,
                speedKmh = baseTrafficSpeed,
                type = type,
                bodyColor = bodyColor,
                widthRatio = wRatio,
                heightRatio = hRatio
            )
        )
    }
}
