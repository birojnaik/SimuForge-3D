package com.example.physics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class SolverMode(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    FLUID_SPH(
        title = "SPH Fluid",
        subtitle = "Navier-Stokes Smoothed Particle Hydrodynamics",
        badge = "ρ(Dv/Dt) = -∇P + μ∇²v + f"
    ),
    CLOTH_VERLET(
        title = "Verlet Cloth",
        subtitle = "Position-Based Spring-Mass & Aerodynamic Lift",
        badge = "x(t+Δt) = 2x(t) - x(t-Δt) + aΔt²"
    ),
    GRAVITY_NBODY(
        title = "N-Body Gravity",
        subtitle = "Symplectic Velocity-Verlet Universal Gravitation",
        badge = "F_ij = G·m_i·m_j(r_j - r_i)/|r_ij|³"
    ),
    COUPLED_VFX(
        title = "Coupled VFX",
        subtitle = "Multi-Physics Fluid + Cloth + Gravity Coupling",
        badge = "∇·T_fluid + F_cloth + F_grav = 0"
    )
}

enum class ViewportShadingMode(val label: String) {
    LIT_SHADED("Lit Shaded"),
    WIREFRAME("Wireframe"),
    VELOCITY_FIELD("Velocity Vectors"),
    PRESSURE_STRESS("Stress / Pressure")
}

enum class ViewportToolMode(val label: String) {
    ORBIT_CAMERA("Orbit 3D"),
    FORCE_BRUSH("Force Brush"),
    MOVE_COLLIDER("Move Collider"),
    SPAWN_EMITTER("Emit / Spawn")
}

data class VfxKeyframe(
    val id: Int,
    val frameNumber: Int,
    val label: String,
    val gravityY: Float,
    val windX: Float,
    val vortexBoost: Float
)

data class SimulationTelemetry(
    val frameIndex: Int = 0,
    val simTimeSeconds: Float = 0f,
    val kineticEnergy: Float = 0f,
    val potentialEnergy: Float = 0f,
    val totalEnergy: Float = 0f,
    val linearMomentumMag: Float = 0f,
    val maxSpeed: Float = 0f,
    val cflNumber: Float = 0f,
    val peakStressOrPressure: Float = 0f,
    val activeElementsCount: Int = 0,
    val activeConstraintsCount: Int = 0,
    val energyHistory: List<Float> = emptyList()
)

data class SimulationFrameCache(
    val frameIndex: Int,
    val simTimeSeconds: Float,
    val fluidParticles: List<SphParticleSnapshot>,
    val clothNodes: List<ClothNodeSnapshot>,
    val clothLinks: List<ClothLinkSnapshot>,
    val clothQuads: List<ClothQuadSnapshot>,
    val celestialBodies: List<CelestialBodySnapshot>,
    val obstacleCenter: Vec3,
    val obstacleRadius: Float,
    val obstacleVisible: Boolean,
    val telemetry: SimulationTelemetry
)

data class PresetSceneInfo(
    val index: Int,
    val name: String,
    val description: String,
    val tag: String
)

