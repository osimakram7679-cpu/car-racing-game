package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ControlMode
import com.example.model.PlayerSettings
import com.example.model.RaceStatus
import com.example.model.RaceUiState
import com.example.sensor.TiltSteeringManager
import com.example.ui.components.drawRaceWorld
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCarbon
import com.example.ui.theme.NitroOrange
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.ShieldEmerald
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TurboGold
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RaceScreen(
    state: RaceUiState,
    settings: PlayerSettings,
    onTick: (Float) -> Unit,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onTiltSteer: (Float) -> Unit,
    onBrake: (Boolean) -> Unit,
    onNitro: (Boolean) -> Unit,
    onHorn: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onBackToGarage: () -> Unit,
    onToggleMute: () -> Unit
) {
    val context = LocalContext.current

    // Back gesture pauses active race or exits overlays to Garage
    BackHandler {
        when (state.status) {
            RaceStatus.RUNNING, RaceStatus.COUNTDOWN -> onPause()
            RaceStatus.PAUSED -> onResume()
            RaceStatus.GAME_OVER -> onBackToGarage()
        }
    }

    // 60 FPS frame-locked simulation loop
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos) / 1_000_000_000f
                    onTick(dt)
                }
                lastNanos = frameNanos
            }
        }
    }

    // Optional accelerometer tilt steering
    val tiltManager = remember(context) {
        TiltSteeringManager(context, onTiltSteer)
    }
    DisposableEffect(settings.controlMode, state.status) {
        if (settings.controlMode == ControlMode.TILT && state.status == RaceStatus.RUNNING) {
            tiltManager.start()
        } else {
            tiltManager.stop()
        }
        onDispose { tiltManager.stop() }
    }

    val shakeX = if (state.screenShake > 0f) {
        sin(state.screenShake * 55f) * 18f * state.screenShake
    } else 0f
    val shakeY = if (state.screenShake > 0f) {
        sin(state.screenShake * 70f) * 12f * state.screenShake
    } else 0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .graphicsLayer {
                translationX = shakeX
                translationY = shakeY
            }
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // 1. Full-screen 60fps Road, Traffic & Supercar Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRaceWorld(state)
        }

        // 2. Floating Callouts (Near Miss, Pickup, Impact)
        state.callouts.forEach { callout ->
            val roadWidth = (screenW * 0.76f).coerceAtMost(720f)
            val roadCenter = screenW * 0.5f
            val px = roadCenter + callout.xNormalized * (roadWidth * 0.5f)
            val py = callout.yNormalized * screenH
            Text(
                text = callout.text,
                style = MaterialTheme.typography.titleLarge,
                color = callout.color.copy(alpha = callout.life.coerceIn(0f, 1f)),
                modifier = Modifier.offset {
                    IntOffset((px - 80f).roundToInt(), py.roundToInt())
                }
            )
        }

        // 3. Top Racing Telemetry HUD
        TopRaceHud(
            state = state,
            isMuted = settings.isMuted,
            onToggleMute = onToggleMute,
            onPause = onPause,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // 4. Bottom Instrument Cluster + Ergonomic Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InstrumentClusterBar(
                state = state,
                modifier = Modifier.widthIn(max = 560.dp)
            )

            RaceControlsBar(
                controlMode = settings.controlMode,
                nitroFuel = state.nitroFuel,
                isNitroActive = state.isNitroActive,
                isBraking = state.isBraking,
                isHornActive = state.isHornActive,
                onSteerLeft = onSteerLeft,
                onSteerRight = onSteerRight,
                onBrake = onBrake,
                onNitro = onNitro,
                onHorn = onHorn,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // 5. Countdown Overlay (3 - 2 - 1)
        AnimatedVisibility(
            visible = state.status == RaceStatus.COUNTDOWN,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = CarbonBlack.copy(alpha = 0.78f),
                    border = BorderStroke(3.dp, NitroOrange)
                ) {
                    Text(
                        text = state.countdownValue.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                        color = NitroOrange,
                        modifier = Modifier.padding(horizontal = 42.dp, vertical = 22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ENGINES READY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }

        // 6. Pause Modal
        if (state.status == RaceStatus.PAUSED) {
            PauseOverlay(
                score = state.score,
                distanceKm = state.distanceKm,
                onResume = onResume,
                onRestart = onRestart,
                onBackToGarage = onBackToGarage
            )
        }

        // 7. Game Over Modal
        if (state.status == RaceStatus.GAME_OVER) {
            GameOverOverlay(
                state = state,
                onPlayAgain = onRestart,
                onBackToGarage = onBackToGarage
            )
        }
    }
}

@Composable
private fun TopRaceHud(
    state: RaceUiState,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Score & Distance Pill
        Surface(
            color = CarbonBlack.copy(alpha = 0.78f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SteelBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.score_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.score.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = TurboGold
                        )
                        if (state.comboMultiplier > 1) {
                            Text(
                                text = "${state.comboMultiplier}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = ShieldEmerald
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.distance_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = String.format("%.2f KM", state.distanceKm),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        // Shield Integrity Hearts + Quick Audio & Pause
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = CarbonBlack.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SteelBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(state.maxShieldHits) { i ->
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Car integrity",
                            tint = if (i < state.shieldHits) RedlineCrimson else SteelBorder,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CarbonBlack.copy(alpha = 0.8f))
                    .border(1.dp, SteelBorder, CircleShape)
                    .testTag("race_mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(R.string.mute_desc),
                    tint = if (isMuted) RedlineCrimson else ElectricCyan
                )
            }

            IconButton(
                onClick = onPause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CarbonBlack.copy(alpha = 0.8f))
                    .border(1.dp, SteelBorder, CircleShape)
                    .testTag("race_pause_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.pause_desc),
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun InstrumentClusterBar(
    state: RaceUiState,
    modifier: Modifier = Modifier
) {
    val rpmFraction = ((state.rpm - 900f) / 8000f).coerceIn(0f, 1f)
    val nitroFraction = (state.nitroFuel / 100f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CarbonBlack.copy(alpha = 0.82f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SteelBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (state.isNitroActive) ElectricCyan else NitroOrange,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "G${state.gear}",
                            style = MaterialTheme.typography.labelLarge,
                            color = CarbonBlack,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${state.speedKmh.toInt()} KM/H",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (state.isNitroActive) ElectricCyan else TextPrimary
                    )
                }

                Text(
                    text = "NITRO ${state.nitroFuel.toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
            }

            // Dual Meter: RPM (Top) + Nitro (Bottom)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AsphaltSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rpmFraction.coerceAtLeast(0.05f))
                            .height(7.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ShieldEmerald, TurboGold, RedlineCrimson)
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AsphaltSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(nitroFraction)
                            .height(7.dp)
                            .background(ElectricCyan)
                    )
                }
            }
        }
    }
}

