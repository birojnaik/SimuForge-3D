package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.physics.Camera3D
import com.example.physics.SimulationFrameCache
import com.example.physics.SimulationUiState
import com.example.physics.SolverMode
import com.example.physics.Vec3
import com.example.physics.ViewportShadingMode
import com.example.physics.ViewportToolMode
import com.example.ui.theme.AmberClothSecondary
import com.example.ui.theme.CrimsonStressAlert
import com.example.ui.theme.CyanFluidPrimary
import com.example.ui.theme.EmeraldCoupledAccent
import com.example.ui.theme.StudioBorderSubtle
import com.example.ui.theme.StudioDeepNavy
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.VioletGravityTertiary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private sealed interface DepthPrimitive {
    val depthZ: Float
}

private data class QuadPrimitive(
    override val depthZ: Float,
    val x0: Float, val y0: Float,
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val x3: Float, val y3: Float,
    val fillColor: Color,
    val strokeColor: Color
) : DepthPrimitive

private data class ParticlePrimitive(
    override val depthZ: Float,
    val sx: Float,
    val sy: Float,
    val radiusPx: Float,
    val coreColor: Color,
    val glowColor: Color,
    val velEndX: Float,
    val velEndY: Float,
    val drawVelocityArrow: Boolean
) : DepthPrimitive

private data class ColliderSpherePrimitive(
    override val depthZ: Float,
    val center: Vec3,
    val radius: Float
) : DepthPrimitive