data class SimulationUiState(
    val solverMode: SolverMode = SolverMode.FLUID_SPH,
    val shadingMode: ViewportShadingMode = ViewportShadingMode.LIT_SHADED,
    val toolMode: ViewportToolMode = ViewportToolMode.ORBIT_CAMERA,
    val isRunning: Boolean = true,
    val timeScale: Float = 1.0f,
    val substeps: Int = 2,
    val camera: Camera3D = Camera3D(),
    val selectedPresetIndex: Int = 0,

    // Global Environmental Vectors
    val gravityVector: Vec3 = Vec3(0f, -9.81f, 0f),
    val windVector: Vec3 = Vec3(2.4f, 0.2f, 0.6f),
    val deviceTiltGravityEnabled: Boolean = false,
    val showMathInspectorModal: Boolean = false,
    val showGridAndAxes: Boolean = true,
    val showBoundingBox: Boolean = true,

    // Solver-specific tunable parameters
    val fluidSmoothingRadius: Float = 0.34f,
    val fluidGasStiffness: Float = 1.85f,
    val fluidViscosity: Float = 0.22f,
    val fluidRestitution: Float = 0.52f,
    val fluidVortexStrength: Float = 0.0f,
    val fluidContinuousJets: Boolean = false,

    val clothStructuralStiffness: Float = 0.92f,
    val clothBendStiffness: Float = 0.38f,
    val clothSolverIterations: Int = 7,
    val clothAerodynamicDrag: Float = 0.55f,
    val clothTearable: Boolean = false,
    val clothTearThreshold: Float = 0.72f,

    val gravityConstantG: Float = 0.85f,
    val gravitySoftening: Float = 0.14f,
    val gravityAccretionMerger: Boolean = false,
    val gravityRelativisticPrecession: Float = 0.0f,

    // Interactive 3D Cursor Feedback
    val activeBrushWorldPos: Vec3? = null,
    val activeBrushWorldDir: Vec3 = Vec3(),

    // Timeline Cache & Keyframes
    val maxCacheFrames: Int = 180,
    val cachedFramesCount: Int = 0,
    val currentScrubSlot: Int = 0,
    val isScrubbingCache: Boolean = false,
    val keyframeAutomationEnabled: Boolean = true,
    val keyframes: List<VfxKeyframe> = listOf(
        VfxKeyframe(1, 0, "Standard 1G Init", gravityY = -9.81f, windX = 2.2f, vortexBoost = 0f),
        VfxKeyframe(2, 60, "Aero Gale Gust", gravityY = -9.81f, windX = 5.4f, vortexBoost = 0.8f),
        VfxKeyframe(3, 120, "Zero-G Uplift", gravityY = 1.5f, windX = -1.8f, vortexBoost = 2.1f)
    ),

    // Current rendered frame snapshot
    val currentFrame: SimulationFrameCache? = null
)

class SimulationViewModel : ViewModel() {
    private val fluidEngine = SphFluidEngine()
    private val clothEngine = VerletClothEngine()
    private val gravityEngine = NBodyGravityEngine()

    private val frameRingBuffer = ArrayList<SimulationFrameCache>(180)
    private val energyHistoryDeque = ArrayDeque<Float>(60)
    private var totalSimulatedFrames = 0
    private var totalSimTime = 0f
    private var nextKeyframeId = 10

    private val _uiState = MutableStateFlow(SimulationUiState())
    val uiState: StateFlow<SimulationUiState> = _uiState.asStateFlow()

    init {
        captureCurrentSnapshotToCache()
    }

    fun presetsForMode(mode: SolverMode): List<PresetSceneInfo> = when (mode) {
        SolverMode.FLUID_SPH -> listOf(
            PresetSceneInfo(0, "Dam Break Surge", "High-column hydrostatic collapse against a 3D spherical obstacle", "Navier-Stokes"),
            PresetSceneInfo(1, "Colliding Hydro Jets", "Opposing high-velocity 3D nozzles colliding mid-air", "Continuous Emitter"),
            PresetSceneInfo(2, "Centrifugal Vortex Tank", "Angular momentum whirlpool funnel with radial pressure gradient", "Vorticity"),
            PresetSceneInfo(3, "Zero-G Coalescence", "Surface-tension driven droplet fusion in microgravity", "Cohesion")
        )
        SolverMode.CLOTH_VERLET -> listOf(
            PresetSceneInfo(0, "Wind Tunnel Aero Silk", "Pinned banner billowing in a 3D curl-noise wind field", "Aerodynamics"),
            PresetSceneInfo(1, "Sphere Drape & Canopy", "Free-falling cloth sheet draping with surface friction over a sphere", "Collision"),
            PresetSceneInfo(2, "Suspension Hammock", "Four-corner pinned elastic membrane under dynamic load", "Hooke Lattice"),
            PresetSceneInfo(3, "Tearable Stress Curtain", "High-tension fabric fracturing when strain exceeds yield limit", "Fracture")
        )
        SolverMode.GRAVITY_NBODY -> listOf(
            PresetSceneInfo(0, "Binary Star Accretion", "Two massive stars orbiting their barycenter with a 3D planetesimal disk", "Keplerian"),
            PresetSceneInfo(1, "Planetary Ring Resonance", "Central hyper-massive attractor with inclined resonant moons", "Symplectic"),
            PresetSceneInfo(2, "Figure-8 Choreography", "Chenciner-Montgomery periodic 3-body solution in 3D space", "3-Body Exact"),
            PresetSceneInfo(3, "Globular Cluster Collapse", "Self-gravitating 3D star cloud with inelastic accretion mergers", "Virial")
        )
        SolverMode.COUPLED_VFX -> listOf(
            PresetSceneInfo(0, "Hydro-Cloth Cascade", "SPH fluid jet impacting an elastic Verlet suspension membrane", "2-Way Coupled"),
            PresetSceneInfo(1, "Orbital Fluid Storm", "Fluid vortex and aero cloth whipped by binary gravitational attractors", "Multi-Solver"),
            PresetSceneInfo(2, "Zero-G Fabric & Droplets", "Floating fluid globules rebounding off a tensioned 3D canopy", "Microgravity"),
            PresetSceneInfo(3, "Fracture Breach Flood", "High-pressure water column tearing through a stressed cloth barrier", "Yield Breach")
        )
    }

