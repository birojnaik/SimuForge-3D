package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.physics.SimulationUiState
import com.example.physics.SimulationViewModel
import com.example.physics.SolverMode
import com.example.physics.ViewportShadingMode
import com.example.ui.MathInspectorDialog
import com.example.ui.ScenePresetsPanel
import com.example.ui.SolverParametersPanel
import com.example.ui.TelemetryAndEnergyPanel
import com.example.ui.TimelineBakerBar
import com.example.ui.VfxKeyframesPanel
import com.example.ui.Viewport3DCanvas
import com.example.ui.theme.AmberClothSecondary
import com.example.ui.theme.CyanFluidPrimary
import com.example.ui.theme.EmeraldCoupledAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioBorderSubtle
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioPanelSurface
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.VioletGravityTertiary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SimuForgeSuiteApp()
            }
        }
    }
}

@Composable
fun SimuForgeSuiteApp(
    simViewModel: SimulationViewModel = viewModel()
) {
    val uiState by simViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedDockTab by rememberSaveable { mutableIntStateOf(0) }

    // Continuous 60Hz real-time mathematical solver loop
    LaunchedEffect(uiState.isRunning) {
        var lastNanos = 0L
        while (uiState.isRunning) {
            withFrameNanos { frameNanos ->
                val dt = if (lastNanos == 0L) {
                    1f / 60f
                } else {
                    ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.005f, 0.033f)
                }
                lastNanos = frameNanos
                simViewModel.advanceSimulationStep(dt)
            }
        }
    }

    // Real Hardware Accelerometer / Gravity Sensor Coupling when enabled
    DisposableEffect(uiState.deviceTiltGravityEnabled) {
        if (!uiState.deviceTiltGravityEnabled) {
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.size >= 3) {
                    simViewModel.updateDeviceSensorGravity(values[0], values[1], values[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (gravitySensor != null) {
            sensorManager?.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    BackHandler(enabled = uiState.showMathInspectorModal) {
        simViewModel.toggleMathInspectorModal()
    }

    if (uiState.showMathInspectorModal) {
        MathInspectorDialog(
            uiState = uiState,
            onDismiss = { simViewModel.toggleMathInspectorModal() }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val isWideScreen = maxWidth >= 700.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioTopHeaderBar(
                    uiState = uiState,
                    onToggleSensorGravity = { simViewModel.toggleDeviceTiltGravity() },
                    onOpenMathInspector = { simViewModel.toggleMathInspectorModal() }
                )

                SolverModeSelectorStrip(
                    selectedMode = uiState.solverMode,
                    onSelectMode = { simViewModel.selectSolverMode(it) }
                )

                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Viewport3DCanvas(
                                uiState = uiState,
                                onViewportGesture = simViewModel::handleViewportGesture,
                                onPinchZoom = { zoom -> simViewModel.orbitCamera(0f, 0f, zoom) },
                                onSelectTool = simViewModel::setToolMode,
                                onCycleShading = {
                                    val modes = ViewportShadingMode.entries
                                    val next = modes[(uiState.shadingMode.ordinal + 1) % modes.size]
                                    simViewModel.setShadingMode(next)
                                },
                                onResetCamera = simViewModel::resetCameraView,
                                onToggleGrid = simViewModel::toggleGridAndAxes,
                                modifier = Modifier.weight(1f)
                            )

                            TimelineBakerBar(
                                uiState = uiState,
                                onTogglePlay = simViewModel::togglePlayPause,
                                onStepBackward = simViewModel::stepSingleFrameBackward,
                                onStepForward = simViewModel::stepSingleFrameForward,
                                onReset = simViewModel::resetSimulation,
                                onShockwave = simViewModel::triggerShockwaveImpulse,
                                onScrubSlot = simViewModel::scrubToCacheSlot
                            )
                        }

                        StudioInspectorDock(
                            uiState = uiState,
                            selectedTab = selectedDockTab,
                            onSelectTab = { selectedDockTab = it },
                            simViewModel = simViewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Viewport3DCanvas(
                        uiState = uiState,
                        onViewportGesture = simViewModel::handleViewportGesture,
                        onPinchZoom = { zoom -> simViewModel.orbitCamera(0f, 0f, zoom) },
                        onSelectTool = simViewModel::setToolMode,
                        onCycleShading = {
                            val modes = ViewportShadingMode.entries
                            val next = modes[(uiState.shadingMode.ordinal + 1) % modes.size]
                            simViewModel.setShadingMode(next)
                        },
                        onResetCamera = simViewModel::resetCameraView,
                        onToggleGrid = simViewModel::toggleGridAndAxes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.52f)
                    )

                    TimelineBakerBar(
                        uiState = uiState,
                        onTogglePlay = simViewModel::togglePlayPause,
                        onStepBackward = simViewModel::stepSingleFrameBackward,
                        onStepForward = simViewModel::stepSingleFrameForward,
                        onReset = simViewModel::resetSimulation,
                        onShockwave = simViewModel::triggerShockwaveImpulse,
                        onScrubSlot = simViewModel::scrubToCacheSlot
                    )

                    StudioInspectorDock(
                        uiState = uiState,
                        selectedTab = selectedDockTab,
                        onSelectTab = { selectedDockTab = it },
                        simViewModel = simViewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.48f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioTopHeaderBar(
    uiState: SimulationUiState,
    onToggleSensorGravity: () -> Unit,
    onOpenMathInspector: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CyanFluidPrimary.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .border(1.dp, CyanFluidPrimary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = CyanFluidPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.suite_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryMuted
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hardware Gravity Sensor Tilt Coupling Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (uiState.deviceTiltGravityEnabled) EmeraldCoupledAccent.copy(alpha = 0.2f)
                        else StudioPanelSurface
                    )
                    .border(
                        1.dp,
                        if (uiState.deviceTiltGravityEnabled) EmeraldCoupledAccent else StudioBorderSubtle,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onToggleSensorGravity)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("btn_tilt_gravity"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = stringResource(R.string.action_sensor_gravity),
                    tint = if (uiState.deviceTiltGravityEnabled) EmeraldCoupledAccent else TextSecondaryMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.action_sensor_gravity),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.deviceTiltGravityEnabled) EmeraldCoupledAccent else TextSecondaryMuted
                )
            }

            // Mathematical PDE Inspector Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanFluidPrimary.copy(alpha = 0.16f))
                    .border(1.dp, CyanFluidPrimary, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenMathInspector)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("btn_math_inspector"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = stringResource(R.string.action_equations),
                    tint = CyanFluidPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "PDE Math",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanFluidPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SolverModeSelectorStrip(
    selectedMode: SolverMode,
    onSelectMode: (SolverMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SolverMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val accentColor = when (mode) {
                SolverMode.FLUID_SPH -> CyanFluidPrimary
                SolverMode.CLOTH_VERLET -> AmberClothSecondary
                SolverMode.GRAVITY_NBODY -> VioletGravityTertiary
                SolverMode.COUPLED_VFX -> EmeraldCoupledAccent
            }
            val icon = when (mode) {
                SolverMode.FLUID_SPH -> Icons.Default.WaterDrop
                SolverMode.CLOTH_VERLET -> Icons.Default.Air
                SolverMode.GRAVITY_NBODY -> Icons.Default.Public
                SolverMode.COUPLED_VFX -> Icons.Default.Hub
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accentColor.copy(alpha = 0.2f)
                        else StudioPanelSurface
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor else StudioBorderSubtle,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("solver_mode_${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = mode.title,
                    tint = if (selected) accentColor else TextSecondaryMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else TextSecondaryMuted
                )
            }
        }
    }
}

@Composable
private fun StudioInspectorDock(
    uiState: SimulationUiState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    simViewModel: SimulationViewModel,
    modifier: Modifier = Modifier
) {
    val tabLabels = listOf(
        stringResource(R.string.tab_parameters),
        stringResource(R.string.tab_presets),
        stringResource(R.string.tab_telemetry),
        stringResource(R.string.tab_keyframes)
    )

    Surface(
        modifier = modifier,
        color = StudioPanelSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Dock Tab Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioCardElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabLabels.forEachIndexed { idx, title ->
                    val active = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) CyanFluidPrimary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (active) CyanFluidPrimary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectTab(idx) }
                            .padding(vertical = 7.dp)
                            .testTag("dock_tab_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) CyanFluidPrimary else TextSecondaryMuted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Scrollable Dock Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                when (selectedTab) {
                    0 -> SolverParametersPanel(
                        uiState = uiState,
                        onTimeScaleChange = simViewModel::updateTimeScale,
                        onSubstepsChange = simViewModel::updateSubsteps,
                        onGravityYChange = simViewModel::updateGravityY,
                        onWindXChange = simViewModel::updateWindX,
                        onUpdateFluid = simViewModel::updateFluidParameters,
                        onUpdateCloth = simViewModel::updateClothParameters,
                        onUpdateGravity = simViewModel::updateGravityParameters
                    )
                    1 -> ScenePresetsPanel(
                        presets = simViewModel.presetsForMode(uiState.solverMode),
                        selectedPresetIndex = uiState.selectedPresetIndex,
                        onSelectPreset = simViewModel::loadPreset
                    )
                    2 -> TelemetryAndEnergyPanel(
                        uiState = uiState,
                        onOpenMathModal = simViewModel::toggleMathInspectorModal
                    )
                    3 -> VfxKeyframesPanel(
                        uiState = uiState,
                        onToggleAutomation = simViewModel::toggleKeyframeAutomation,
                        onAddKeyframe = simViewModel::addCurrentStateAsKeyframe,
                        onRemoveKeyframe = simViewModel::removeKeyframe
                    )
                }
            }
        }
    }
}
