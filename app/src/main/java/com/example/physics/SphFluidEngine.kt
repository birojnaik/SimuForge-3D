package com.example.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class SphParticleSnapshot(
    val id: Int,
    val position: Vec3,
    val velocity: Vec3,
    val density: Float,
    val pressure: Float,
    val vorticityMag: Float
)

class SphFluidEngine {
    private class MutableParticle(
        val id: Int,
        var pos: Vec3,
        var vel: Vec3 = Vec3(),
        var force: Vec3 = Vec3(),
        var density: Float = 1000f,
        var pressure: Float = 0f,
        var vorticity: Float = 0f
    )

    private val particles = ArrayList<MutableParticle>(220)
    var smoothingRadius: Float = 0.34f
    var restDensity: Float = 1000f
    var gasStiffness: Float = 1.85f
    var dynamicViscosity: Float = 0.22f
    var surfaceTension: Float = 0.08f
    var restitution: Float = 0.52f
    var vortexStrength: Float = 0f
    var continuousEmitterActive: Boolean = false
    private var emitterPhase: Float = 0f

    val domainHalfExtents = Vec3(1.45f, 1.35f, 1.15f)
    var obstacleCenter = Vec3(0.25f, -0.55f, 0.0f)
    var obstacleRadius = 0.38f
    var obstacleEnabled = true

    init {
        loadPreset(0)
    }

    fun loadPreset(presetIndex: Int) {
        particles.clear()
        emitterPhase = 0f
        when (presetIndex) {
            0 -> {
                // Dam Break Surge: Tall column of fluid on left + obstacle sphere on right
                vortexStrength = 0f
                continuousEmitterActive = false
                obstacleEnabled = true
                obstacleCenter = Vec3(0.32f, -0.62f, 0.0f)
                obstacleRadius = 0.38f
                var id = 0
                val spacing = 0.165f
                for (ix in 0 until 6) {
                    for (iy in 0 until 10) {
                        for (iz in 0 until 4) {
                            val jitter = ((ix * 17 + iy * 31 + iz * 13) % 10 - 5) * 0.003f
                            val p = Vec3(
                                -1.25f + ix * spacing + jitter,
                                -1.15f + iy * spacing,
                                -0.35f + iz * spacing + jitter
                            )
                            particles.add(MutableParticle(id = id++, pos = p))
                        }
                    }
                }
            }
            1 -> {
                // Colliding Hydro Jets: Dual high-velocity opposing streams colliding in 3D
                vortexStrength = 0.25f
                continuousEmitterActive = true
                obstacleEnabled = true
                obstacleCenter = Vec3(0f, -0.82f, 0f)
                obstacleRadius = 0.34f
                var id = 0
                val spacing = 0.17f
                for (i in 0 until 110) {
                    val leftSide = i % 2 == 0
                    val layer = i / 2
                    val row = layer % 5
                    val col = (layer / 5) % 4
                    val depth = layer / 20
                    val x = if (leftSide) -1.2f + depth * spacing else 1.2f - depth * spacing
                    val y = 0.1f + (row - 2) * spacing * 0.85f
                    val z = (col - 1.5f) * spacing * 0.85f
                    val vx = if (leftSide) 2.4f else -2.4f
                    val vy = 0.35f
                    particles.add(
                        MutableParticle(
                            id = id++,
                            pos = Vec3(x, y, z),
                            vel = Vec3(vx, vy, 0f)
                        )
                    )
                }
            }
            2 -> {
                // Centrifugal Vortex Tank: Swirling whirlpool funnel
                vortexStrength = 2.6f
                continuousEmitterActive = false
                obstacleEnabled = false
                var id = 0
                val count = 216
                for (i in 0 until count) {
                    val angle = i * 0.42f
                    val ring = 0.25f + (i % 7) * 0.14f
                    val height = -1.1f + (i / 24) * 0.19f
                    val x = cos(angle) * ring
                    val z = sin(angle) * ring
                    val tangentVel = Vec3(-sin(angle) * 2.1f, 0f, cos(angle) * 2.1f)
                    particles.add(
                        MutableParticle(
                            id = id++,
                            pos = Vec3(x, height, z),
                            vel = tangentVel
                        )
                    )
                }
            }
            else -> {
                // Zero-G Coalescence: Two floating fluid globules colliding in microgravity
                vortexStrength = 0.35f
                continuousEmitterActive = false
                obstacleEnabled = false
                var id = 0
                val count = 200
                for (i in 0 until count) {
                    val cluster = if (i < count / 2) -1 else 1
                    val idx = i % (count / 2)
                    val phi = idx * 2.39996f
                    val r = 0.42f * sqrt((idx + 1f) / (count / 2f))
                    val theta = (idx % 9) * 0.35f
                    val local = Vec3(
                        r * cos(phi) * sin(theta),
                        r * sin(phi) * sin(theta),
                        r * cos(theta)
                    )
                    val center = Vec3(cluster * 0.68f, cluster * 0.18f, 0f)
                    val initVel = Vec3(-cluster * 1.15f, -cluster * 0.25f, 0f)
                    particles.add(
                        MutableParticle(
                            id = id++,
                            pos = center + local,
                            vel = initVel
                        )
                    )
                }
            }
        }
    }