    fun selectSolverMode(mode: SolverMode) {
        _uiState.update { state ->
            state.copy(
                solverMode = mode,
                selectedPresetIndex = 0,
                isScrubbingCache = false
            )
        }
        loadPreset(0)
    }

    fun loadPreset(index: Int) {
        val state = _uiState.value
        frameRingBuffer.clear()
        energyHistoryDeque.clear()
        totalSimulatedFrames = 0
        totalSimTime = 0f

        when (state.solverMode) {
            SolverMode.FLUID_SPH -> {
                fluidEngine.loadPreset(index)
                val newGravity = if (index == 3) Vec3(0f, 0f, 0f) else Vec3(0f, -9.81f, 0f)
                _uiState.update {
                    it.copy(
                        selectedPresetIndex = index,
                        gravityVector = newGravity,
                        fluidVortexStrength = fluidEngine.vortexStrength,
                        fluidContinuousJets = fluidEngine.continuousEmitterActive,
                        isScrubbingCache = false
                    )
                }
            }
            SolverMode.CLOTH_VERLET -> {
                clothEngine.loadPreset(index)
                val newWind = when (index) {
                    0 -> Vec3(4.2f, 0.4f, 0.8f)
                    1 -> Vec3(0.3f, 0f, 0.2f)
                    else -> Vec3(1.8f, 0.2f, 0.9f)
                }
                _uiState.update {
                    it.copy(
                        selectedPresetIndex = index,
                        gravityVector = Vec3(0f, -9.81f, 0f),
                        windVector = newWind,
                        clothTearable = clothEngine.tearableEnabled,
                        clothTearThreshold = clothEngine.tearStrainThreshold,
                        isScrubbingCache = false
                    )
                }
            }
            SolverMode.GRAVITY_NBODY -> {
                gravityEngine.loadPreset(index)
                _uiState.update {
                    it.copy(
                        selectedPresetIndex = index,
                        gravityAccretionMerger = gravityEngine.accretionCoalescence,
                        isScrubbingCache = false
                    )
                }
            }
            SolverMode.COUPLED_VFX -> {
                fluidEngine.loadPreset(if (index == 1) 2 else 1)
                clothEngine.loadPreset(if (index == 3) 3 else 2)
                gravityEngine.loadPreset(0)
                val gVec = if (index == 2) Vec3(0f, -0.5f, 0f) else Vec3(0f, -9.81f, 0f)
                _uiState.update {
                    it.copy(
                        selectedPresetIndex = index,
                        gravityVector = gVec,
                        fluidContinuousJets = true,
                        clothTearable = (index == 3),
                        isScrubbingCache = false
                    )
                }
            }
        }
        captureCurrentSnapshotToCache()
    }

    fun setShadingMode(mode: ViewportShadingMode) {
        _uiState.update { it.copy(shadingMode = mode) }
    }

    fun setToolMode(mode: ViewportToolMode) {
        _uiState.update { it.copy(toolMode = mode, activeBrushWorldPos = null) }
    }

    fun togglePlayPause() {
        _uiState.update {
            it.copy(
                isRunning = !it.isRunning,
                isScrubbingCache = false
            )
        }
    }

    fun stepSingleFrameForward() {
        _uiState.update { it.copy(isRunning = false, isScrubbingCache = false) }
        advanceSimulationStep(1f / 60f, forceStep = true)
    }

