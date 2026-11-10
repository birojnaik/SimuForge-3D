package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.physics.PresetSceneInfo
import com.example.physics.SimulationUiState
import com.example.physics.SolverMode
import com.example.ui.theme.AmberClothSecondary
import com.example.ui.theme.CrimsonStressAlert
import com.example.ui.theme.CyanFluidPrimary
import com.example.ui.theme.EmeraldCoupledAccent
import com.example.ui.theme.StudioBorderSubtle
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioPanelSurface
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.VioletGravityTertiary
import java.util.Locale

@Composable
fun TimelineBakerBar(
    uiState: SimulationUiState,
    onTogglePlay: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onReset: () -> Unit,
    onShockwave: () -> Unit,
    onScrubSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioPanelSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onStepBackward,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_step_backward")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Step Backward",
                            tint = TextSecondaryMuted
                        )
                    }
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(42.dp)
                            .background(CyanFluidPrimary, CircleShape)
                            .testTag("btn_play_pause")
                    ) {
                        Icon(
                            imageVector = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isRunning) "Pause Simulation" else "Play Simulation",
                            tint = StudioObsidian
                        )
                    }
                    IconButton(
                        onClick = onStepForward,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_step_forward")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Step Forward",
                            tint = TextSecondaryMuted
                        )
                    }
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_reset_solver")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Reset Solver",
                            tint = TextSecondaryMuted
                        )
                    }
                }

                // Cache Frame Readout Badge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isScrubbingCache) "CACHE SCRUBBING" else "REAL-TIME SOLVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isScrubbingCache) AmberClothSecondary else EmeraldCoupledAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Frame ${uiState.currentFrame?.frameIndex ?: 0} • Cache ${uiState.currentScrubSlot + 1}/${uiState.cachedFramesCount.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }

                // Shockwave Impulse Action Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AmberClothSecondary.copy(alpha = 0.18f))
                        .border(1.dp, AmberClothSecondary, RoundedCornerShape(12.dp))
                        .clickable(onClick = onShockwave)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("btn_inject_shockwave"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Inject Shockwave",
                        tint = AmberClothSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Impulse",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberClothSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.cachedFramesCount > 1) {
                Slider(
                    value = uiState.currentScrubSlot.toFloat(),
                    onValueChange = { onScrubSlot(it.toInt()) },
                    valueRange = 0f..(uiState.cachedFramesCount - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("slider_timeline_scrub"),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanFluidPrimary,
                        activeTrackColor = CyanFluidPrimary,
                        inactiveTrackColor = StudioCardElevated
                    )
                )
            }
        }
    }
}

