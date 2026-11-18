package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CarSpec
import com.example.model.ControlMode
import com.example.model.PlayerSettings
import com.example.model.RacingCatalog
import com.example.model.TrackTheme
import com.example.ui.components.drawPlayerSupercar
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
import kotlin.math.sin

@Composable
fun GarageScreen(
    settings: PlayerSettings,
    garageRpm: Float,
    isRevving: Boolean,
    sessionBestScore: Int,
    onSelectCar: (String) -> Unit,
    onSelectTrack: (String) -> Unit,
    onRevChanged: (Boolean) -> Unit,
    onHornClick: () -> Unit,
    onToggleLoudBoost: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onControlModeChange: (ControlMode) -> Unit,
    onStartRace: () -> Unit
) {
    val selectedCar = RacingCatalog.carById(settings.selectedCarId)
    val selectedTrack = RacingCatalog.trackById(settings.selectedTrackId)
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "garage_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
    ) {
        val isWide = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Art Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWide) 220.dp else 190.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_menu_hero),
                    contentDescription = "Turbo Roar supercar hero art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CarbonBlack.copy(alpha = 0.35f),
                                    CarbonBlack.copy(alpha = 0.65f),
                                    CarbonBlack
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = NitroOrange.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NitroOrange.copy(alpha = 0.65f))
                            ) {
                                Text(
                                    text = stringResource(R.string.tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NitroOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (sessionBestScore > 0) {
                                Surface(
                                    color = TurboGold.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TurboGold.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "BEST $sessionBestScore",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurboGold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AsphaltSurface.copy(alpha = 0.85f))
                            .border(1.dp, SteelBorder, CircleShape)
                            .testTag("garage_mute_button")
                    ) {
                        Icon(
                            imageVector = if (settings.isMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = stringResource(R.string.mute_desc),
                            tint = if (settings.isMuted) RedlineCrimson else ElectricCyan
                        )
                    }
                }
            }

            // Main Adaptive Container
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Supercar Sound & Revving Stage
                CarPreviewStage(
                    car = selectedCar,
                    rpm = garageRpm,
                    isRevving = isRevving,
                    pulsePhase = pulsePhase,
                    onRevChanged = onRevChanged,
                    onHornClick = onHornClick
                )

                // Machine Selector
                Text(
                    text = stringResource(R.string.garage_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RacingCatalog.cars.forEach { car ->
                        CarSelectionCard(
                            car = car,
                            selected = car.id == selectedCar.id,
                            onClick = { onSelectCar(car.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Circuit Track Selector
                Text(
                    text = stringResource(R.string.track_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RacingCatalog.tracks.forEach { track ->
                        TrackChipCard(
                            track = track,
                            selected = track.id == selectedTrack.id,
                            onClick = { onSelectTrack(track.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Loud Sound & Control Settings Card
                AudioAndControlsCard(
                    settings = settings,
                    onToggleLoudBoost = onToggleLoudBoost,
                    onVolumeChange = onVolumeChange,
                    onControlModeChange = onControlModeChange
                )

                // Big Start Race Button
                Button(
                    onClick = onStartRace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("start_race_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NitroOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.start_race),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CarPreviewStage(
    car: CarSpec,
    rpm: Float,
    isRevving: Boolean,
    pulsePhase: Float,
    onRevChanged: (Boolean) -> Unit,
    onHornClick: () -> Unit
) {
    val rpmFraction = ((rpm - 850f) / 7800f).coerceIn(0f, 1f)
    val vibX = if (isRevving) sin(pulsePhase * 12f) * 3.5f else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
        border = BorderStroke(
            1.dp,
            if (isRevving) NitroOrange else SteelBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = car.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = car.engineClass,
                        style = MaterialTheme.typography.labelSmall,
                        color = car.primaryColor
                    )
                }

                // Live RPM Badge
                Surface(
                    color = if (rpm > 6800f) RedlineCrimson.copy(alpha = 0.25f) else ElevatedCarbon,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (rpm > 6800f) RedlineCrimson else SteelBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = if (rpm > 6800f) RedlineCrimson else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${rpm.toInt()} RPM",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Car Canvas Preview + Stat Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 116.dp, height = 136.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    car.primaryColor.copy(alpha = if (isRevving) 0.28f else 0.14f),
                                    CarbonBlack
                                )
                            )
                        )
                        .border(1.dp, SteelBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(degrees = 0f, pivot = center) {
                            drawPlayerSupercar(
                                car = car,
                                center = Offset(center.x + vibX, center.y),
                                carSize = Size(size.width * 0.44f, size.height * 0.68f),
                                isNitro = isRevving,
                                isBraking = false,
                                isNight = true,
                                flamePulse = pulsePhase
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBarRow("TOP SPEED", "${car.topSpeedKmh.toInt()} KM/H", car.speedStat, ElectricCyan)
                    StatBarRow("ACCEL", "${(car.accelStat * 100).toInt()}", car.accelStat, NitroOrange)
                    StatBarRow("HANDLING", "${(car.handlingStat * 100).toInt()}", car.handlingStat, ShieldEmerald)

                    // Live RPM Tachometer Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CarbonBlack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(rpmFraction.coerceAtLeast(0.06f))
                                .height(8.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(ElectricCyan, TurboGold, RedlineCrimson)
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Rev Engine & Horn Test Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val revBg by animateColorAsState(
                    targetValue = if (isRevving) RedlineCrimson else ElevatedCarbon,
                    label = "rev_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(revBg)
                        .border(
                            1.5.dp,
                            if (isRevving) NitroOrange else car.primaryColor.copy(alpha = 0.8f),
                            RoundedCornerShape(12.dp)
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onRevChanged(true)
                                waitForUpOrCancellation()
                                onRevChanged(false)
                            }
                        }
                        .testTag("rev_engine_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (isRevving) Color.White else NitroOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isRevving) "ROARING!" else "HOLD TO ${stringResource(R.string.rev_engine)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    onClick = onHornClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("garage_horn_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = ElevatedCarbon,
                    border = BorderStroke(1.5.dp, TurboGold.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = stringResource(R.string.horn_label),
                            tint = TurboGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.horn_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = TurboGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBarRow(
    label: String,
    valueText: String,
    progress: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CarbonBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.1f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun CarSelectionCard(
    car: CarSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("car_card_${car.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ElevatedCarbon else AsphaltSurface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) car.primaryColor else SteelBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(car.primaryColor)
                )
                Text(
                    text = car.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${car.topSpeedKmh.toInt()} KM/H",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 13.sp,
                color = car.primaryColor
            )
            Text(
                text = if (selected) "SELECTED" else "SELECT",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) ShieldEmerald else TextMuted
            )
        }
    }
}

@Composable
private fun TrackChipCard(
    track: TrackTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("track_card_${track.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ElevatedCarbon else AsphaltSurface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) track.accentGlowColor else SteelBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = track.accentGlowColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = track.subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioAndControlsCard(
    settings: PlayerSettings,
    onToggleLoudBoost: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onControlModeChange: (ControlMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
        border = BorderStroke(1.dp, SteelBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (settings.loudBoostEnabled) NitroOrange else TextSecondary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.sound_boost_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = if (settings.loudBoostEnabled) {
                                stringResource(R.string.sound_max_badge)
                            } else {
                                stringResource(R.string.sound_normal_badge)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.loudBoostEnabled) NitroOrange else TextMuted
                        )
                    }
                }
                Switch(
                    checked = settings.loudBoostEnabled,
                    onCheckedChange = onToggleLoudBoost,
                    modifier = Modifier.testTag("loud_boost_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NitroOrange
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "VOL ${(settings.masterVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(66.dp)
                )
                Slider(
                    value = settings.masterVolume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.controls_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlModePill(
                        label = stringResource(R.string.controls_touch),
                        icon = Icons.Filled.TouchApp,
                        selected = settings.controlMode == ControlMode.TOUCH,
                        onClick = { onControlModeChange(ControlMode.TOUCH) },
                        modifier = Modifier.testTag("control_touch_pill")
                    )
                    ControlModePill(
                        label = stringResource(R.string.controls_tilt),
                        icon = Icons.Filled.PhoneAndroid,
                        selected = settings.controlMode == ControlMode.TILT,
                        onClick = { onControlModeChange(ControlMode.TILT) },
                        modifier = Modifier.testTag("control_tilt_pill")
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ElectricCyan.copy(alpha = 0.2f) else ElevatedCarbon,
        border = BorderStroke(
            1.5.dp,
            if (selected) ElectricCyan else SteelBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricCyan else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                color = if (selected) ElectricCyan else TextSecondary
            )
        }
    }
}