    fun stepSingleFrameBackward() {
        val count = frameRingBuffer.size
        if (count <= 1) return
        _uiState.update { state ->
            val currentSlot = if (state.isScrubbingCache) state.currentScrubSlot else count - 1
            val prevSlot = (currentSlot - 1).coerceAtLeast(0)
            state.copy(
                isRunning = false,
                isScrubbingCache = true,
                currentScrubSlot = prevSlot,
                currentFrame = frameRingBuffer[prevSlot]
            )
        }
    }

    fun scrubToCacheSlot(slotIndex: Int) {
        if (frameRingBuffer.isEmpty()) return
        val clamped = slotIndex.coerceIn(0, frameRingBuffer.size - 1)
        _uiState.update {
            it.copy(
                isRunning = false,
                isScrubbingCache = true,
                currentScrubSlot = clamped,
                currentFrame = frameRingBuffer[clamped]
            )
        }
    }

    fun resetSimulation() {
        loadPreset(_uiState.value.selectedPresetIndex)
    }

    fun triggerShockwaveImpulse() {
        val state = _uiState.value
        val center = Vec3(0f, -0.2f, 0f)
        when (state.solverMode) {
            SolverMode.FLUID_SPH -> fluidEngine.applyShockwave(center, 4.6f)
            SolverMode.CLOTH_VERLET -> clothEngine.applyShockwave(center, 0.25f)
            SolverMode.GRAVITY_NBODY -> gravityEngine.applySupernovaKick(center, 3.0f)
            SolverMode.COUPLED_VFX -> {
                fluidEngine.applyShockwave(center, 4.5f)
                clothEngine.applyShockwave(center, 0.22f)
                gravityEngine.applySupernovaKick(center, 2.2f)
            }
        }
        if (!state.isRunning) {
            advanceSimulationStep(1f / 60f, forceStep = true)
        }
    }

    fun orbitCamera(deltaYaw: Float, deltaPitch: Float, zoomFactor: Float = 1f) {
        _uiState.update { state ->
            val cam = state.camera
            val newYaw = cam.yawRadians - deltaYaw
            val newPitch = (cam.pitchRadians + deltaPitch).coerceIn(-1.42f, 1.42f)
            val newDist = (cam.distance / zoomFactor.coerceIn(0.8f, 1.25f)).coerceIn(2.2f, 10.5f)
            state.copy(
                camera = cam.copy(
                    yawRadians = newYaw,
                    pitchRadians = newPitch,
                    distance = newDist
                )
            )
        }
    }

    fun resetCameraView() {
        _uiState.update { it.copy(camera = Camera3D()) }
    }

    fun handleViewportGesture(
        screenX: Float,
        screenY: Float,
        dxPixels: Float,
        dyPixels: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        isRelease: Boolean
    ) {
        if (isRelease) {
            _uiState.update { it.copy(activeBrushWorldPos = null, activeBrushWorldDir = Vec3()) }
            return
        }

        val state = _uiState.value
        when (state.toolMode) {
            ViewportToolMode.ORBIT_CAMERA -> {
                orbitCamera(
                    deltaYaw = dxPixels * 0.0075f,
                    deltaPitch = dyPixels * 0.0075f
                )
            }
            ViewportToolMode.FORCE_BRUSH -> {
                val worldPos = state.camera.screenPointToWorldOnTargetPlane(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight
                )
                val worldDelta = state.camera.screenDeltaToWorld(
                    dxPixels,
                    dyPixels,
                    viewportWidth,
                    viewportHeight
                )
                val dir = if (worldDelta.lengthSq() > 1e-5f) worldDelta * 12f else Vec3(0f, 1.5f, 0f)
                _uiState.update {
                    it.copy(
                        activeBrushWorldPos = worldPos,
                        activeBrushWorldDir = dir
                    )
                }
            }
            ViewportToolMode.MOVE_COLLIDER -> {
                val deltaWorld = state.camera.screenDeltaToWorld(
                    dxPixels,
                    dyPixels,
                    viewportWidth,
                    viewportHeight
                )
                fluidEngine.obstacleEnabled = true
                fluidEngine.obstacleCenter = Vec3(
                    (fluidEngine.obstacleCenter.x + deltaWorld.x).coerceIn(-1.2f, 1.2f),
                    (fluidEngine.obstacleCenter.y + deltaWorld.y).coerceIn(-1.1f, 1.1f),
                    (fluidEngine.obstacleCenter.z + deltaWorld.z).coerceIn(-0.9f, 0.9f)
                )
                clothEngine.colliderEnabled = true
                clothEngine.colliderCenter = fluidEngine.obstacleCenter
                _uiState.update { it.copy(activeBrushWorldPos = fluidEngine.obstacleCenter) }
            }
            ViewportToolMode.SPAWN_EMITTER -> {
                val worldPos = state.camera.screenPointToWorldOnTargetPlane(
                    screenX,
                    screenY,
                    viewportWidth,
                    viewportHeight
                )
                val worldVel = state.camera.screenDeltaToWorld(
                    dxPixels,
                    dyPixels,
                    viewportWidth,
                    viewportHeight
                ) * 18f + Vec3(0f, 0.8f, 0f)
                when (state.solverMode) {
                    SolverMode.FLUID_SPH -> fluidEngine.injectFluidBurst(worldPos, worldVel)
                    SolverMode.GRAVITY_NBODY -> gravityEngine.spawnCelestialBody(worldPos, worldVel * 0.35f, 0.75f)
                    SolverMode.CLOTH_VERLET -> clothEngine.applyShockwave(worldPos, 0.16f)
                    SolverMode.COUPLED_VFX -> {
                        fluidEngine.injectFluidBurst(worldPos, worldVel)
                        clothEngine.applyShockwave(worldPos, 0.12f)
                    }
                }
                _uiState.update { it.copy(activeBrushWorldPos = worldPos) }
            }
        }
    }

