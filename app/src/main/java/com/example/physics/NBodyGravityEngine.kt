package com.example.physics

import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CelestialBodySnapshot(
    val id: Int,
    val name: String,
    val mass: Float,
    val radius: Float,
    val position: Vec3,
    val velocity: Vec3,
    val acceleration: Vec3,
    val colorCategory: Int,
    val trail: List<Vec3>
)

class NBodyGravityEngine {
    private class MutableBody(
        val id: Int,
        var name: String,
        var mass: Float,
        var radius: Float,
        var pos: Vec3,
        var vel: Vec3,
        var acc: Vec3 = Vec3(),
        var colorCategory: Int = 0,
        val trail: ArrayDeque<Vec3> = ArrayDeque(36),
        var absorbed: Boolean = false
    )

    private val bodies = ArrayList<MutableBody>(48)
    var gravitationalConstantG: Float = 0.85f
    var softeningEpsilon: Float = 0.14f
    var accretionCoalescence: Boolean = false
    var relativisticPrecession: Float = 0.0f
    private var trailCounter = 0
    private var nextId = 100

    init {
        loadPreset(0)
    }

    fun loadPreset(presetIndex: Int) {
        bodies.clear()
        trailCounter = 0
        when (presetIndex) {
            0 -> {
                // Binary Star Accretion Disk
                accretionCoalescence = false
                val starMass = 5.2f
                val sep = 0.56f
                val vOrb = sqrt(gravitationalConstantG * starMass / (4f * sep)) * 1.05f
                bodies.add(
                    MutableBody(
                        id = 0,
                        name = "Alpha Primary",
                        mass = starMass,
                        radius = 0.17f,
                        pos = Vec3(-sep, 0f, 0f),
                        vel = Vec3(0f, 0.12f, vOrb),
                        colorCategory = 0
                    )
                )
                bodies.add(
                    MutableBody(
                        id = 1,
                        name = "Beta Companion",
                        mass = starMass,
                        radius = 0.17f,
                        pos = Vec3(sep, 0f, 0f),
                        vel = Vec3(0f, -0.12f, -vOrb),
                        colorCategory = 1
                    )
                )
                // Protoplanetary 3D disk particles
                val totalCentralMass = starMass * 2f
                for (i in 0 until 26) {
                    val r = 0.95f + (i % 6) * 0.15f
                    val angle = i * 0.68f
                    val inclination = sin(i * 1.3f) * 0.22f
                    val speed = sqrt(gravitationalConstantG * totalCentralMass / r)
                    val pos = Vec3(cos(angle) * r, inclination, sin(angle) * r)
                    val vel = Vec3(-sin(angle) * speed, cos(angle * 2f) * 0.15f, cos(angle) * speed)
                    bodies.add(
                        MutableBody(
                            id = i + 2,
                            name = "Planetesimal-${i + 1}",
                            mass = 0.08f + (i % 3) * 0.04f,
                            radius = 0.055f + (i % 3) * 0.012f,
                            pos = pos,
                            vel = vel,
                            colorCategory = 2 + (i % 2)
                        )
                    )
                }
            }
            1 -> {
                // Keplerian Planetary Ring System
                accretionCoalescence = false
                val sunMass = 11.5f
                bodies.add(
                    MutableBody(
                        id = 0,
                        name = "Sol Singularity",
                        mass = sunMass,
                        radius = 0.22f,
                        pos = Vec3(0f, 0f, 0f),
                        vel = Vec3(0f, 0f, 0f),
                        colorCategory = 0
                    )
                )
                for (i in 0 until 28) {
                    val r = 0.52f + i * 0.048f
                    val angle = i * 0.85f
                    val tilt = if (i % 4 == 0) 0.28f else 0.05f
                    val v = sqrt(gravitationalConstantG * sunMass / r)
                    val p = Vec3(cos(angle) * r, sin(angle) * r * tilt, sin(angle) * r)
                    val vel = Vec3(-sin(angle) * v, cos(angle) * v * tilt, cos(angle) * v)
                    val isGasGiant = i == 7 || i == 17
                    bodies.add(
                        MutableBody(
                            id = i + 1,
                            name = if (isGasGiant) "Jovian-$i" else "RingBody-$i",
                            mass = if (isGasGiant) 0.65f else 0.06f,
                            radius = if (isGasGiant) 0.11f else 0.05f,
                            pos = p,
                            vel = vel,
                            colorCategory = if (isGasGiant) 1 else 2 + (i % 2)
                        )
                    )
                }
            }
            2 -> {
                // Figure-8 Three-Body Choreography (Chenciner-Montgomery solution scaled in 3D)
                accretionCoalescence = false
                val m = 4.2f
                val scalePos = 1.05f
                val scaleVel = sqrt(gravitationalConstantG * m / scalePos)
                val p1 = Vec3(0.97000436f * scalePos, -0.24308753f * scalePos, 0.12f)
                val p2 = Vec3(-0.97000436f * scalePos, 0.24308753f * scalePos, -0.12f)
                val p3 = Vec3(0f, 0f, 0f)
                val v3 = Vec3(-0.93240737f * scaleVel, -0.86473146f * scaleVel, 0.18f)
                val v1 = v3 * -0.5f
                val v2 = v3 * -0.5f
                bodies.add(MutableBody(0, "Body Alpha", m, 0.15f, p1, v1, colorCategory = 0))
                bodies.add(MutableBody(1, "Body Beta", m, 0.15f, p2, v2, colorCategory = 1))
                bodies.add(MutableBody(2, "Body Gamma", m, 0.15f, p3, v3, colorCategory = 2))

                // Add light asteroid dust around the 3-body system
                for (i in 0 until 16) {
                    val ang = i * 0.39f
                    val r = 1.45f
                    val v = sqrt(gravitationalConstantG * (3f * m) / r)
                    bodies.add(
                        MutableBody(
                            id = i + 3,
                            name = "Halo-${i + 1}",
                            mass = 0.03f,
                            radius = 0.048f,
                            pos = Vec3(cos(ang) * r, sin(ang * 2f) * 0.2f, sin(ang) * r),
                            vel = Vec3(-sin(ang) * v, 0f, cos(ang) * v),
                            colorCategory = 3
                        )
                    )
                }
            }
            else -> {
                // Globular Cluster Virial Collapse & Accretion
                accretionCoalescence = true
                for (i in 0 until 32) {
                    val phi = i * 2.39996f
                    val r = 0.35f + (i % 7) * 0.17f
                    val y = ((i % 9) - 4) * 0.22f
                    val pos = Vec3(cos(phi) * r, y, sin(phi) * r)
                    val swirl = Vec3(-sin(phi) * 0.85f, cos(phi * 1.5f) * 0.25f, cos(phi) * 0.85f)
                    val mass = if (i < 3) 2.2f else 0.25f + (i % 4) * 0.12f
                    val rad = 0.055f * cbrt(mass * 4f)
                    bodies.add(
                        MutableBody(
                            id = i,
                            name = "Star-${i + 1}",
                            mass = mass,
                            radius = rad,
                            pos = pos,
                            vel = swirl,
                            colorCategory = i % 4
                        )
                    )
                }
            }
        }
        computeAccelerations(null, Vec3(), 0f)
    }