@Composable
private fun RaceControlsBar(
    controlMode: ControlMode,
    nitroFuel: Float,
    isNitroActive: Boolean,
    isBraking: Boolean,
    isHornActive: Boolean,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit,
    onBrake: (Boolean) -> Unit,
    onNitro: (Boolean) -> Unit,
    onHorn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Touch Steering Pads or Tilt Indicator
        if (controlMode == ControlMode.TOUCH) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldActionPad(
                    label = null,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.steer_left_desc),
                    accentColor = TextPrimary,
                    onHoldChanged = onSteerLeft,
                    modifier = Modifier
                        .size(width = 76.dp, height = 66.dp)
                        .testTag("steer_left_button")
                )
                HoldActionPad(
                    label = null,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.steer_right_desc),
                    accentColor = TextPrimary,
                    onHoldChanged = onSteerRight,
                    modifier = Modifier
                        .size(width = 76.dp, height = 66.dp)
                        .testTag("steer_right_button")
                )
            }
        } else {
            Surface(
                color = CarbonBlack.copy(alpha = 0.78f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "TILT PHONE TO STEER",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                )
            }
        }

        // Right Side: Horn + Brake + Nitro
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldActionPad(
                label = stringResource(R.string.horn_label),
                icon = Icons.Filled.Campaign,
                contentDescription = stringResource(R.string.horn_label),
                accentColor = TurboGold,
                forcePressed = isHornActive,
                onHoldChanged = onHorn,
                modifier = Modifier
                    .size(width = 62.dp, height = 66.dp)
                    .testTag("race_horn_button")
            )
            HoldActionPad(
                label = stringResource(R.string.brake_label),
                icon = Icons.Filled.Warning,
                contentDescription = stringResource(R.string.brake_label),
                accentColor = RedlineCrimson,
                forcePressed = isBraking,
                onHoldChanged = onBrake,
                modifier = Modifier
                    .size(width = 66.dp, height = 66.dp)
                    .testTag("race_brake_button")
            )
            HoldActionPad(
                label = stringResource(R.string.nitro_label),
                icon = Icons.Filled.Bolt,
                contentDescription = stringResource(R.string.nitro_label),
                accentColor = if (nitroFuel > 8f) ElectricCyan else TextMuted,
                forcePressed = isNitroActive,
                onHoldChanged = onNitro,
                modifier = Modifier
                    .size(width = 76.dp, height = 66.dp)
                    .testTag("race_nitro_button")
            )
        }
    }
}

@Composable
private fun HoldActionPad(
    label: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accentColor: Color,
    forcePressed: Boolean = false,
    onHoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var localPressed by remember { mutableStateOf(false) }
    val active = localPressed || forcePressed

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) accentColor.copy(alpha = 0.34f) else CarbonBlack.copy(alpha = 0.82f)
            )
            .border(
                width = if (active) 2.dp else 1.5.dp,
                color = if (active) accentColor else SteelBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    localPressed = true
                    onHoldChanged(true)
                    waitForUpOrCancellation()
                    localPressed = false
                    onHoldChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accentColor,
                modifier = Modifier.size(if (label == null) 34.dp else 24.dp)
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    score: Int,
    distanceKm: Float,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onBackToGarage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
            border = BorderStroke(1.5.dp, SteelBorder)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.paused_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Text(
                    text = "SCORE $score  •  ${String.format("%.2f KM", distanceKm)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TurboGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resume_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NitroOrange)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.resume_race), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pause_restart_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricCyan)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restart_race), color = ElectricCyan)
                }
                OutlinedButton(
                    onClick = onBackToGarage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pause_garage_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back_to_garage), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(
    state: RaceUiState,
    onPlayAgain: () -> Unit,
    onBackToGarage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
            border = BorderStroke(2.dp, RedlineCrimson)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.game_over_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = RedlineCrimson
                )

                Surface(
                    color = ElevatedCarbon,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatLine(stringResource(R.string.score_label), "${state.score}", TurboGold)
                        TelemetryStatLine(stringResource(R.string.best_label), "${state.sessionBestScore}", ShieldEmerald)
                        TelemetryStatLine(stringResource(R.string.top_speed_label), "${state.topSpeedReachedKmh.toInt()} KM/H", ElectricCyan)
                        TelemetryStatLine(stringResource(R.string.distance_label), String.format("%.2f KM", state.distanceKm), TextPrimary)
                        TelemetryStatLine(stringResource(R.string.near_miss_label), "${state.nearMissCount}", NitroOrange)
                    }
                }

                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("play_again_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NitroOrange)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play_again), style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onBackToGarage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("gameover_garage_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back_to_garage), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}