    // Parameter Updaters
    fun updateTimeScale(scale: Float) {
        _uiState.update { it.copy(timeScale = scale.coerceIn(0.15f, 2.0f)) }
    }

    fun updateSubsteps(steps: Int) {
        _uiState.update { it.copy(substeps = steps.coerceIn(1, 5)) }
    }

    fun updateGravityY(gy: Float) {
        _uiState.update { it.copy(gravityVector = it.gravityVector.copy(y = gy)) }
    }

    fun updateWindX(wx: Float) {
        _uiState.update { it.copy(windVector = it.windVector.copy(x = wx)) }
    }

    fun updateDeviceSensorGravity(ax: Float, ay: Float, az: Float) {
        if (!_uiState.value.deviceTiltGravityEnabled) return
        // Map device accelerometer vector into 3D viewport gravity
        val gx = (-ax).coerceIn(-12f, 12f)
        val gy = (-ay).coerceIn(-12f, 12f)
        val gz = (az * 0.5f).coerceIn(-8f, 8f)
        _uiState.update { it.copy(gravityVector = Vec3(gx, gy, gz)) }
    }

    fun toggleDeviceTiltGravity() {
        _uiState.update {
            val next = !it.deviceTiltGravityEnabled
            val resetGrav = if (!next) Vec3(0f, -9.81f, 0f) else it.gravityVector
            it.copy(deviceTiltGravityEnabled = next, gravityVector = resetGrav)
        }
    }

    fun toggleMathInspectorModal() {
        _uiState.update { it.copy(showMathInspectorModal = !it.showMathInspectorModal) }
    }

    fun toggleGridAndAxes() {
        _uiState.update { it.copy(showGridAndAxes = !it.showGridAndAxes) }
    }

    fun updateFluidParameters(
        smoothingRadius: Float = _uiState.value.fluidSmoothingRadius,
        gasStiffness: Float = _uiState.value.fluidGasStiffness,
        viscosity: Float = _uiState.value.fluidViscosity,
        restitution: Float = _uiState.value.fluidRestitution,
        vortexStrength: Float = _uiState.value.fluidVortexStrength,
        continuousJets: Boolean = _uiState.value.fluidContinuousJets
    ) {
        fluidEngine.smoothingRadius = smoothingRadius
        fluidEngine.gasStiffness = gasStiffness
        fluidEngine.dynamicViscosity = viscosity
        fluidEngine.restitution = restitution
        fluidEngine.vortexStrength = vortexStrength
        fluidEngine.continuousEmitterActive = continuousJets
        _uiState.update {
            it.copy(
                fluidSmoothingRadius = smoothingRadius,
                fluidGasStiffness = gasStiffness,
                fluidViscosity = viscosity,
                fluidRestitution = restitution,
                fluidVortexStrength = vortexStrength,
                fluidContinuousJets = continuousJets
            )
        }
    }

