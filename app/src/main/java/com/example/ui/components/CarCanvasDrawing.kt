package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.model.CarSpec
import com.example.model.PickupItem
import com.example.model.PickupType
import com.example.model.RaceUiState
import com.example.model.TrackTheme
import com.example.model.TrafficCar
import com.example.model.TrafficType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NitroOrange
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TurboGold
import com.example.viewmodel.RacingViewModel
import kotlin.math.sin

fun DrawScope.drawRaceWorld(state: RaceUiState) {
    val w = size.width
    val h = size.height
    val roadWidth = (w * 0.76f).coerceAtMost(720f)
    val roadLeft = (w - roadWidth) * 0.5f
    val roadRight = roadLeft + roadWidth
    val roadHalfW = roadWidth * 0.5f
    val roadCenter = w * 0.5f

    val track = state.track

    // 1. Shoulders & Track Atmosphere
    drawRect(color = track.shoulderColor, size = size)

    // Subtle track edge glow
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                track.accentGlowColor.copy(alpha = 0.22f),
                Color.Transparent
            ),
            startX = roadLeft - 48f,
            endX = roadRight + 48f
        ),
        size = size
    )

    // 2. Main Asphalt Road
    drawRect(
        color = track.roadColor,
        topLeft = Offset(roadLeft, 0f),
        size = Size(roadWidth, h)
    )

    // Center asphalt sheen
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.04f),
                Color.Black.copy(alpha = 0.25f)
            ),
            startX = roadLeft,
            endX = roadRight
        ),
        topLeft = Offset(roadLeft, 0f),
        size = Size(roadWidth, h)
    )

    // 3. Alternating Rumble Curbs on Left & Right Edges
    val curbWidth = (roadWidth * 0.045f).coerceAtLeast(10f)
    val segmentH = h / 12f
    val offsetPx = state.roadOffset * segmentH * 2f
    var idx = -2
    var yPos = -segmentH * 2f + offsetPx
    while (yPos < h + segmentH) {
        val isA = (idx % 2 == 0)
        val c = if (isA) track.curbColorA else track.curbColorB
        drawRect(
            color = c,
            topLeft = Offset(roadLeft - curbWidth, yPos),
            size = Size(curbWidth, segmentH + 1f)
        )
        drawRect(
            color = c,
            topLeft = Offset(roadRight, yPos),
            size = Size(curbWidth, segmentH + 1f)
        )

        // Roadside light poles / markers every 2 segments
        if (idx % 3 == 0) {
            val poleColor = if (track.isNight) track.accentGlowColor else Color(0xFF94A3B8)
            drawCircle(
                color = poleColor.copy(alpha = 0.35f),
                radius = 18f,
                center = Offset(roadLeft - curbWidth - 20f, yPos + segmentH * 0.5f)
            )
            drawCircle(
                color = poleColor,
                radius = 5f,
                center = Offset(roadLeft - curbWidth - 20f, yPos + segmentH * 0.5f)
            )
            drawCircle(
                color = poleColor.copy(alpha = 0.35f),
                radius = 18f,
                center = Offset(roadRight + curbWidth + 20f, yPos + segmentH * 0.5f)
            )
            drawCircle(
                color = poleColor,
                radius = 5f,
                center = Offset(roadRight + curbWidth + 20f, yPos + segmentH * 0.5f)
            )
        }
        yPos += segmentH
        idx++
    }

    // 4. Lane Divider Dashes (4 lanes -> 3 dividers)
    val dashPeriod = h / 8f
    val dashOffsetPx = state.roadOffset * dashPeriod
    val laneDividers = floatArrayOf(-0.48f, 0f, 0.48f)
    for (divNorm in laneDividers) {
        val x = roadCenter + divNorm * roadHalfW
        val isCenter = divNorm == 0f
        val lineW = if (isCenter) 5f else 3.5f
        val lineCol = if (isCenter) track.laneLineColor.copy(alpha = 0.85f) else track.laneLineColor.copy(alpha = 0.55f)
        var dy = -dashPeriod + dashOffsetPx
        while (dy < h + dashPeriod) {
            drawRoundRect(
                color = lineCol,
                topLeft = Offset(x - lineW * 0.5f, dy),
                size = Size(lineW, dashPeriod * 0.48f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            dy += dashPeriod
        }
    }

    // 5. Tire Skid Marks
    for (skid in state.skidMarks) {
        val sx = roadCenter + skid.xNormalized * roadHalfW
        val sy = skid.yNormalized * h
        drawRect(
            color = Color.Black.copy(alpha = skid.alpha * 0.6f),
            topLeft = Offset(sx - 4f, sy - 14f),
            size = Size(8f, 28f)
        )
    }

    // 6. Pickups (Nitro & Coins)
    for (pickup in state.pickups) {
        drawPickup(
            pickup = pickup,
            center = Offset(roadCenter + pickup.xNormalized * roadHalfW, pickup.yNormalized * h),
            unitScale = roadWidth * 0.055f,
            pulse = state.policeFlashPhase
        )
    }

    // 7. Traffic Vehicles
    for (car in state.traffic) {
        val cx = roadCenter + car.xNormalized * roadHalfW
        val cy = car.yNormalized * h
        val cw = car.widthRatio * roadHalfW * 2f
        val ch = car.heightRatio * h
        drawTrafficVehicle(
            car = car,
            center = Offset(cx, cy),
            carSize = Size(cw, ch),
            isNight = track.isNight,
            flashPhase = state.policeFlashPhase
        )
    }

    // 8. Particles (Exhaust Flames & Crash Sparks)
    for (pt in state.particles) {
        val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        val px = roadCenter + pt.x * roadHalfW
        val py = pt.y * h
        drawCircle(
            color = pt.color.copy(alpha = alpha * 0.9f),
            radius = (roadWidth * 0.014f) * pt.size * (0.5f + 0.5f * alpha),
            center = Offset(px, py)
        )
    }

    // 9. Player Supercar
    val blinkSkip = state.invulnerableTimer > 0f && ((state.invulnerableTimer * 16f).toInt() % 2 == 0)
    if (!blinkSkip) {
        val playerCx = roadCenter + state.playerX * roadHalfW
        val playerCy = RacingViewModel.PLAYER_Y * h
        val playerW = RacingViewModel.PLAYER_HALF_W * 2f * roadHalfW
        val playerH = RacingViewModel.PLAYER_HALF_H * 2f * h

        rotate(degrees = state.playerTiltDeg, pivot = Offset(playerCx, playerCy)) {
            drawPlayerSupercar(
                car = state.car,
                center = Offset(playerCx, playerCy),
                carSize = Size(playerW, playerH),
                isNitro = state.isNitroActive,
                isBraking = state.isBraking,
                isNight = track.isNight,
                flamePulse = state.policeFlashPhase
            )
        }
    }

    // 10. High-speed motion lines (> 210 km/h) or Nitro tunnel effect
    if (state.speedKmh > 210f || state.isNitroActive) {
        val intensity = if (state.isNitroActive) 0.55f else ((state.speedKmh - 210f) / 150f).coerceIn(0f, 0.35f)
        val streakColor = if (state.isNitroActive) ElectricCyan else Color.White
        for (i in 0 until 8) {
            val sideX = if (i % 2 == 0) roadLeft + (i * 14f) else roadRight - (i * 14f)
            val sy = ((state.roadOffset * 2.5f + i * 0.17f) % 1f) * h
            drawLine(
                color = streakColor.copy(alpha = intensity),
                start = Offset(sideX, sy),
                end = Offset(sideX, sy + 95f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }
    }

    // 11. Storm Rain Overlay
    if (track.isRainy) {
        val dropCount = 34
        for (i in 0 until dropCount) {
            val rx = ((i * 73) % 100) / 100f * w
            val ry = (((i * 29) / 100f + state.rainPhase) % 1f) * h
            drawLine(
                color = Color(0xFF93C5FD).copy(alpha = 0.25f),
                start = Offset(rx, ry),
                end = Offset(rx - 8f, ry + 34f),
                strokeWidth = 1.6f
            )
        }
    }
}

fun DrawScope.drawPlayerSupercar(
    car: CarSpec,
    center: Offset,
    carSize: Size,
    isNitro: Boolean,
    isBraking: Boolean,
    isNight: Boolean,
    flamePulse: Float
) {
    val left = center.x - carSize.width * 0.5f
    val top = center.y - carSize.height * 0.5f
    val w = carSize.width
    val h = carSize.height

    // Headlight beam cones
    if (isNight) {
        val beamH = h * 2.1f
        val leftBeam = Path().apply {
            moveTo(left + w * 0.18f, top + 4f)
            lineTo(left - w * 0.22f, top - beamH)
            lineTo(left + w * 0.36f, top - beamH)
            close()
        }
        val rightBeam = Path().apply {
            moveTo(left + w * 0.82f, top + 4f)
            lineTo(left + w * 0.64f, top - beamH)
            lineTo(left + w * 1.22f, top - beamH)
            close()
        }
        val beamBrush = Brush.verticalGradient(
            colors = listOf(Color(0x44FFFDE7), Color.Transparent),
            startY = top,
            endY = top - beamH
        )
        drawPath(leftBeam, brush = beamBrush)
        drawPath(rightBeam, brush = beamBrush)
    }

    // Underglow aura
    drawRoundRect(
        color = (if (isNitro) ElectricCyan else car.primaryColor).copy(alpha = if (isNitro) 0.35f else 0.18f),
        topLeft = Offset(left - 6f, top - 4f),
        size = Size(w + 12f, h + 8f),
        cornerRadius = CornerRadius(w * 0.32f, w * 0.32f)
    )

    // Wheels
    val wheelW = w * 0.14f
    val wheelH = h * 0.20f
    val wheelColor = Color(0xFF090B0E)
    drawRoundRect(wheelColor, Offset(left - wheelW * 0.35f, top + h * 0.15f), Size(wheelW, wheelH), CornerRadius(4f, 4f))
    drawRoundRect(wheelColor, Offset(left + w - wheelW * 0.65f, top + h * 0.15f), Size(wheelW, wheelH), CornerRadius(4f, 4f))
    drawRoundRect(wheelColor, Offset(left - wheelW * 0.35f, top + h * 0.68f), Size(wheelW, wheelH), CornerRadius(4f, 4f))
    drawRoundRect(wheelColor, Offset(left + w - wheelW * 0.65f, top + h * 0.68f), Size(wheelW, wheelH), CornerRadius(4f, 4f))

    // Main sculpted supercar chassis
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(car.primaryColor, car.primaryColor.copy(alpha = 0.82f), car.secondaryColor),
            startY = top,
            endY = top + h
        ),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.34f, h * 0.16f)
    )

    // Center Racing Stripes
    drawRect(
        color = car.accentColor.copy(alpha = 0.88f),
        topLeft = Offset(center.x - w * 0.08f, top + h * 0.04f),
        size = Size(w * 0.06f, h * 0.90f)
    )
    drawRect(
        color = car.accentColor.copy(alpha = 0.88f),
        topLeft = Offset(center.x + w * 0.02f, top + h * 0.04f),
        size = Size(w * 0.06f, h * 0.90f)
    )

    // Cockpit / Windshield Canopy
    drawRoundRect(
        color = Color(0xFF0B1220),
        topLeft = Offset(left + w * 0.17f, top + h * 0.25f),
        size = Size(w * 0.66f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
    )
    // Windshield glass highlight
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(left + w * 0.25f, top + h * 0.31f),
        end = Offset(left + w * 0.52f, top + h * 0.31f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Carbon Fiber Rear Wing / Spoiler
    drawRoundRect(
        color = Color(0xFF111827),
        topLeft = Offset(left + w * 0.04f, top + h * 0.86f),
        size = Size(w * 0.92f, h * 0.10f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // Front LED Headlights
    drawRoundRect(
        color = Color(0xFFFFFDE7),
        topLeft = Offset(left + w * 0.12f, top + h * 0.04f),
        size = Size(w * 0.18f, h * 0.06f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = Color(0xFFFFFDE7),
        topLeft = Offset(left + w * 0.70f, top + h * 0.04f),
        size = Size(w * 0.18f, h * 0.06f),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // Rear Taillights / Brake Lights
    val tailAlpha = if (isBraking) 1.0f else 0.7f
    val tailGlowH = if (isBraking) h * 0.08f else h * 0.05f
    drawRoundRect(
        color = RedlineCrimson.copy(alpha = tailAlpha),
        topLeft = Offset(left + w * 0.10f, top + h - tailGlowH),
        size = Size(w * 0.26f, tailGlowH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = RedlineCrimson.copy(alpha = tailAlpha),
        topLeft = Offset(left + w * 0.64f, top + h - tailGlowH),
        size = Size(w * 0.26f, tailGlowH),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // Twin Nitro Exhaust Flames
    if (isNitro) {
        val flicker = 0.85f + 0.30f * sin(flamePulse * 5f)
        val flameLen = h * 0.36f * flicker
        val pipeLeftX = left + w * 0.30f
        val pipeRightX = left + w * 0.70f
        val pipeY = top + h + 2f

        for (px in listOf(pipeLeftX, pipeRightX)) {
            val outerFlame = Path().apply {
                moveTo(px - w * 0.08f, pipeY)
                lineTo(px + w * 0.08f, pipeY)
                lineTo(px, pipeY + flameLen)
                close()
            }
            drawPath(outerFlame, color = ElectricCyan)
            val innerFlame = Path().apply {
                moveTo(px - w * 0.04f, pipeY)
                lineTo(px + w * 0.04f, pipeY)
                lineTo(px, pipeY + flameLen * 0.6f)
                close()
            }
            drawPath(innerFlame, color = Color.White)
        }
    }
}

private fun DrawScope.drawTrafficVehicle(
    car: TrafficCar,
    center: Offset,
    carSize: Size,
    isNight: Boolean,
    flashPhase: Float
) {
    val left = center.x - carSize.width * 0.5f
    val top = center.y - carSize.height * 0.5f
    val w = carSize.width
    val h = carSize.height

    // Soft shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(left + 3f, top + 4f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
    )

    // Body
    drawRoundRect(
        color = car.bodyColor,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
    )

    if (car.type == TrafficType.TRUCK) {
        // Cargo trailer roof lines
        drawRoundRect(
            color = Color.White.copy(alpha = 0.14f),
            topLeft = Offset(left + w * 0.08f, top + h * 0.22f),
            size = Size(w * 0.84f, h * 0.72f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Cab windshield
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(left + w * 0.12f, top + h * 0.06f),
            size = Size(w * 0.76f, h * 0.12f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    } else {
        // Windshield & Rear glass
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(left + w * 0.16f, top + h * 0.26f),
            size = Size(w * 0.68f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
        )
    }

    // Police lightbar
    if (car.type == TrafficType.POLICE) {
        // White police doors/roof accent
        drawRect(
            color = Color.White,
            topLeft = Offset(left + w * 0.15f, top + h * 0.40f),
            size = Size(w * 0.70f, h * 0.18f)
        )
        val isRedPhase = sin(flashPhase) > 0f
        val leftFlash = if (isRedPhase) RedlineCrimson else ElectricCyan
        val rightFlash = if (isRedPhase) ElectricCyan else RedlineCrimson
        drawCircle(leftFlash.copy(alpha = 0.45f), radius = w * 0.25f, center = Offset(left + w * 0.30f, top + h * 0.49f))
        drawCircle(rightFlash.copy(alpha = 0.45f), radius = w * 0.25f, center = Offset(left + w * 0.70f, top + h * 0.49f))
        drawRect(leftFlash, Offset(left + w * 0.22f, top + h * 0.45f), Size(w * 0.24f, h * 0.08f))
        drawRect(rightFlash, Offset(left + w * 0.54f, top + h * 0.45f), Size(w * 0.24f, h * 0.08f))
    }

    // Taillights
    val tailCol = if (isNight) RedlineCrimson else RedlineCrimson.copy(alpha = 0.85f)
    drawRect(tailCol, Offset(left + w * 0.10f, top + h - 5f), Size(w * 0.22f, 5f))
    drawRect(tailCol, Offset(left + w * 0.68f, top + h - 5f), Size(w * 0.22f, 5f))
}

private fun DrawScope.drawPickup(
    pickup: PickupItem,
    center: Offset,
    unitScale: Float,
    pulse: Float
) {
    val scale = 1f + 0.10f * sin(pulse * 1.4f)
    val radius = unitScale * scale

    when (pickup.type) {
        PickupType.NITRO -> {
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.28f),
                radius = radius * 1.45f,
                center = center
            )
            drawRoundRect(
                color = ElectricCyan,
                topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.85f),
                size = Size(radius * 1.1f, radius * 1.7f),
                cornerRadius = CornerRadius(radius * 0.35f, radius * 0.35f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x - radius * 0.25f, center.y - radius * 1.05f),
                size = Size(radius * 0.5f, radius * 0.28f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
        PickupType.COIN -> {
            drawCircle(
                color = TurboGold.copy(alpha = 0.25f),
                radius = radius * 1.35f,
                center = center
            )
            drawCircle(
                color = TurboGold,
                radius = radius * 0.85f,
                center = center
            )
            drawCircle(
                color = Color(0xFF92400E),
                radius = radius * 0.55f,
                center = center,
                style = Stroke(width = 3f)
            )
        }
    }
}
