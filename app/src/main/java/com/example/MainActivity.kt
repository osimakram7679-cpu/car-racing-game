package com.example

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.GameScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.theme.TurboRoarTheme
import com.example.viewmodel.RacingViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RacingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route hardware volume buttons to game media volume for instant loud control
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            TurboRoarTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val raceState by viewModel.raceState.collectAsStateWithLifecycle()
                val garageRpm by viewModel.garageRevRpm.collectAsStateWithLifecycle()
                val isGarageRevving by viewModel.isGarageRevving.collectAsStateWithLifecycle()

                Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                    when (screen) {
                        GameScreen.GARAGE -> {
                            GarageScreen(
                                settings = settings,
                                garageRpm = garageRpm,
                                isRevving = isGarageRevving,
                                sessionBestScore = raceState.sessionBestScore,
                                onSelectCar = viewModel::selectCar,
                                onSelectTrack = viewModel::selectTrack,
                                onRevChanged = viewModel::setGarageRevving,
                                onHornClick = viewModel::blastHornOnce,
                                onToggleLoudBoost = viewModel::toggleLoudBoost,
                                onToggleMute = viewModel::toggleMute,
                                onVolumeChange = viewModel::setMasterVolume,
                                onControlModeChange = viewModel::setControlMode,
                                onStartRace = viewModel::startRace
                            )
                        }

                        GameScreen.RACING -> {
                            RaceScreen(
                                state = raceState,
                                settings = settings,
                                onTick = viewModel::tickRace,
                                onSteerLeft = viewModel::setSteerLeft,
                                onSteerRight = viewModel::setSteerRight,
                                onTiltSteer = viewModel::setTiltSteer,
                                onBrake = viewModel::setBrake,
                                onNitro = viewModel::setNitro,
                                onHorn = viewModel::setHorn,
                                onPause = viewModel::pauseRace,
                                onResume = viewModel::resumeRace,
                                onRestart = viewModel::startRace,
                                onBackToGarage = viewModel::returnToGarage,
                                onToggleMute = viewModel::toggleMute
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }
}