    fun updateClothParameters(
        structuralStiffness: Float = _uiState.value.clothStructuralStiffness,
        bendStiffness: Float = _uiState.value.clothBendStiffness,
        solverIterations: Int = _uiState.value.clothSolverIterations,
        aeroDrag: Float = _uiState.value.clothAerodynamicDrag,
        tearable: Boolean = _uiState.value.clothTearable,
        tearThreshold: Float = _uiState.value.clothTearThreshold
    ) {
        clothEngine.structuralStiffness = structuralStiffness
        clothEngine.bendStiffness = bendStiffness
        clothEngine.solverIterations = solverIterations
        clothEngine.dragCoefficient = aeroDrag
        clothEngine.tearableEnabled = tearable
        clothEngine.tearStrainThreshold = tearThreshold
        _uiState.update {
            it.copy(
                clothStructuralStiffness = structuralStiffness,
                clothBendStiffness = bendStiffness,
                clothSolverIterations = solverIterations,
                clothAerodynamicDrag = aeroDrag,
                clothTearable = tearable,
                clothTearThreshold = tearThreshold
            )
        }
    }

    fun updateGravityParameters(
        constantG: Float = _uiState.value.gravityConstantG,
        softening: Float = _uiState.value.gravitySoftening,
        accretion: Boolean = _uiState.value.gravityAccretionMerger,
        precession: Float = _uiState.value.gravityRelativisticPrecession
    ) {
        gravityEngine.gravitationalConstantG = constantG
        gravityEngine.softeningEpsilon = softening
        gravityEngine.accretionCoalescence = accretion
        gravityEngine.relativisticPrecession = precession
        _uiState.update {
            it.copy(
                gravityConstantG = constantG,
                gravitySoftening = softening,
                gravityAccretionMerger = accretion,
                gravityRelativisticPrecession = precession
            )
        }
    }

    fun toggleKeyframeAutomation() {
        _uiState.update { it.copy(keyframeAutomationEnabled = !it.keyframeAutomationEnabled) }
    }

    fun addCurrentStateAsKeyframe() {
        val state = _uiState.value
        val cycleFrame = totalSimulatedFrames % state.maxCacheFrames
        val newKf = VfxKeyframe(
            id = nextKeyframeId++,
            frameNumber = cycleFrame,
            label = "Baked @F$cycleFrame",
            gravityY = state.gravityVector.y,
            windX = state.windVector.x,
            vortexBoost = state.fluidVortexStrength
        )
        _uiState.update {
            val updated = (it.keyframes.filterNot { k -> abs(k.frameNumber - cycleFrame) < 6 } + newKf)
                .sortedBy { k -> k.frameNumber }
            it.copy(keyframes = updated)
        }
    }

    fun removeKeyframe(id: Int) {
        _uiState.update { state ->
            state.copy(keyframes = state.keyframes.filterNot { it.id == id })
        }
    }