    fun step(
        dt: Float,
        gravity: Vec3,
        wind: Vec3,
        externalForceCenter: Vec3?,
        externalForceDir: Vec3,
        externalForceRadius: Float
    ) {
        val h = smoothingRadius.coerceIn(0.20f, 0.55f)
        val h2 = h * h
        val mass = 1.0f

        // Analytical 3D SPH Kernel Normalization Constants (Müller et al. 2003)
        val poly6Coeff = (315.0 / (64.0 * PI * h.toDouble().pow(9.0))).toFloat()
        val spikyGradCoeff = (-45.0 / (PI * h.toDouble().pow(6.0))).toFloat()
        val viscLaplacianCoeff = (45.0 / (PI * h.toDouble().pow(6.0))).toFloat()

        val n = particles.size
        emitterPhase += dt

        // Optional jet recycling when continuousEmitterActive is enabled
        if (continuousEmitterActive && n > 10) {
            val idx = ((emitterPhase * 60f).toInt().coerceAtLeast(0)) % n
            val p = particles[idx]
            if (p.pos.y < -domainHalfExtents.y + 0.28f) {
                val side = if (idx % 2 == 0) -1f else 1f
                p.pos = Vec3(
                    side * (domainHalfExtents.x - 0.15f),
                    0.55f + sin(emitterPhase * 4f + idx) * 0.12f,
                    cos(emitterPhase * 3f + idx) * 0.22f
                )
                p.vel = Vec3(-side * 2.5f, 0.25f, 0f)
            }
        }

        // Step 1: Compute 3D SPH Density & State Pressure
        for (i in 0 until n) {
            val pi = particles[i]
            var rho = 0f
            for (j in 0 until n) {
                val pj = particles[j]
                val dx = pi.pos.x - pj.pos.x
                val dy = pi.pos.y - pj.pos.y
                val dz = pi.pos.z - pj.pos.z
                val r2 = dx * dx + dy * dy + dz * dz
                if (r2 < h2) {
                    val diff = h2 - r2
                    rho += mass * poly6Coeff * diff * diff * diff
                }
            }
            pi.density = max(rho, restDensity * 0.25f)
            // Tait / Ideal Gas Equation of State
            pi.pressure = max(0f, gasStiffness * (pi.density - restDensity))
        }

        // Step 2: Compute Navier-Stokes Pressure Gradient, Viscosity, Cohesion & External Forces
        for (i in 0 until n) {
            val pi = particles[i]
            var fPressX = 0f
            var fPressY = 0f
            var fPressZ = 0f
            var fViscX = 0f
            var fViscY = 0f
            var fViscZ = 0f
            var fCohesionX = 0f
            var fCohesionY = 0f
            var fCohesionZ = 0f
            var curlAccum = 0f

            for (j in 0 until n) {
                if (i == j) continue
                val pj = particles[j]
                val dx = pi.pos.x - pj.pos.x
                val dy = pi.pos.y - pj.pos.y
                val dz = pi.pos.z - pj.pos.z
                val r2 = dx * dx + dy * dy + dz * dz
                if (r2 < h2 && r2 > 1e-8f) {
                    val r = sqrt(r2)
                    val invR = 1f / r
                    val hMinusR = h - r

                    // Spiky Kernel Gradient for Pressure
                    val gradMag = spikyGradCoeff * hMinusR * hMinusR
                    val sharedPressure = -mass * (pi.pressure + pj.pressure) / (2f * pj.density) * gradMag
                    fPressX += sharedPressure * (dx * invR)
                    fPressY += sharedPressure * (dy * invR)
                    fPressZ += sharedPressure * (dz * invR)

                    // Viscosity Laplacian Kernel
                    val lap = viscLaplacianCoeff * hMinusR
                    val viscFactor = dynamicViscosity * mass * (lap / pj.density)
                    fViscX += viscFactor * (pj.vel.x - pi.vel.x)
                    fViscY += viscFactor * (pj.vel.y - pi.vel.y)
                    fViscZ += viscFactor * (pj.vel.z - pi.vel.z)

                    // Surface Tension Cohesion
                    val cohFactor = -surfaceTension * mass * (hMinusR / h)
                    fCohesionX += cohFactor * (dx * invR)
                    fCohesionY += cohFactor * (dy * invR)
                    fCohesionZ += cohFactor * (dz * invR)

                    // Local velocity shear / vorticity proxy
                    val dvx = pj.vel.x - pi.vel.x
                    val dvy = pj.vel.y - pi.vel.y
                    val dvz = pj.vel.z - pi.vel.z
                    curlAccum += sqrt(dvx * dvx + dvy * dvy + dvz * dvz) * (hMinusR / h)
                }
            }

            // Swirling centrifugal vortex force around Y axis if enabled
            var vortexForce = Vec3()
            if (vortexStrength > 0.01f) {
                val radial = sqrt(pi.pos.x * pi.pos.x + pi.pos.z * pi.pos.z).coerceAtLeast(0.15f)
                val tx = -pi.pos.z / radial
                val tz = pi.pos.x / radial
                val inwardX = -pi.pos.x * 0.45f
                val inwardZ = -pi.pos.z * 0.45f
                vortexForce = Vec3(
                    (tx * 2.2f + inwardX) * vortexStrength,
                    0f,
                    (tz * 2.2f + inwardZ) * vortexStrength
                )
            }

            // Interactive 3D user force brush
            var userForce = Vec3()
            if (externalForceCenter != null) {
                val toCursor = pi.pos - externalForceCenter
                val dist = toCursor.length()
                if (dist < externalForceRadius && dist > 1e-4f) {
                    val falloff = 1f - (dist / externalForceRadius)
                    userForce = externalForceDir * (falloff * 18f) + toCursor.normalized() * (falloff * 6.5f)
                }
            }

            val totalAcc = Vec3(
                (fPressX + fViscX + fCohesionX) / pi.density + gravity.x + wind.x * 0.25f + vortexForce.x + userForce.x,
                (fPressY + fViscY + fCohesionY) / pi.density + gravity.y + wind.y * 0.25f + vortexForce.y + userForce.y,
                (fPressZ + fViscZ + fCohesionZ) / pi.density + gravity.z + wind.z * 0.25f + vortexForce.z + userForce.z
            )
            pi.force = totalAcc
            pi.vorticity = curlAccum
        }

        // Step 3: Symplectic Euler Integration & 3D Boundary / Obstacle Collisions
        for (i in 0 until n) {
            val p = particles[i]
            var vx = (p.vel.x + p.force.x * dt) * 0.996f
            var vy = (p.vel.y + p.force.y * dt) * 0.996f
            var vz = (p.vel.z + p.force.z * dt) * 0.996f

            // Clamp velocity to maintain CFL stability
            val speedSq = vx * vx + vy * vy + vz * vz
            val maxSpeed = 7.5f
            if (speedSq > maxSpeed * maxSpeed) {
                val s = maxSpeed / sqrt(speedSq)
                vx *= s
                vy *= s
                vz *= s
            }

            var px = p.pos.x + vx * dt
            var py = p.pos.y + vy * dt
            var pz = p.pos.z + vz * dt

            // 3D Domain Container Walls
            val bx = domainHalfExtents.x
            val by = domainHalfExtents.y
            val bz = domainHalfExtents.z
            if (px < -bx) { px = -bx; vx = -vx * restitution }
            if (px > bx) { px = bx; vx = -vx * restitution }
            if (py < -by) { py = -by; vy = -vy * restitution }
            if (py > by) { py = by; vy = -vy * restitution }
            if (pz < -bz) { pz = -bz; vz = -vz * restitution }
            if (pz > bz) { pz = bz; vz = -vz * restitution }

            // 3D Spherical Obstacle Collision
            if (obstacleEnabled) {
                val dx = px - obstacleCenter.x
                val dy = py - obstacleCenter.y
                val dz = pz - obstacleCenter.z
                val distSq = dx * dx + dy * dy + dz * dz
                val minDist = obstacleRadius + 0.05f
                if (distSq < minDist * minDist && distSq > 1e-6f) {
                    val dist = sqrt(distSq)
                    val nx = dx / dist
                    val ny = dy / dist
                    val nz = dz / dist
                    px = obstacleCenter.x + nx * minDist
                    py = obstacleCenter.y + ny * minDist
                    pz = obstacleCenter.z + nz * minDist

                    val vn = vx * nx + vy * ny + vz * nz
                    if (vn < 0f) {
                        val impulse = -(1f + restitution) * vn
                        vx += impulse * nx
                        vy += impulse * ny
                        vz += impulse * nz
                    }
                }
            }

            p.pos = Vec3(px, py, pz)
            p.vel = Vec3(vx, vy, vz)
        }
    }