@Composable
fun SolverParametersPanel(
    uiState: SimulationUiState,
    onTimeScaleChange: (Float) -> Unit,
    onSubstepsChange: (Int) -> Unit,
    onGravityYChange: (Float) -> Unit,
    onWindXChange: (Float) -> Unit,
    onUpdateFluid: (Float, Float, Float, Float, Float, Boolean) -> Unit,
    onUpdateCloth: (Float, Float, Int, Float, Boolean, Float) -> Unit,
    onUpdateGravity: (Float, Float, Boolean, Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Global Solver Integration & Environment Card
        Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "GLOBAL INTEGRATOR & ENVIRONMENT FIELDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanFluidPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                ParameterSliderRow(
                    label = "Time Scale (Δt Multiplier)",
                    valueText = String.format(Locale.US, "%.2fx", uiState.timeScale),
                    value = uiState.timeScale,
                    range = 0.2f..2.0f,
                    onValueChange = onTimeScaleChange,
                    testTag = "slider_time_scale"
                )
                ParameterSliderRow(
                    label = "Solver Substeps / Frame",
                    valueText = "${uiState.substeps}x",
                    value = uiState.substeps.toFloat(),
                    range = 1f..5f,
                    onValueChange = { onSubstepsChange(it.toInt()) },
                    testTag = "slider_substeps"
                )
                if (uiState.solverMode != SolverMode.GRAVITY_NBODY) {
                    ParameterSliderRow(
                        label = "Gravity Acceleration g_y",
                        valueText = String.format(Locale.US, "%.2f m/s²", uiState.gravityVector.y),
                        value = uiState.gravityVector.y,
                        range = -18.0f..8.0f,
                        onValueChange = onGravityYChange,
                        testTag = "slider_gravity_y"
                    )
                    ParameterSliderRow(
                        label = "Turbulent Wind Velocity w_x",
                        valueText = String.format(Locale.US, "%.2f m/s", uiState.windVector.x),
                        value = uiState.windVector.x,
                        range = -8.0f..8.0f,
                        onValueChange = onWindXChange,
                        testTag = "slider_wind_x"
                    )
                }
            }
        }

        // Mode-Specific Differential Equation Parameters
        if (uiState.solverMode == SolverMode.FLUID_SPH || uiState.solverMode == SolverMode.COUPLED_VFX) {
            Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "NAVIER-STOKES SPH FLUID KERNEL PARAMETERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanFluidPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ParameterSliderRow(
                        label = "Smoothing Kernel Radius (h)",
                        valueText = String.format(Locale.US, "%.2f m", uiState.fluidSmoothingRadius),
                        value = uiState.fluidSmoothingRadius,
                        range = 0.22f..0.48f,
                        onValueChange = {
                            onUpdateFluid(
                                it,
                                uiState.fluidGasStiffness,
                                uiState.fluidViscosity,
                                uiState.fluidRestitution,
                                uiState.fluidVortexStrength,
                                uiState.fluidContinuousJets
                            )
                        },
                        testTag = "slider_fluid_radius"
                    )
                    ParameterSliderRow(
                        label = "Tait Pressure Stiffness (k)",
                        valueText = String.format(Locale.US, "%.2f Pa", uiState.fluidGasStiffness),
                        value = uiState.fluidGasStiffness,
                        range = 0.5f..3.8f,
                        onValueChange = {
                            onUpdateFluid(
                                uiState.fluidSmoothingRadius,
                                it,
                                uiState.fluidViscosity,
                                uiState.fluidRestitution,
                                uiState.fluidVortexStrength,
                                uiState.fluidContinuousJets
                            )
                        },
                        testTag = "slider_fluid_stiffness"
                    )
                    ParameterSliderRow(
                        label = "Dynamic Viscosity (μ)",
                        valueText = String.format(Locale.US, "%.2f Pa·s", uiState.fluidViscosity),
                        value = uiState.fluidViscosity,
                        range = 0.02f..0.85f,
                        onValueChange = {
                            onUpdateFluid(
                                uiState.fluidSmoothingRadius,
                                uiState.fluidGasStiffness,
                                it,
                                uiState.fluidRestitution,
                                uiState.fluidVortexStrength,
                                uiState.fluidContinuousJets
                            )
                        },
                        testTag = "slider_fluid_viscosity"
                    )
                    ParameterSliderRow(
                        label = "Centrifugal Vortex Field (ω)",
                        valueText = String.format(Locale.US, "%.2f rad/s", uiState.fluidVortexStrength),
                        value = uiState.fluidVortexStrength,
                        range = 0.0f..4.0f,
                        onValueChange = {
                            onUpdateFluid(
                                uiState.fluidSmoothingRadius,
                                uiState.fluidGasStiffness,
                                uiState.fluidViscosity,
                                uiState.fluidRestitution,
                                it,
                                uiState.fluidContinuousJets
                            )
                        },
                        testTag = "slider_fluid_vortex"
                    )
                    ParameterSwitchRow(
                        label = "Continuous Hydro Jet Emitter",
                        checked = uiState.fluidContinuousJets,
                        onCheckedChange = {
                            onUpdateFluid(
                                uiState.fluidSmoothingRadius,
                                uiState.fluidGasStiffness,
                                uiState.fluidViscosity,
                                uiState.fluidRestitution,
                                uiState.fluidVortexStrength,
                                it
                            )
                        },
                        testTag = "switch_fluid_jets"
                    )
                }
            }
        }

        if (uiState.solverMode == SolverMode.CLOTH_VERLET || uiState.solverMode == SolverMode.COUPLED_VFX) {
            Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VERLET SPRING-MASS CLOTH & FRACTURE PARAMETERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberClothSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ParameterSliderRow(
                        label = "Structural Hooke Stiffness (k_s)",
                        valueText = String.format(Locale.US, "%.2f", uiState.clothStructuralStiffness),
                        value = uiState.clothStructuralStiffness,
                        range = 0.25f..1.0f,
                        onValueChange = {
                            onUpdateCloth(
                                it,
                                uiState.clothBendStiffness,
                                uiState.clothSolverIterations,
                                uiState.clothAerodynamicDrag,
                                uiState.clothTearable,
                                uiState.clothTearThreshold
                            )
                        },
                        testTag = "slider_cloth_stiffness"
                    )
                    ParameterSliderRow(
                        label = "Gauss-Seidel Relaxation Iterations",
                        valueText = "${uiState.clothSolverIterations} iters",
                        value = uiState.clothSolverIterations.toFloat(),
                        range = 2f..14f,
                        onValueChange = {
                            onUpdateCloth(
                                uiState.clothStructuralStiffness,
                                uiState.clothBendStiffness,
                                it.toInt(),
                                uiState.clothAerodynamicDrag,
                                uiState.clothTearable,
                                uiState.clothTearThreshold
                            )
                        },
                        testTag = "slider_cloth_iterations"
                    )
                    ParameterSliderRow(
                        label = "Aerodynamic Lift & Drag (C_d)",
                        valueText = String.format(Locale.US, "%.2f", uiState.clothAerodynamicDrag),
                        value = uiState.clothAerodynamicDrag,
                        range = 0.05f..1.4f,
                        onValueChange = {
                            onUpdateCloth(
                                uiState.clothStructuralStiffness,
                                uiState.clothBendStiffness,
                                uiState.clothSolverIterations,
                                it,
                                uiState.clothTearable,
                                uiState.clothTearThreshold
                            )
                        },
                        testTag = "slider_cloth_drag"
                    )
                    ParameterSwitchRow(
                        label = "Enable Mechanical Strain Fracture (Tearing)",
                        checked = uiState.clothTearable,
                        onCheckedChange = {
                            onUpdateCloth(
                                uiState.clothStructuralStiffness,
                                uiState.clothBendStiffness,
                                uiState.clothSolverIterations,
                                uiState.clothAerodynamicDrag,
                                it,
                                uiState.clothTearThreshold
                            )
                        },
                        testTag = "switch_cloth_tearable"
                    )
                }
            }
        }

        if (uiState.solverMode == SolverMode.GRAVITY_NBODY || uiState.solverMode == SolverMode.COUPLED_VFX) {
            Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SYMPLECTIC N-BODY GRAVITATIONAL PARAMETERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = VioletGravityTertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ParameterSliderRow(
                        label = "Gravitational Constant (G)",
                        valueText = String.format(Locale.US, "%.2f N·m²/kg²", uiState.gravityConstantG),
                        value = uiState.gravityConstantG,
                        range = 0.2f..2.4f,
                        onValueChange = {
                            onUpdateGravity(
                                it,
                                uiState.gravitySoftening,
                                uiState.gravityAccretionMerger,
                                uiState.gravityRelativisticPrecession
                            )
                        },
                        testTag = "slider_gravity_g"
                    )
                    ParameterSliderRow(
                        label = "Plummer Softening Length (ε)",
                        valueText = String.format(Locale.US, "%.2f", uiState.gravitySoftening),
                        value = uiState.gravitySoftening,
                        range = 0.05f..0.40f,
                        onValueChange = {
                            onUpdateGravity(
                                uiState.gravityConstantG,
                                it,
                                uiState.gravityAccretionMerger,
                                uiState.gravityRelativisticPrecession
                            )
                        },
                        testTag = "slider_gravity_softening"
                    )
                    ParameterSliderRow(
                        label = "Post-Newtonian Relativistic Precession",
                        valueText = String.format(Locale.US, "%.2f", uiState.gravityRelativisticPrecession),
                        value = uiState.gravityRelativisticPrecession,
                        range = 0.0f..0.5f,
                        onValueChange = {
                            onUpdateGravity(
                                uiState.gravityConstantG,
                                uiState.gravitySoftening,
                                uiState.gravityAccretionMerger,
                                it
                            )
                        },
                        testTag = "slider_gravity_precession"
                    )
                    ParameterSwitchRow(
                        label = "Inelastic Accretion Coalescence on Impact",
                        checked = uiState.gravityAccretionMerger,
                        onCheckedChange = {
                            onUpdateGravity(
                                uiState.gravityConstantG,
                                uiState.gravitySoftening,
                                it,
                                uiState.gravityRelativisticPrecession
                            )
                        },
                        testTag = "switch_gravity_accretion"
                    )
                }
            }
        }
    }
}