    fun advanceSimulationStep(frameDt: Float, forceStep: Boolean = false) {
        val state = _uiState.value
        if (!state.isRunning && !forceStep) return

        val cycleFrame = totalSimulatedFrames % state.maxCacheFrames
        var effectiveGravity = state.gravityVector
        var effectiveWind = state.windVector

        // Evaluate keyframe curve interpolation if automation is active and not overridden by tilt sensor
        if (state.keyframeAutomationEnabled && !state.deviceTiltGravityEnabled && state.keyframes.size >= 2) {
            val sorted = state.keyframes.sortedBy { it.frameNumber }
            val prevKf = sorted.lastOrNull { it.frameNumber <= cycleFrame } ?: sorted.last()
            val nextKf = sorted.firstOrNull { it.frameNumber > cycleFrame } ?: sorted.first()
            val span = if (nextKf.frameNumber > prevKf.frameNumber) {
                (nextKf.frameNumber - prevKf.frameNumber).toFloat()
            } else {
                (state.maxCacheFrames - prevKf.frameNumber + nextKf.frameNumber).toFloat()
            }.coerceAtLeast(1f)
            val dist = if (cycleFrame >= prevKf.frameNumber) {
                (cycleFrame - prevKf.frameNumber).toFloat()
            } else {
                (state.maxCacheFrames - prevKf.frameNumber + cycleFrame).toFloat()
            }
            val alpha = (dist / span).coerceIn(0f, 1f)
            val interpGy = prevKf.gravityY + (nextKf.gravityY - prevKf.gravityY) * alpha
            val interpWx = prevKf.windX + (nextKf.windX - prevKf.windX) * alpha
            effectiveGravity = effectiveGravity.copy(y = interpGy)
            effectiveWind = effectiveWind.copy(
                x = interpWx,
                z = sin(totalSimTime * 1.8f) * 1.1f
            )
        }

        val substeps = state.substeps.coerceIn(1, 5)
        val subDt = (frameDt.coerceIn(0.008f, 0.025f) * state.timeScale) / substeps

        for (s in 0 until substeps) {
            when (state.solverMode) {
                SolverMode.FLUID_SPH -> {
                    fluidEngine.step(
                        dt = subDt,
                        gravity = effectiveGravity,
                        wind = effectiveWind,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.72f
                    )
                }
                SolverMode.CLOTH_VERLET -> {
                    clothEngine.step(
                        dt = subDt,
                        gravity = effectiveGravity,
                        wind = effectiveWind,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.68f
                    )
                }
                SolverMode.GRAVITY_NBODY -> {
                    gravityEngine.step(
                        dt = subDt,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.95f
                    )
                }
                SolverMode.COUPLED_VFX -> {
                    // Advance all three mathematical solvers and couple their forces
                    gravityEngine.step(
                        dt = subDt,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.85f
                    )
                    val primaryAttractor = gravityEngine.snapshot().firstOrNull()?.position ?: Vec3(0f, -0.3f, 0f)
                    clothEngine.colliderEnabled = true
                    clothEngine.colliderCenter = primaryAttractor * 0.45f
                    clothEngine.colliderRadius = 0.36f
                    fluidEngine.obstacleEnabled = true
                    fluidEngine.obstacleCenter = clothEngine.colliderCenter
                    fluidEngine.obstacleRadius = 0.36f

                    clothEngine.step(
                        dt = subDt,
                        gravity = effectiveGravity,
                        wind = effectiveWind,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.65f
                    )
                    fluidEngine.step(
                        dt = subDt,
                        gravity = effectiveGravity,
                        wind = effectiveWind,
                        externalForceCenter = state.activeBrushWorldPos,
                        externalForceDir = state.activeBrushWorldDir,
                        externalForceRadius = 0.72f
                    )
                }
            }
            totalSimTime += subDt
        }

        totalSimulatedFrames++
        captureCurrentSnapshotToCache()
    }