    private fun computeAccelerations(
        externalForceCenter: Vec3?,
        externalForceDir: Vec3,
        externalForceRadius: Float
    ) {
        val epsSq = softeningEpsilon * softeningEpsilon
        val n = bodies.size
        for (i in 0 until n) {
            bodies[i].acc = Vec3()
        }

        for (i in 0 until n) {
            val bi = bodies[i]
            if (bi.absorbed) continue
            for (j in i + 1 until n) {
                val bj = bodies[j]
                if (bj.absorbed) continue
                val delta = bj.pos - bi.pos
                val distSq = delta.lengthSq() + epsSq
                val invDist = 1f / sqrt(distSq)
                val invDist3 = invDist * invDist * invDist

                // Optional post-Newtonian relativistic 1/r^4 correction term
                val relBoost = 1f + relativisticPrecession * invDist
                val forceOverMassProduct = gravitationalConstantG * invDist3 * relBoost
                val fVec = delta * forceOverMassProduct

                bi.acc = bi.acc + fVec * bj.mass
                bj.acc = bj.acc - fVec * bi.mass
            }

            if (externalForceCenter != null) {
                val toCursor = externalForceCenter - bi.pos
                val d = toCursor.length()
                if (d < externalForceRadius && d > 1e-4f) {
                    val falloff = 1f - d / externalForceRadius
                    // Gravitational well attractor + directional brush
                    bi.acc = bi.acc + toCursor.normalized() * (falloff * 14f) + externalForceDir * (falloff * 18f)
                }
            }
        }
    }

