package com.example

import com.example.physics.Camera3D
import com.example.physics.NBodyGravityEngine
import com.example.physics.SimulationViewModel
import com.example.physics.SolverMode
import com.example.physics.SphFluidEngine
import com.example.physics.Vec3
import com.example.physics.VerletClothEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun sphFluidSolver_computesDensityPressureAndAdvectsUnderGravity() {
        val engine = SphFluidEngine()
        val initial = engine.snapshot()
        assertTrue("Expected SPH particles initialized", initial.size > 100)

        val avgYBefore = initial.map { it.position.y }.average()
        repeat(8) {
            engine.step(
                dt = 0.012f,
                gravity = Vec3(0f, -9.81f, 0f),
                wind = Vec3(1.5f, 0f, 0f),
                externalForceCenter = null,
                externalForceDir = Vec3(),
                externalForceRadius = 0.6f
            )
        }
        val after = engine.snapshot()
        val avgYAfter = after.map { it.position.y }.average()
        assertTrue("Fluid center of mass should respond to gravity and pressure", avgYAfter != avgYBefore)
        assertTrue("Densities must be positive", after.all { it.density > 0f })
    }

    @Test
    fun verletClothSolver_maintainsPinnedNodesAndComputesStrain() {
        val cloth = VerletClothEngine()
        cloth.loadPreset(0)
        val initialNodes = cloth.snapshotNodes()
        val pinnedBefore = initialNodes.filter { it.pinned }
        assertTrue("Expected pinned cloth anchor vertices", pinnedBefore.isNotEmpty())

        repeat(6) {
            cloth.step(
                dt = 0.014f,
                gravity = Vec3(0f, -9.81f, 0f),
                wind = Vec3(4.5f, 0.2f, 0.5f),
                externalForceCenter = null,
                externalForceDir = Vec3(),
                externalForceRadius = 0.6f
            )
        }

        val afterNodes = cloth.snapshotNodes()
        val pinnedAfter = afterNodes.filter { it.pinned }
        for (i in pinnedBefore.indices) {
            assertEquals(pinnedBefore[i].position.x, pinnedAfter[i].position.x, 1e-5f)
            assertEquals(pinnedBefore[i].position.y, pinnedAfter[i].position.y, 1e-5f)
        }
        assertTrue("Expected active cloth quads", cloth.snapshotQuads().isNotEmpty())
    }

    @Test
    fun nBodyGravitySolver_advancesOrbitsAndTracksPotentialEnergy() {
        val gravity = NBodyGravityEngine()
        gravity.loadPreset(0)
        val ep0 = gravity.computePotentialEnergy()
        assertTrue("Gravitational potential energy should be negative for bound system", ep0 < 0f)

        repeat(10) {
            gravity.step(
                dt = 0.012f,
                externalForceCenter = null,
                externalForceDir = Vec3(),
                externalForceRadius = 0.8f
            )
        }
        val bodies = gravity.snapshot()
        assertTrue("Celestial bodies should record 3D orbital trails", bodies.first().trail.isNotEmpty())
    }

    @Test
    fun viewModel_switchesSolversAndCachesFrames() {
        val vm = SimulationViewModel()
        vm.stepSingleFrameForward()
        vm.stepSingleFrameForward()
        assertEquals(3, vm.uiState.value.cachedFramesCount)

        vm.selectSolverMode(SolverMode.COUPLED_VFX)
        vm.stepSingleFrameForward()
        val frame = vm.uiState.value.currentFrame
        assertTrue("Coupled VFX mode should contain fluid, cloth, and gravity elements",
            frame != null && frame.fluidParticles.isNotEmpty() && frame.clothNodes.isNotEmpty() && frame.celestialBodies.isNotEmpty()
        )

        val cam = Camera3D()
        val proj = cam.project(Vec3(0f, 0f, 0f), 1080f, 1920f)
        assertTrue(proj.visible)
    }
}