@Composable
fun Viewport3DCanvas(
    uiState: SimulationUiState,
    onViewportGesture: (
        screenX: Float,
        screenY: Float,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
        isRelease: Boolean
    ) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onSelectTool: (ViewportToolMode) -> Unit,
    onCycleShading: () -> Unit,
    onResetCamera: () -> Unit,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frame = uiState.currentFrame
    val cdCanvas = stringResource(R.string.cd_viewport_canvas)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF131D33),
                        StudioDeepNavy,
                        StudioObsidian
                    ),
                    radius = 950f
                )
            )
            .border(1.dp, StudioBorderSubtle, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("viewport_3d_canvas")
                .pointerInput(uiState.toolMode, uiState.solverMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        onViewportGesture(down.position.x, down.position.y, 0f, 0f, w, h, false)

                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            if (abs(zoom - 1f) > 0.01f) {
                                onPinchZoom(zoom)
                            }
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            if (centroid != Offset.Unspecified) {
                                onViewportGesture(
                                    centroid.x,
                                    centroid.y,
                                    pan.x,
                                    pan.y,
                                    w,
                                    h,
                                    false
                                )
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        onViewportGesture(0f, 0f, 0f, 0f, w, h, true)
                    }
                }
        ) {
            val vw = size.width
            val vh = size.height
            val cam = uiState.camera

            if (uiState.showGridAndAxes) {
                draw3DFloorGridAndDomainBox(cam, vw, vh, uiState.solverMode)
            }

            if (frame != null) {
                draw3DSimulationFrame(
                    frame = frame,
                    cam = cam,
                    vw = vw,
                    vh = vh,
                    shadingMode = uiState.shadingMode,
                    solverMode = uiState.solverMode
                )
            }

            // Draw interactive 3D Force / Emitter Cursor Ring if active
            uiState.activeBrushWorldPos?.let { brushPos ->
                val proj = cam.project(brushPos, vw, vh)
                if (proj.visible) {
                    val ringRadius = (0.45f * proj.scale).coerceIn(16f, 90f)
                    drawCircle(
                        color = CyanFluidPrimary.copy(alpha = 0.22f),
                        radius = ringRadius,
                        center = Offset(proj.screenX, proj.screenY)
                    )
                    drawCircle(
                        color = CyanFluidPrimary,
                        radius = ringRadius,
                        center = Offset(proj.screenX, proj.screenY),
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            // Draw 3D Orientation & Gravity Vector Gizmo in bottom-right above toolbar
            draw3DAxisAndGravityGizmo(
                cam = cam,
                gravity = uiState.gravityVector,
                centerX = vw - 54.dp.toPx(),
                centerY = vh - 102.dp.toPx(),
                armLength = 28.dp.toPx()
            )
        }

        // Top-Left Mathematical Solver Telemetry HUD Overlay
        if (frame != null) {
            val tel = frame.telemetry
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(
                        StudioObsidian.copy(alpha = 0.78f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, StudioBorderSubtle.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (uiState.isRunning) EmeraldCoupledAccent else AmberClothSecondary,
                                CircleShape
                            )
                    )
                    Text(
                        text = "${uiState.solverMode.title.uppercase()} • ${uiState.shadingMode.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanFluidPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.solverMode.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD0E4FF)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "t=%.2fs | DOF:%d | Ek:%.1fJ | |p|:%.1f | CFL:%.2f",
                        tel.simTimeSeconds,
                        tel.activeElementsCount,
                        tel.kineticEnergy,
                        tel.linearMomentumMag,
                        tel.cflNumber
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryMuted
                )
            }
        }

        // Top-Right Viewport Controls (Shading Mode Cycler, Grid Toggle, Reset 3D Camera)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ViewportMiniActionChip(
                icon = Icons.Default.Layers,
                label = uiState.shadingMode.label,
                active = uiState.shadingMode != ViewportShadingMode.LIT_SHADED,
                onClick = onCycleShading,
                testTag = "btn_cycle_shading"
            )
            ViewportMiniActionChip(
                icon = Icons.Default.GridOn,
                label = "Grid",
                active = uiState.showGridAndAxes,
                onClick = onToggleGrid,
                testTag = "btn_toggle_grid"
            )
            ViewportMiniActionChip(
                icon = Icons.Default.CenterFocusStrong,
                label = "Reset Cam",
                active = false,
                onClick = onResetCamera,
                testTag = "btn_reset_camera"
            )
        }

        // Bottom Floating 3D Interaction Tool Strip
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            color = StudioObsidian.copy(alpha = 0.88f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewportToolMode.entries.forEach { tool ->
                    val selected = uiState.toolMode == tool
                    val icon = when (tool) {
                        ViewportToolMode.ORBIT_CAMERA -> Icons.Default.RotateRight
                        ViewportToolMode.FORCE_BRUSH -> Icons.Default.PanToolAlt
                        ViewportToolMode.MOVE_COLLIDER -> Icons.Default.OpenWith
                        ViewportToolMode.SPAWN_EMITTER -> Icons.Default.WaterDrop
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) CyanFluidPrimary.copy(alpha = 0.22f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) CyanFluidPrimary else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectTool(tool) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("tool_${tool.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tool.label,
                            tint = if (selected) CyanFluidPrimary else TextSecondaryMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = tool.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else TextSecondaryMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewportMiniActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) CyanFluidPrimary.copy(alpha = 0.2f)
                else StudioObsidian.copy(alpha = 0.78f)
            )
            .border(
                1.dp,
                if (active) CyanFluidPrimary else StudioBorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CyanFluidPrimary else TextSecondaryMuted,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else TextSecondaryMuted,
            fontSize = 10.sp
        )
    }
}

