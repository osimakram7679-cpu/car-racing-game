package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Process
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Low-latency, real-time multi-harmonic PCM audio synthesizer for loud supercar sound.
 * Synthesizes V8/V10/V12 engine growl, exhaust crackles, turbo/nitro jet whine,
 * tire screech, dual-tone car horn, near-miss whoosh, pickup chimes, and crash impacts.
 * Uses Android's LoudnessEnhancer (+12 dB hardware boost) + soft-clipping overdrive saturation.
 */
class CarSoundEngine(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var isThreadRunning = false
    @Volatile private var isPlaybackActive = false

    // Real-time vehicle telemetry parameters (lock-free updated from UI/game loop)
    @Volatile private var engineActive = false
    @Volatile private var targetRpm = 950f
    @Volatile private var targetThrottle = 0.15f
    @Volatile private var carPitchMultiplier = 1.0f
    @Volatile private var isNitroActive = false
    @Volatile private var isHornActive = false
    @Volatile private var tireScreechIntensity = 0f

    // Master loudness controls
    @Volatile private var isMuted = false
    @Volatile private var loudBoostEnabled = true
    @Volatile private var masterVolume = 1.0f

    // One-shot synthesized SFX timers (countdown / crash / near-miss / pickup / gearshift)
    @Volatile private var crashTimer = 0f
    @Volatile private var gearPopTimer = 0f
    @Volatile private var nearMissTimer = 0f
    @Volatile private var pickupTimer = 0f
    @Volatile private var beepTimer = 0f
    @Volatile private var beepFreq = 520f

    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun start() {
        if (isThreadRunning) {
            resume()
            return
        }
        requestAudioFocus()
        isThreadRunning = true
        isPlaybackActive = true

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runSynthesisLoop()
        }, "CarAudioSynthThread").apply {
            isDaemon = true
            start()
        }
    }

    fun pause() {
        isPlaybackActive = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {
        }
    }

    fun resume() {
        if (!isThreadRunning) {
            start()
            return
        }
        requestAudioFocus()
        isPlaybackActive = true
        try {
            audioTrack?.play()
        } catch (_: Exception) {
        }
    }

    fun release() {
        isThreadRunning = false
        isPlaybackActive = false
        try {
            audioThread?.join(300)
        } catch (_: InterruptedException) {
        }
        audioThread = null
        abandonAudioFocus()
        cleanupTrack()
    }

    fun updateSettings(
        muted: Boolean,
        loudBoost: Boolean,
        volume: Float
    ) {
        isMuted = muted
        loudBoostEnabled = loudBoost
        masterVolume = volume.coerceIn(0f, 1f)
        applyHardwareLoudness()
    }

    fun updateVehicleTelemetry(
        engineRunning: Boolean,
        rpm: Float,
        throttle: Float,
        pitchMultiplier: Float,
        nitro: Boolean,
        horn: Boolean,
        screech: Float
    ) {
        engineActive = engineRunning
        targetRpm = rpm.coerceIn(750f, 9500f)
        targetThrottle = throttle.coerceIn(0f, 1f)
        carPitchMultiplier = pitchMultiplier.coerceIn(0.6f, 1.5f)
        isNitroActive = nitro
        isHornActive = horn
        tireScreechIntensity = screech.coerceIn(0f, 1f)
    }

    fun triggerCrash() {
        crashTimer = 0.50f
    }

    fun triggerGearShift() {
        gearPopTimer = 0.14f
    }

    fun triggerNearMiss() {
        nearMissTimer = 0.28f
    }

    fun triggerPickup() {
        pickupTimer = 0.24f
    }

    fun triggerCountdownBeep(isGo: Boolean) {
        beepFreq = if (isGo) 980f else 520f
        beepTimer = if (isGo) 0.28f else 0.14f
    }

    private fun applyHardwareLoudness() {
        try {
            val track = audioTrack ?: return
            val effectiveVol = if (isMuted) 0f else masterVolume
            track.setVolume(effectiveVol)
            loudnessEnhancer?.apply {
                val gainMb = if (loudBoostEnabled) 1250 else 250 // +12.5 dB boost when MAX ROAR is on!
                setTargetGain(gainMb)
                enabled = !isMuted
            }
        } catch (_: Exception) {
        }
    }

    private fun runSynthesisLoop() {
        val sampleRate = 44100
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val bufferBytes = (minBufBytes * 2).coerceAtMost(8192)
        val chunkSamples = 512
        val pcmBuffer = ShortArray(chunkSamples)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            isThreadRunning = false
            return
        }

        audioTrack = track
        try {
            loudnessEnhancer = LoudnessEnhancer(track.audioSessionId)
        } catch (_: Exception) {
            loudnessEnhancer = null
        }

        applyHardwareLoudness()
        try {
            track.play()
        } catch (_: Exception) {
        }

        val twoPi = (2.0 * PI).toFloat()
        val dt = 1f / sampleRate

        var enginePhase = 0f
        var hornPhaseA = 0f
        var hornPhaseB = 0f
        var turboPhase = 0f
        var screechPhase = 0f
        var crashPhase = 0f
        var beepPhase = 0f
        var pickupPhase = 0f

        var smoothRpm = 950f
        var smoothThrottle = 0.15f
        var lpNoise = 0f
        var hpPrevNoise = 0f
        var prevRawNoise = 0f

        val rng = Random(42)

        while (isThreadRunning) {
            if (!isPlaybackActive || isMuted) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            val localTargetRpm = targetRpm
            val localTargetThrottle = targetThrottle
            val localEngineOn = engineActive
            val localPitch = carPitchMultiplier
            val localNitro = isNitroActive
            val localHorn = isHornActive
            val localScreech = tireScreechIntensity
            val driveGain = if (loudBoostEnabled) 2.25f else 1.25f

            for (i in 0 until chunkSamples) {
                // Smooth RPM & throttle transitions
                smoothRpm += (localTargetRpm - smoothRpm) * 0.0028f
                smoothThrottle += (localTargetThrottle - smoothThrottle) * 0.004f

                val rawNoise = rng.nextFloat() * 2f - 1f
                lpNoise += (rawNoise - lpNoise) * 0.16f
                val hpNoise = rawNoise - prevRawNoise + 0.85f * hpPrevNoise
                prevRawNoise = rawNoise
                hpPrevNoise = hpNoise

                var mix = 0f

                // 1. Multi-harmonic V8/V10/V12 Engine Synthesizer
                if (localEngineOn) {
                    val firingHz = (smoothRpm / 60f) * 2.5f * localPitch
                    enginePhase += twoPi * firingHz * dt
                    if (enginePhase >= twoPi) enginePhase -= twoPi

                    val p1 = sin(enginePhase)
                    val p2 = sin(enginePhase * 2f)
                    val p3 = sin(enginePhase * 3f)
                    val p4 = sin(enginePhase * 4f)
                    val subBurble = sin(enginePhase * 0.5f)

                    // Sawtooth-like exhaust rasp approximated smoothly
                    val sawRasp = (enginePhase / PI.toFloat()) - 1f

                    val rpmNorm = ((smoothRpm - 900f) / 7800f).coerceIn(0f, 1f)
                    val load = 0.42f + 0.58f * smoothThrottle

                    val engineWave = (
                        0.52f * p1 +
                        0.44f * p2 +
                        0.32f * p3 * (0.5f + 0.5f * rpmNorm) +
                        0.24f * p4 * rpmNorm +
                        0.22f * subBurble * (1f - 0.5f * rpmNorm) +
                        0.18f * sawRasp * load
                    ) * load

                    // Exhaust rumble texture
                    val exhaustRumble = lpNoise * (0.20f + 0.30f * smoothThrottle)
                    mix += (engineWave + exhaustRumble) * 0.72f
                }

                // 2. Gear-shift exhaust pop / turbo flutter
                val gTimer = gearPopTimer
                if (gTimer > 0f) {
                    val env = (gTimer / 0.14f).coerceIn(0f, 1f)
                    mix += (lpNoise * 0.9f + rawNoise * 0.35f) * env * 0.75f
                    gearPopTimer = (gTimer - dt).coerceAtLeast(0f)
                }

                // 3. Nitro Afterburner + Turbo Turbine Whine
                if (localNitro && localEngineOn) {
                    val turbineHz = 720f + ((smoothRpm / 8800f) * 950f)
                    turboPhase += twoPi * turbineHz * dt
                    if (turboPhase >= twoPi) turboPhase -= twoPi

                    val jetRoar = (lpNoise * 0.65f + rawNoise * 0.35f) * 0.55f
                    val turbineWhine = sin(turboPhase) * 0.28f
                    mix += jetRoar + turbineWhine
                }

                // 4. Dual-Tone Supercar Horn (415 Hz + 523 Hz brassy square-saw blend)
                if (localHorn) {
                    hornPhaseA += twoPi * 415f * dt
                    hornPhaseB += twoPi * 523.25f * dt
                    if (hornPhaseA >= twoPi) hornPhaseA -= twoPi
                    if (hornPhaseB >= twoPi) hornPhaseB -= twoPi

                    val hornA = tanh(sin(hornPhaseA) * 2.8f)
                    val hornB = tanh(sin(hornPhaseB) * 2.8f)
                    mix += (hornA + hornB) * 0.50f
                }

                // 5. Tire Screech on sharp high-speed cornering
                if (localScreech > 0.05f && localEngineOn) {
                    screechPhase += twoPi * (1120f + lpNoise * 160f) * dt
                    if (screechPhase >= twoPi) screechPhase -= twoPi
                    val squeal = sin(screechPhase) * 0.5f + hpNoise * 0.3f
                    mix += squeal * localScreech * 0.38f
                }

                // 6. Near-Miss Doppler Whoosh
                val nmTimer = nearMissTimer
                if (nmTimer > 0f) {
                    val progress = 1f - (nmTimer / 0.28f)
                    val env = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
                    mix += (lpNoise * 0.7f + rawNoise * 0.3f) * env * 0.60f
                    nearMissTimer = (nmTimer - dt).coerceAtLeast(0f)
                }

                // 7. Pickup Chime (Coin / Nitro)
                val pTimer = pickupTimer
                if (pTimer > 0f) {
                    val progress = 1f - (pTimer / 0.24f)
                    val chimeHz = if (progress < 0.5f) 880f else 1318.5f
                    pickupPhase += twoPi * chimeHz * dt
                    if (pickupPhase >= twoPi) pickupPhase -= twoPi
                    val env = (1f - progress).coerceIn(0f, 1f)
                    mix += sin(pickupPhase) * env * 0.55f
                    pickupTimer = (pTimer - dt).coerceAtLeast(0f)
                }

                // 8. Countdown Race Lights Beep
                val bTimer = beepTimer
                if (bTimer > 0f) {
                    beepPhase += twoPi * beepFreq * dt
                    if (beepPhase >= twoPi) beepPhase -= twoPi
                    val env = (bTimer / 0.14f).coerceIn(0f, 1f)
                    mix += tanh(sin(beepPhase) * 2.0f) * env * 0.65f
                    beepTimer = (bTimer - dt).coerceAtLeast(0f)
                }

                // 9. Heavy Crash Impact (sub-bass thud + metallic crunch)
                val cTimer = crashTimer
                if (cTimer > 0f) {
                    val progress = 1f - (cTimer / 0.50f)
                    val thudHz = 110f * (1f - 0.7f * progress)
                    crashPhase += twoPi * thudHz * dt
                    if (crashPhase >= twoPi) crashPhase -= twoPi
                    val env = (1f - progress) * (1f - progress)
                    val crunch = rawNoise * 0.85f + sin(crashPhase) * 1.15f
                    mix += crunch * env * 1.15f
                    crashTimer = (cTimer - dt).coerceAtLeast(0f)
                }

                // Analog-style soft-clipping overdrive limiter for maximum clean loudness
                val saturated = tanh(mix * driveGain)
                val sampleInt = (saturated * 32000f).toInt().coerceIn(-32767, 32767)
                pcmBuffer[i] = sampleInt.toShort()
            }

            try {
                track.write(pcmBuffer, 0, chunkSamples)
            } catch (_: Exception) {
                break
            }
        }

        cleanupTrack()
    }

    private fun cleanupTrack() {
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        loudnessEnhancer = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }
}