@Composable
fun ScenePresetsPanel(
    presets: List<PresetSceneInfo>,
    selectedPresetIndex: Int,
    onSelectPreset: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val selected = preset.index == selectedPresetIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPreset(preset.index) }
                    .testTag("preset_card_${preset.index}"),
                color = if (selected) CyanFluidPrimary.copy(alpha = 0.14f) else StudioCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) CyanFluidPrimary else StudioBorderSubtle
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = preset.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanFluidPrimary,
                                modifier = Modifier
                                    .background(
                                        CyanFluidPrimary.copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryAndEnergyPanel(
    uiState: SimulationUiState,
    onOpenMathModal: () -> Unit
) {
    val tel = uiState.currentFrame?.telemetry
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = EmeraldCoupledAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "REAL-TIME HAMILTONIAN ENERGY WAVEFORM",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldCoupledAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Inspect PDEs →",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanFluidPrimary,
                        modifier = Modifier
                            .clickable(onClick = onOpenMathModal)
                            .testTag("btn_open_pde_modal")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Real-time Energy History Sparkline Canvas
                val history = tel?.energyHistory ?: emptyList()
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .background(StudioObsidian, RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    if (history.size >= 2) {
                        val maxE = (history.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
                        val minE = (history.minOrNull() ?: 0f)
                        val span = (maxE - minE).coerceAtLeast(0.5f)
                        val path = Path()
                        history.forEachIndexed { idx, eVal ->
                            val x = (idx.toFloat() / (history.size - 1)) * size.width
                            val norm = ((eVal - minE) / span).coerceIn(0f, 1f)
                            val y = size.height - norm * size.height
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = CyanFluidPrimary,
                            style = Stroke(width = 2.5f)
                        )
                    } else {
                        drawLine(
                            color = TextSecondaryMuted,
                            start = Offset(0f, size.height * 0.5f),
                            end = Offset(size.width, size.height * 0.5f),
                            strokeWidth = 1.5f
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (tel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryMetricBox("Kinetic E_k", String.format(Locale.US, "%.2f J", tel.kineticEnergy), CyanFluidPrimary)
                        TelemetryMetricBox("Potential E_p", String.format(Locale.US, "%.2f J", tel.potentialEnergy), AmberClothSecondary)
                        TelemetryMetricBox("Momentum |p|", String.format(Locale.US, "%.2f N·s", tel.linearMomentumMag), EmeraldCoupledAccent)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryMetricBox("Peak Speed", String.format(Locale.US, "%.2f m/s", tel.maxSpeed), Color.White)
                        TelemetryMetricBox("CFL Stability", String.format(Locale.US, "%.3f", tel.cflNumber), if (tel.cflNumber < 1f) EmeraldCoupledAccent else CrimsonStressAlert)
                        TelemetryMetricBox("Constraints", "${tel.activeConstraintsCount}", VioletGravityTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricBox(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        modifier = Modifier
            .background(StudioObsidian.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondaryMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VfxKeyframesPanel(
    uiState: SimulationUiState,
    onToggleAutomation: () -> Unit,
    onAddKeyframe: () -> Unit,
    onRemoveKeyframe: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = StudioCardElevated,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VFX TIMELINE KEYFRAME AUTOMATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberClothSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Interpolates gravity & wind vectors across the 180-frame simulation loop",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                    Switch(
                        checked = uiState.keyframeAutomationEnabled,
                        onCheckedChange = { onToggleAutomation() },
                        modifier = Modifier.testTag("switch_keyframe_automation"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioObsidian,
                            checkedTrackColor = AmberClothSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanFluidPrimary.copy(alpha = 0.16f))
                        .border(1.dp, CyanFluidPrimary, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAddKeyframe)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                        .testTag("btn_bake_keyframe"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "Bake Keyframe",
                        tint = CyanFluidPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bake Current Gravity & Wind at Current Frame",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanFluidPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                uiState.keyframes.forEach { kf ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(StudioObsidian.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Frame ${kf.frameNumber}: ${kf.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "g_y = %.2f m/s²  |  w_x = %.2f m/s",
                                    kf.gravityY,
                                    kf.windX
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanFluidPrimary
                            )
                        }
                        if (uiState.keyframes.size > 1) {
                            IconButton(
                                onClick = { onRemoveKeyframe(kf.id) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("btn_delete_kf_${kf.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Keyframe",
                                    tint = CrimsonStressAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(text = valueText, style = MaterialTheme.typography.bodySmall, color = CyanFluidPrimary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = CyanFluidPrimary,
                activeTrackColor = CyanFluidPrimary,
                inactiveTrackColor = StudioObsidian
            )
        )
    }
}

@Composable
private fun ParameterSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioObsidian,
                checkedTrackColor = CyanFluidPrimary
            )
        )
    }
}