    fun step(
        dt: Float,
        externalForceCenter: Vec3?,
        externalForceDir: Vec3,
        externalForceRadius: Float
    ) {
        val safeDt = dt.coerceIn(0.004f, 0.025f)
        trailCounter++

        // Symplectic Velocity-Verlet Step 1: Half-step velocity & full-step position
        for (b in bodies) {
            if (b.absorbed) continue
            val halfVel = b.vel + b.acc * (0.5f * safeDt)
            b.pos = b.pos + halfVel * safeDt
            b.vel = halfVel

            // Soft spherical containment if body drifts too far (> 3.8 units)
            val r = b.pos.length()
            if (r > 3.6f) {
                val normal = b.pos / r
                b.pos = normal * 3.6f
                val vn = b.vel.dot(normal)
                if (vn > 0f) {
                    b.vel = b.vel - normal * (1.7f * vn)
                }
            }

            if (trailCounter % 2 == 0) {
                if (b.trail.size >= 32) b.trail.removeFirst()
                b.trail.addLast(b.pos)
            }
        }

        // Step 2: Resolve Collisions (Accretion Coalescence or Momentum-Conserving Elastic Bounce)
        val n = bodies.size
        for (i in 0 until n) {
            val bi = bodies[i]
            if (bi.absorbed) continue
            for (j in i + 1 until n) {
                val bj = bodies[j]
                if (bj.absorbed) continue
                val delta = bj.pos - bi.pos
                val dist = delta.length()
                val minDist = (bi.radius + bj.radius) * 0.85f
                if (dist < minDist && dist > 1e-5f) {
                    if (accretionCoalescence && bodies.count { !it.absorbed } > 6) {
                        // Inelastic merger conserving linear momentum: (m1 v1 + m2 v2) / (m1 + m2)
                        val primary = if (bi.mass >= bj.mass) bi else bj
                        val secondary = if (bi.mass >= bj.mass) bj else bi
                        val totalMass = primary.mass + secondary.mass
                        primary.vel = (primary.vel * primary.mass + secondary.vel * secondary.mass) / totalMass
                        primary.pos = (primary.pos * primary.mass + secondary.pos * secondary.mass) / totalMass
                        primary.mass = totalMass
                        primary.radius = (primary.radius.pow(3) + secondary.radius.pow(3)).pow(1f / 3f)
                        secondary.absorbed = true
                    } else {
                        // Elastic 3D sphere impulse conserving momentum & kinetic energy
                        val normal = delta / dist
                        val overlap = minDist - dist
                        val invM1 = 1f / bi.mass
                        val invM2 = 1f / bj.mass
                        val invSum = invM1 + invM2
                        bi.pos = bi.pos - normal * (overlap * (invM1 / invSum))
                        bj.pos = bj.pos + normal * (overlap * (invM2 / invSum))

                        val relVel = bj.vel - bi.vel
                        val sepVel = relVel.dot(normal)
                        if (sepVel < 0f) {
                            val restitution = 0.78f
                            val jImpulse = -(1f + restitution) * sepVel / invSum
                            val impulseVec = normal * jImpulse
                            bi.vel = bi.vel - impulseVec * invM1
                            bj.vel = bj.vel + impulseVec * invM2
                        }
                    }
                }
            }
        }

        // Symplectic Velocity-Verlet Step 3: Recompute accelerations & complete half-step velocity
        computeAccelerations(externalForceCenter, externalForceDir, externalForceRadius)
        for (b in bodies) {
            if (b.absorbed) continue
            b.vel = b.vel + b.acc * (0.5f * safeDt)
        }
    }

    fun spawnCelestialBody(pos: Vec3, vel: Vec3, mass: Float = 0.85f) {
        if (bodies.count { !it.absorbed } >= 44) {
            val lightest = bodies.filter { !it.absorbed }.minByOrNull { it.mass }
            lightest?.absorbed = true
        }
        val rad = (0.06f * cbrt(mass * 3f)).coerceIn(0.05f, 0.20f)
        bodies.add(
            MutableBody(
                id = nextId++,
                name = "Rogue-${nextId}",
                mass = mass,
                radius = rad,
                pos = pos,
                vel = vel,
                colorCategory = nextId % 4
            )
        )
    }

    fun applySupernovaKick(center: Vec3, strength: Float = 2.8f) {
        for (b in bodies) {
            if (b.absorbed) continue
            val delta = b.pos - center
            val d = delta.length().coerceAtLeast(0.25f)
            b.vel = b.vel + (delta / d) * (strength / (0.5f + d))
        }
    }

    fun computePotentialEnergy(): Float {
        var ep = 0f
        val epsSq = softeningEpsilon * softeningEpsilon
        val active = bodies.filter { !it.absorbed }
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val r = sqrt((active[i].pos - active[j].pos).lengthSq() + epsSq)
                ep -= gravitationalConstantG * active[i].mass * active[j].mass / r
            }
        }
        return ep
    }

    fun snapshot(): List<CelestialBodySnapshot> =
        bodies.filter { !it.absorbed }.map { b ->
            CelestialBodySnapshot(
                id = b.id,
                name = b.name,
                mass = b.mass,
                radius = b.radius,
                position = b.pos,
                velocity = b.vel,
                acceleration = b.acc,
                colorCategory = b.colorCategory,
                trail = b.trail.toList()
            )
        }
}