private fun DrawScope.draw3DFloorGridAndDomainBox(
    cam: Camera3D,
    vw: Float,
    vh: Float,
    solverMode: SolverMode
) {
    val floorY = -1.35f
    val gridExtent = 1.6f
    val steps = 8
    val stepSize = (gridExtent * 2f) / steps

    for (i in 0..steps) {
        val coord = -gridExtent + i * stepSize
        val isCenter = i == steps / 2
        val lineColor = if (isCenter) {
            CyanFluidPrimary.copy(alpha = 0.28f)
        } else {
            Color(0xFF2A3B5C).copy(alpha = 0.35f)
        }
        val strokeW = if (isCenter) 2f else 1f

        val pStartX = cam.project(Vec3(coord, floorY, -gridExtent), vw, vh)
        val pEndX = cam.project(Vec3(coord, floorY, gridExtent), vw, vh)
        if (pStartX.visible && pEndX.visible) {
            drawLine(
                color = lineColor,
                start = Offset(pStartX.screenX, pStartX.screenY),
                end = Offset(pEndX.screenX, pEndX.screenY),
                strokeWidth = strokeW
            )
        }

        val pStartZ = cam.project(Vec3(-gridExtent, floorY, coord), vw, vh)
        val pEndZ = cam.project(Vec3(gridExtent, floorY, coord), vw, vh)
        if (pStartZ.visible && pEndZ.visible) {
            drawLine(
                color = lineColor,
                start = Offset(pStartZ.screenX, pStartZ.screenY),
                end = Offset(pEndZ.screenX, pEndZ.screenY),
                strokeWidth = strokeW
            )
        }
    }

    // Draw 3D Domain Wireframe Bounding Box for Fluid / Coupled modes
    if (solverMode == SolverMode.FLUID_SPH || solverMode == SolverMode.COUPLED_VFX) {
        val bx = 1.45f
        val by = 1.35f
        val bz = 1.15f
        val corners = arrayOf(
            Vec3(-bx, -by, -bz), Vec3(bx, -by, -bz), Vec3(bx, by, -bz), Vec3(-bx, by, -bz),
            Vec3(-bx, -by, bz), Vec3(bx, -by, bz), Vec3(bx, by, bz), Vec3(-bx, by, bz)
        )
        val projected = corners.map { cam.project(it, vw, vh) }
        val edges = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        for ((a, b) in edges) {
            val pa = projected[a]
            val pb = projected[b]
            if (pa.visible && pb.visible) {
                drawLine(
                    color = CyanFluidPrimary.copy(alpha = 0.18f),
                    start = Offset(pa.screenX, pa.screenY),
                    end = Offset(pb.screenX, pb.screenY),
                    strokeWidth = 1.2f
                )
            }
        }
    }
}