    fun injectFluidBurst(origin: Vec3, velocity: Vec3) {
        // Re-energize or add up to 240 particles max
        val maxParticles = 240
        for (k in 0 until 12) {
            val offset = Vec3(
                ((k % 3) - 1) * 0.08f,
                ((k / 3) % 2) * 0.08f,
                (k % 2 - 0.5f) * 0.08f
            )
            if (particles.size < maxParticles) {
                particles.add(
                    MutableParticle(
                        id = particles.size,
                        pos = origin + offset,
                        vel = velocity + offset * 3f
                    )
                )
            } else {
                val targetIdx = (particles.size + k * 13) % particles.size
                particles[targetIdx].pos = origin + offset
                particles[targetIdx].vel = velocity + offset * 3f
            }
        }
    }

    fun applyShockwave(center: Vec3, strength: Float = 4.2f) {
        for (p in particles) {
            val delta = p.pos - center
            val d = delta.length().coerceAtLeast(0.15f)
            val dir = delta / d
            val impulse = (strength / (0.6f + d * d)).coerceAtMost(5.5f)
            p.vel = p.vel + dir * impulse + Vec3(0f, impulse * 0.45f, 0f)
        }
    }

    fun snapshot(): List<SphParticleSnapshot> =
        particles.map { p ->
            SphParticleSnapshot(
                id = p.id,
                position = p.pos,
                velocity = p.vel,
                density = p.density,
                pressure = p.pressure,
                vorticityMag = p.vorticity
            )
        }
}