    private fun captureCurrentSnapshotToCache() {
        val state = _uiState.value
        val mode = state.solverMode

        val fluidSnap = if (mode == SolverMode.FLUID_SPH || mode == SolverMode.COUPLED_VFX) {
            fluidEngine.snapshot()
        } else emptyList()

        val clothNodes = if (mode == SolverMode.CLOTH_VERLET || mode == SolverMode.COUPLED_VFX) {
            clothEngine.snapshotNodes()
        } else emptyList()

        val clothLinks = if (mode == SolverMode.CLOTH_VERLET || mode == SolverMode.COUPLED_VFX) {
            clothEngine.snapshotStructuralLinks()
        } else emptyList()

        val clothQuads = if (mode == SolverMode.CLOTH_VERLET || mode == SolverMode.COUPLED_VFX) {
            clothEngine.snapshotQuads()
        } else emptyList()

        val gravityBodies = if (mode == SolverMode.GRAVITY_NBODY || mode == SolverMode.COUPLED_VFX) {
            gravityEngine.snapshot()
        } else emptyList()

        // Compute rigorous physical telemetry (Kinetic Energy, Potential Energy, Momentum, CFL)
        var ek = 0f
        var ep = 0f
        var px = 0f
        var py = 0f
        var pz = 0f
        var maxV = 0f
        var peakStress = 0f
        val gMag = abs(state.gravityVector.y)

        for (p in fluidSnap) {
            val vSq = p.velocity.lengthSq()
            val v = sqrt(vSq)
            if (v > maxV) maxV = v
            if (p.pressure > peakStress) peakStress = p.pressure
            ek += 0.5f * vSq
            ep += gMag * (p.position.y + 1.35f).coerceAtLeast(0f) * 0.15f
            px += p.velocity.x
            py += p.velocity.y
            pz += p.velocity.z
        }

        for (n in clothNodes) {
            val vSq = n.velocity.lengthSq()
            val v = sqrt(vSq)
            if (v > maxV) maxV = v
            if (n.maxStrain > peakStress) peakStress = n.maxStrain
            ek += 0.5f * vSq * 0.25f
            ep += gMag * (n.position.y + 1.25f).coerceAtLeast(0f) * 0.08f
            px += n.velocity.x * 0.25f
            py += n.velocity.y * 0.25f
            pz += n.velocity.z * 0.25f
        }

        if (gravityBodies.isNotEmpty()) {
            for (b in gravityBodies) {
                val vSq = b.velocity.lengthSq()
                val v = sqrt(vSq)
                if (v > maxV) maxV = v
                val aMag = b.acceleration.length()
                if (aMag > peakStress) peakStress = aMag
                ek += 0.5f * b.mass * vSq
                px += b.mass * b.velocity.x
                py += b.mass * b.velocity.y
                pz += b.mass * b.velocity.z
            }
            ep += gravityEngine.computePotentialEnergy()
        }

        val totalE = ek + abs(ep)
        if (energyHistoryDeque.size >= 60) energyHistoryDeque.removeFirst()
        energyHistoryDeque.addLast(totalE)

        val momentumMag = sqrt(px * px + py * py + pz * pz)
        val dx = when (mode) {
            SolverMode.FLUID_SPH -> state.fluidSmoothingRadius * 0.5f
            SolverMode.CLOTH_VERLET -> 0.15f
            SolverMode.GRAVITY_NBODY -> state.gravitySoftening
            SolverMode.COUPLED_VFX -> 0.16f
        }.coerceAtLeast(0.05f)
        val dtSub = (1f / 60f) * state.timeScale / state.substeps.coerceAtLeast(1)
        val cfl = (maxV * dtSub) / dx

        val activeCount = fluidSnap.size + clothNodes.size + gravityBodies.size
        val constraintCount = when (mode) {
            SolverMode.FLUID_SPH -> fluidSnap.size * 6
            SolverMode.CLOTH_VERLET -> clothEngine.activeConstraintCount()
            SolverMode.GRAVITY_NBODY -> (gravityBodies.size * max(0, gravityBodies.size - 1)) / 2
            SolverMode.COUPLED_VFX -> clothEngine.activeConstraintCount() + fluidSnap.size * 4
        }

        val telemetry = SimulationTelemetry(
            frameIndex = totalSimulatedFrames,
            simTimeSeconds = totalSimTime,
            kineticEnergy = ek,
            potentialEnergy = ep,
            totalEnergy = ek + ep,
            linearMomentumMag = momentumMag,
            maxSpeed = maxV,
            cflNumber = cfl,
            peakStressOrPressure = peakStress,
            activeElementsCount = activeCount,
            activeConstraintsCount = constraintCount,
            energyHistory = energyHistoryDeque.toList()
        )

        val obstacleCenter = when (mode) {
            SolverMode.CLOTH_VERLET -> clothEngine.colliderCenter
            else -> fluidEngine.obstacleCenter
        }
        val obstacleRadius = when (mode) {
            SolverMode.CLOTH_VERLET -> clothEngine.colliderRadius
            else -> fluidEngine.obstacleRadius
        }
        val obstacleVisible = when (mode) {
            SolverMode.FLUID_SPH -> fluidEngine.obstacleEnabled
            SolverMode.CLOTH_VERLET -> clothEngine.colliderEnabled
            SolverMode.GRAVITY_NBODY -> false
            SolverMode.COUPLED_VFX -> true
        }

        val snapshot = SimulationFrameCache(
            frameIndex = totalSimulatedFrames,
            simTimeSeconds = totalSimTime,
            fluidParticles = fluidSnap,
            clothNodes = clothNodes,
            clothLinks = clothLinks,
            clothQuads = clothQuads,
            celestialBodies = gravityBodies,
            obstacleCenter = obstacleCenter,
            obstacleRadius = obstacleRadius,
            obstacleVisible = obstacleVisible,
            telemetry = telemetry
        )

        if (frameRingBuffer.size >= state.maxCacheFrames) {
            frameRingBuffer.removeAt(0)
        }
        frameRingBuffer.add(snapshot)

        _uiState.update {
            it.copy(
                cachedFramesCount = frameRingBuffer.size,
                currentScrubSlot = frameRingBuffer.size - 1,
                currentFrame = snapshot
            )
        }
    }
}