private fun DrawScope.draw3DSimulationFrame(
    frame: SimulationFrameCache,
    cam: Camera3D,
    vw: Float,
    vh: Float,
    shadingMode: ViewportShadingMode,
    solverMode: SolverMode
) {
    // Draw N-Body 3D Orbital Trajectory Ribbons first
    for (body in frame.celestialBodies) {
        val trail = body.trail
        if (trail.size >= 2) {
            val baseColor = when (body.colorCategory % 4) {
                0 -> AmberClothSecondary
                1 -> CyanFluidPrimary
                2 -> VioletGravityTertiary
                else -> EmeraldCoupledAccent
            }
            for (i in 0 until trail.size - 1) {
                val p0 = cam.project(trail[i], vw, vh)
                val p1 = cam.project(trail[i + 1], vw, vh)
                if (p0.visible && p1.visible) {
                    val alpha = ((i + 1).toFloat() / trail.size) * 0.65f
                    drawLine(
                        color = baseColor.copy(alpha = alpha),
                        start = Offset(p0.screenX, p0.screenY),
                        end = Offset(p1.screenX, p1.screenY),
                        strokeWidth = 2.2f
                    )
                }
            }
        }
    }

    val primitives = ArrayList<DepthPrimitive>(
        frame.fluidParticles.size + frame.clothQuads.size + frame.celestialBodies.size + 2
    )

    // Add 3D Obstacle / Collider Sphere if active
    if (frame.obstacleVisible) {
        val obsProj = cam.project(frame.obstacleCenter, vw, vh)
        if (obsProj.visible) {
            primitives.add(
                ColliderSpherePrimitive(
                    depthZ = obsProj.depthZ,
                    center = frame.obstacleCenter,
                    radius = frame.obstacleRadius
                )
            )
        }
    }

    // Project Cloth Quads or Wireframe Links
    val nodeProjections = if (frame.clothNodes.isNotEmpty()) {
        frame.clothNodes.map { cam.project(it.position, vw, vh) }
    } else emptyList()

    if (shadingMode == ViewportShadingMode.WIREFRAME && frame.clothLinks.isNotEmpty()) {
        for (link in frame.clothLinks) {
            if (link.indexA < nodeProjections.size && link.indexB < nodeProjections.size) {
                val pa = nodeProjections[link.indexA]
                val pb = nodeProjections[link.indexB]
                if (pa.visible && pb.visible) {
                    val strainRatio = (link.strain / 0.65f).coerceIn(0f, 1f)
                    val col = lerpColor(AmberClothSecondary, CrimsonStressAlert, strainRatio)
                    drawLine(
                        color = col.copy(alpha = 0.85f),
                        start = Offset(pa.screenX, pa.screenY),
                        end = Offset(pb.screenX, pb.screenY),
                        strokeWidth = 2f
                    )
                }
            }
        }
    } else if (frame.clothQuads.isNotEmpty()) {
        val lightDir = Vec3(0.45f, 0.78f, 0.42f).normalized()
        for (quad in frame.clothQuads) {
            val p00 = nodeProjections.getOrNull(quad.n00) ?: continue
            val p10 = nodeProjections.getOrNull(quad.n10) ?: continue
            val p11 = nodeProjections.getOrNull(quad.n11) ?: continue
            val p01 = nodeProjections.getOrNull(quad.n01) ?: continue
            if (!p00.visible || !p10.visible || !p11.visible || !p01.visible) continue

            val avgDepth = (p00.depthZ + p10.depthZ + p11.depthZ + p01.depthZ) * 0.25f
            val ndotl = abs(quad.normal.dot(lightDir)).coerceIn(0.22f, 1f)

            val baseHue = when (shadingMode) {
                ViewportShadingMode.LIT_SHADED -> {
                    val strainMix = (quad.strain / 0.65f).coerceIn(0f, 1f)
                    lerpColor(Color(0xFFFFB300), CrimsonStressAlert, strainMix)
                }
                ViewportShadingMode.VELOCITY_FIELD -> {
                    val vMag = frame.clothNodes.getOrNull(quad.n00)?.velocity?.length() ?: 0f
                    speedToHeatmapColor(vMag / 3.5f)
                }
                ViewportShadingMode.PRESSURE_STRESS -> {
                    speedToHeatmapColor(quad.strain / 0.55f)
                }
                ViewportShadingMode.WIREFRAME -> AmberClothSecondary
            }

            val litFill = Color(
                red = (baseHue.red * ndotl).coerceIn(0f, 1f),
                green = (baseHue.green * ndotl).coerceIn(0f, 1f),
                blue = (baseHue.blue * ndotl).coerceIn(0f, 1f),
                alpha = 0.82f
            )
            val wireStroke = Color.White.copy(alpha = 0.24f)

            primitives.add(
                QuadPrimitive(
                    depthZ = avgDepth,
                    x0 = p00.screenX, y0 = p00.screenY,
                    x1 = p10.screenX, y1 = p10.screenY,
                    x2 = p11.screenX, y2 = p11.screenY,
                    x3 = p01.screenX, y3 = p01.screenY,
                    fillColor = litFill,
                    strokeColor = wireStroke
                )
            )
        }
    }

    // Project SPH Fluid Particles
    for (p in frame.fluidParticles) {
        val proj = cam.project(p.position, vw, vh)
        if (!proj.visible) continue

        val speed = p.velocity.length()
        val coreColor = when (shadingMode) {
            ViewportShadingMode.LIT_SHADED -> {
                val foam = (speed / 4.2f).coerceIn(0f, 1f)
                lerpColor(CyanFluidPrimary, Color(0xFFE0FFFF), foam)
            }
            ViewportShadingMode.WIREFRAME -> CyanFluidPrimary
            ViewportShadingMode.VELOCITY_FIELD -> speedToHeatmapColor(speed / 4.0f)
            ViewportShadingMode.PRESSURE_STRESS -> speedToHeatmapColor(p.pressure / 950f)
        }

        val velTipProj = if (shadingMode == ViewportShadingMode.VELOCITY_FIELD || shadingMode == ViewportShadingMode.WIREFRAME) {
            cam.project(p.position + p.velocity * 0.09f, vw, vh)
        } else proj

        val radiusPx = (0.078f * proj.scale).coerceIn(4.5f, 22f)
        primitives.add(
            ParticlePrimitive(
                depthZ = proj.depthZ,
                sx = proj.screenX,
                sy = proj.screenY,
                radiusPx = radiusPx,
                coreColor = coreColor,
                glowColor = coreColor.copy(alpha = 0.26f),
                velEndX = velTipProj.screenX,
                velEndY = velTipProj.screenY,
                drawVelocityArrow = shadingMode == ViewportShadingMode.VELOCITY_FIELD || shadingMode == ViewportShadingMode.WIREFRAME
            )
        )
    }

    // Project N-Body Celestial Bodies
    for (body in frame.celestialBodies) {
        val proj = cam.project(body.position, vw, vh)
        if (!proj.visible) continue

        val speed = body.velocity.length()
        val baseColor = when (shadingMode) {
            ViewportShadingMode.VELOCITY_FIELD -> speedToHeatmapColor(speed / 4.2f)
            ViewportShadingMode.PRESSURE_STRESS -> speedToHeatmapColor(body.acceleration.length() / 8f)
            else -> when (body.colorCategory % 4) {
                0 -> AmberClothSecondary
                1 -> CyanFluidPrimary
                2 -> VioletGravityTertiary
                else -> EmeraldCoupledAccent
            }
        }
        val radiusPx = (body.radius * proj.scale).coerceIn(6f, 44f)
        val velTip = cam.project(body.position + body.velocity * 0.14f, vw, vh)

        primitives.add(
            ParticlePrimitive(
                depthZ = proj.depthZ,
                sx = proj.screenX,
                sy = proj.screenY,
                radiusPx = radiusPx,
                coreColor = baseColor,
                glowColor = baseColor.copy(alpha = 0.34f),
                velEndX = velTip.screenX,
                velEndY = velTip.screenY,
                drawVelocityArrow = shadingMode == ViewportShadingMode.VELOCITY_FIELD || shadingMode == ViewportShadingMode.WIREFRAME
            )
        )
    }

    // Sort back-to-front by camera depthZ (Painter's Algorithm)
    primitives.sortByDescending { it.depthZ }

    val quadPath = Path()
    for (prim in primitives) {
        when (prim) {
            is ColliderSpherePrimitive -> {
                draw3DWireframeShadedSphere(cam, vw, vh, prim.center, prim.radius)
            }
            is QuadPrimitive -> {
                quadPath.reset()
                quadPath.moveTo(prim.x0, prim.y0)
                quadPath.lineTo(prim.x1, prim.y1)
                quadPath.lineTo(prim.x2, prim.y2)
                quadPath.lineTo(prim.x3, prim.y3)
                quadPath.close()
                drawPath(path = quadPath, color = prim.fillColor, style = Fill)
                drawPath(path = quadPath, color = prim.strokeColor, style = Stroke(width = 1.1f))
            }
            is ParticlePrimitive -> {
                // Outer metaball / corona glow
                drawCircle(
                    color = prim.glowColor,
                    radius = prim.radiusPx * 1.65f,
                    center = Offset(prim.sx, prim.sy)
                )
                if (shadingMode == ViewportShadingMode.WIREFRAME) {
                    drawCircle(
                        color = prim.coreColor,
                        radius = prim.radiusPx,
                        center = Offset(prim.sx, prim.sy),
                        style = Stroke(width = 1.8f)
                    )
                } else {
                    // Core sphere
                    drawCircle(
                        color = prim.coreColor,
                        radius = prim.radiusPx,
                        center = Offset(prim.sx, prim.sy)
                    )
                    // Specular 3D Phong highlight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.72f),
                        radius = prim.radiusPx * 0.34f,
                        center = Offset(
                            prim.sx - prim.radiusPx * 0.28f,
                            prim.sy - prim.radiusPx * 0.28f
                        )
                    )
                }

                if (prim.drawVelocityArrow) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(prim.sx, prim.sy),
                        end = Offset(prim.velEndX, prim.velEndY),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    // Highlight pinned cloth vertices
    for (node in frame.clothNodes) {
        if (node.pinned) {
            val p = nodeProjections.getOrNull(node.index) ?: continue
            if (p.visible) {
                drawCircle(
                    color = CrimsonStressAlert,
                    radius = 5.5f,
                    center = Offset(p.screenX, p.screenY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(p.screenX, p.screenY)
                )
            }
        }
    }
}

private fun DrawScope.draw3DWireframeShadedSphere(
    cam: Camera3D,
    vw: Float,
    vh: Float,
    center: Vec3,
    radius: Float
) {
    val cProj = cam.project(center, vw, vh)
    if (!cProj.visible) return
    val rPx = (radius * cProj.scale).coerceIn(10f, 220f)

    // Translucent 3D shaded core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                VioletGravityTertiary.copy(alpha = 0.42f),
                Color(0xFF281848).copy(alpha = 0.65f),
                Color(0xFF120A24).copy(alpha = 0.82f)
            ),
            center = Offset(cProj.screenX - rPx * 0.28f, cProj.screenY - rPx * 0.28f),
            radius = rPx * 1.25f
        ),
        radius = rPx,
        center = Offset(cProj.screenX, cProj.screenY)
    )

    // Draw 3 great circles (XY, YZ, XZ) in true 3D perspective
    val segments = 24
    for (plane in 0..2) {
        var prev: Offset? = null
        for (s in 0..segments) {
            val ang = (s.toFloat() / segments) * (2f * Math.PI.toFloat())
            val ca = cos(ang) * radius
            val sa = sin(ang) * radius
            val pt = when (plane) {
                0 -> center + Vec3(ca, sa, 0f)
                1 -> center + Vec3(0f, ca, sa)
                else -> center + Vec3(ca, 0f, sa)
            }
            val pProj = cam.project(pt, vw, vh)
            if (pProj.visible) {
                val cur = Offset(pProj.screenX, pProj.screenY)
                prev?.let {
                    drawLine(
                        color = VioletGravityTertiary.copy(alpha = 0.55f),
                        start = it,
                        end = cur,
                        strokeWidth = 1.5f
                    )
                }
                prev = cur
            }
        }
    }

    // Outer rim silhouette
    drawCircle(
        color = VioletGravityTertiary.copy(alpha = 0.85f),
        radius = rPx,
        center = Offset(cProj.screenX, cProj.screenY),
        style = Stroke(width = 2f)
    )
}

private fun DrawScope.draw3DAxisAndGravityGizmo(
    cam: Camera3D,
    gravity: Vec3,
    centerX: Float,
    centerY: Float,
    armLength: Float
) {
    val (right, up, _) = cam.basisVectors()

    fun projectDir(dir: Vec3): Offset {
        val sx = dir.dot(right)
        val sy = -dir.dot(up)
        return Offset(centerX + sx * armLength, centerY + sy * armLength)
    }

    drawCircle(
        color = StudioObsidian.copy(alpha = 0.72f),
        radius = armLength * 1.2f,
        center = Offset(centerX, centerY)
    )

    val origin = Offset(centerX, centerY)
    val xEnd = projectDir(Vec3(1f, 0f, 0f))
    val yEnd = projectDir(Vec3(0f, 1f, 0f))
    val zEnd = projectDir(Vec3(0f, 0f, 1f))

    drawLine(color = CrimsonStressAlert, start = origin, end = xEnd, strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = EmeraldCoupledAccent, start = origin, end = yEnd, strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = CyanFluidPrimary, start = origin, end = zEnd, strokeWidth = 3f, cap = StrokeCap.Round)

    if (gravity.lengthSq() > 0.05f) {
        val gEnd = projectDir(gravity.normalized() * 0.85f)
        drawLine(
            color = AmberClothSecondary,
            start = origin,
            end = gEnd,
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawCircle(color = AmberClothSecondary, radius = 4f, center = gEnd)
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = start.alpha + (stop.alpha - start.alpha) * t
    )
}

private fun speedToHeatmapColor(normValue: Float): Color {
    val t = normValue.coerceIn(0f, 1f)
    return when {
        t < 0.33f -> lerpColor(Color(0xFF0072FF), CyanFluidPrimary, t / 0.33f)
        t < 0.66f -> lerpColor(CyanFluidPrimary, AmberClothSecondary, (t - 0.33f) / 0.33f)
        else -> lerpColor(AmberClothSecondary, CrimsonStressAlert, (t - 0.66f) / 0.34f)
    }
}
