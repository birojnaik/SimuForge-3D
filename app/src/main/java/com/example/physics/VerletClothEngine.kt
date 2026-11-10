package com.example.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class ClothNodeSnapshot(
    val index: Int,
    val row: Int,
    val col: Int,
    val position: Vec3,
    val velocity: Vec3,
    val normal: Vec3,
    val pinned: Boolean,
    val maxStrain: Float
)

data class ClothLinkSnapshot(
    val indexA: Int,
    val indexB: Int,
    val strain: Float,
    val isStructural: Boolean
)

data class ClothQuadSnapshot(
    val n00: Int,
    val n10: Int,
    val n11: Int,
    val n01: Int,
    val center: Vec3,
    val normal: Vec3,
    val strain: Float
)

class VerletClothEngine {
    private class MutableNode(
        val index: Int,
        val row: Int,
        val col: Int,
        var pos: Vec3,
        var prevPos: Vec3,
        var acc: Vec3 = Vec3(),
        var normal: Vec3 = Vec3(0f, 0f, 1f),
        var pinned: Boolean = false,
        var maxStrain: Float = 0f
    )

    private class MutableConstraint(
        val a: Int,
        val b: Int,
        val restLength: Float,
        val stiffness: Float,
        val isStructural: Boolean,
        var broken: Boolean = false,
        var currentStrain: Float = 0f
    )

    val gridCols = 15
    val gridRows = 13
    private val nodes = ArrayList<MutableNode>(gridCols * gridRows)
    private val constraints = ArrayList<MutableConstraint>(gridCols * gridRows * 4)

    var structuralStiffness: Float = 0.92f
    var shearStiffness: Float = 0.65f
    var bendStiffness: Float = 0.38f
    var solverIterations: Int = 7
    var damping: Float = 0.988f
    var dragCoefficient: Float = 0.55f
    var tearableEnabled: Boolean = false
    var tearStrainThreshold: Float = 0.85f

    var colliderCenter = Vec3(0.0f, -0.32f, 0.12f)
    var colliderRadius = 0.46f
    var colliderEnabled = true
    private var simTime = 0f

    init {
        loadPreset(0)
    }

    private fun idx(r: Int, c: Int): Int = r * gridCols + c

    fun loadPreset(presetIndex: Int) {
        nodes.clear()
        constraints.clear()
        simTime = 0f
        val spacing = 0.155f
        val width = (gridCols - 1) * spacing
        val height = (gridRows - 1) * spacing

        when (presetIndex) {
            0 -> {
                // Wind Tunnel Aero Flag: Pinned along left pole, billowing in 3D wind around collider
                tearableEnabled = false
                colliderEnabled = true
                colliderCenter = Vec3(0.28f, -0.15f, 0.18f)
                colliderRadius = 0.42f
                for (r in 0 until gridRows) {
                    for (c in 0 until gridCols) {
                        val x = -width * 0.52f + c * spacing
                        val y = height * 0.48f - r * spacing
                        val z = sin(c * 0.4f) * 0.04f
                        val p = Vec3(x, y, z)
                        val pinned = (c == 0 && (r == 0 || r == gridRows / 2 || r == gridRows - 1)) ||
                            (r == 0 && (c == 0 || c == gridCols / 2 || c == gridCols - 1))
                        nodes.add(
                            MutableNode(
                                index = idx(r, c),
                                row = r,
                                col = c,
                                pos = p,
                                prevPos = p,
                                pinned = pinned
                            )
                        )
                    }
                }
            }
            1 -> {
                // Sphere Drape & Canopy: Horizontal sheet dropping onto a 3D sphere
                tearableEnabled = false
                colliderEnabled = true
                colliderCenter = Vec3(0.0f, -0.35f, 0.0f)
                colliderRadius = 0.52f
                for (r in 0 until gridRows) {
                    for (c in 0 until gridCols) {
                        val x = -width * 0.5f + c * spacing
                        val z = -height * 0.5f + r * spacing
                        val y = 0.68f
                        val p = Vec3(x, y, z)
                        nodes.add(
                            MutableNode(
                                index = idx(r, c),
                                row = r,
                                col = c,
                                pos = p,
                                prevPos = p,
                                pinned = false
                            )
                        )
                    }
                }
            }
            2 -> {
                // Suspension Hammock: 4 corners pinned in 3D space with center sag
                tearableEnabled = false
                colliderEnabled = true
                colliderCenter = Vec3(0.0f, 0.25f, 0.0f)
                colliderRadius = 0.36f
                for (r in 0 until gridRows) {
                    for (c in 0 until gridCols) {
                        val x = -width * 0.5f + c * spacing
                        val z = -height * 0.5f + r * spacing
                        val y = 0.32f
                        val p = Vec3(x, y, z)
                        val isCorner = (r == 0 || r == gridRows - 1) && (c == 0 || c == gridCols - 1)
                        nodes.add(
                            MutableNode(
                                index = idx(r, c),
                                row = r,
                                col = c,
                                pos = p,
                                prevPos = p,
                                pinned = isCorner
                            )
                        )
                    }
                }
            }
            else -> {
                // High-Tension Tearable Curtain: Top row pinned, ready to fracture under stress
                tearableEnabled = true
                tearStrainThreshold = 0.68f
                colliderEnabled = true
                colliderCenter = Vec3(0.0f, -0.22f, 0.28f)
                colliderRadius = 0.44f
                for (r in 0 until gridRows) {
                    for (c in 0 until gridCols) {
                        val x = -width * 0.5f + c * spacing
                        val y = 0.95f - r * spacing
                        val z = 0f
                        val p = Vec3(x, y, z)
                        val pinned = r == 0 && (c % 2 == 0 || c == gridCols - 1)
                        nodes.add(
                            MutableNode(
                                index = idx(r, c),
                                row = r,
                                col = c,
                                pos = p,
                                prevPos = p,
                                pinned = pinned
                            )
                        )
                    }
                }
            }
        }

        // Build Structural, Shear, and Bending Hookean Constraints
        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val cur = idx(r, c)
                // Structural Right & Down
                if (c + 1 < gridCols) addConstraint(cur, idx(r, c + 1), structuralStiffness, true)
                if (r + 1 < gridRows) addConstraint(cur, idx(r + 1, c), structuralStiffness, true)

                // Shear Diagonals
                if (r + 1 < gridRows && c + 1 < gridCols) {
                    addConstraint(cur, idx(r + 1, c + 1), shearStiffness, false)
                    addConstraint(idx(r + 1, c), idx(r, c + 1), shearStiffness, false)
                }

                // Bending 2-hop springs
                if (c + 2 < gridCols) addConstraint(cur, idx(r, c + 2), bendStiffness, false)
                if (r + 2 < gridRows) addConstraint(cur, idx(r + 2, c), bendStiffness, false)
            }
        }
        recomputeNormals()
    }

    private fun addConstraint(a: Int, b: Int, stiffness: Float, isStructural: Boolean) {
        val rest = nodes[a].pos.distanceTo(nodes[b].pos)
        constraints.add(
            MutableConstraint(
                a = a,
                b = b,
                restLength = rest,
                stiffness = stiffness,
                isStructural = isStructural
            )
        )
    }

    fun step(
        dt: Float,
        gravity: Vec3,
        wind: Vec3,
        externalForceCenter: Vec3?,
        externalForceDir: Vec3,
        externalForceRadius: Float
    ) {
        simTime += dt
        val safeDt = dt.coerceIn(0.004f, 0.025f)
        val dtSq = safeDt * safeDt

        // Step 1: Compute Vertex Normals & Aerodynamic Wind Lift/Drag per face
        recomputeNormals()

        for (node in nodes) {
            node.acc = gravity
            node.maxStrain = 0f

            // Curl-noise turbulent wind field + normal-projected aerodynamic pressure
            val turb = FieldMath.sampleCurlNoise3D(node.pos, simTime, 1.5f) * 0.75f
            val localWind = wind + turb
            val vel = (node.pos - node.prevPos) / safeDt
            val relFlow = localWind - vel
            val normalProj = relFlow.dot(node.normal)
            val aeroForce = node.normal * (normalProj * dragCoefficient * 2.4f) + relFlow * (dragCoefficient * 0.45f)
            node.acc = node.acc + aeroForce

            // Interactive 3D Force Brush
            if (externalForceCenter != null) {
                val toNode = node.pos - externalForceCenter
                val dist = toNode.length()
                if (dist < externalForceRadius) {
                    val falloff = 1f - dist / externalForceRadius
                    node.acc = node.acc + externalForceDir * (falloff * 42f)
                    if (tearableEnabled && falloff > 0.62f && externalForceDir.length() > 0.75f) {
                        // High-impulse brush tears nearby constraints
                        for (con in constraints) {
                            if (con.a == node.index || con.b == node.index) {
                                con.broken = true
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Position-Based Verlet Integration
        for (node in nodes) {
            if (node.pinned) continue
            val current = node.pos
            val velocityDelta = (current - node.prevPos) * damping
            node.prevPos = current
            node.pos = current + velocityDelta + node.acc * dtSq
        }

        // Step 3: Iterative Gauss-Seidel Constraint Relaxation & Collision Projection
        val iters = solverIterations.coerceIn(2, 16)
        val floorY = -1.25f

        for (iter in 0 until iters) {
            for (con in constraints) {
                if (con.broken) continue
                val na = nodes[con.a]
                val nb = nodes[con.b]
                val delta = nb.pos - na.pos
                val dist = delta.length()
                if (dist < 1e-6f) continue

                val strain = (dist - con.restLength) / con.restLength
                con.currentStrain = max(0f, strain)

                if (tearableEnabled && strain > tearStrainThreshold) {
                    con.broken = true
                    continue
                }

                val stiffness = if (con.isStructural) structuralStiffness else con.stiffness
                val diff = (dist - con.restLength) / dist
                val correction = delta * (diff * stiffness * 0.5f)

                if (!na.pinned && !nb.pinned) {
                    na.pos = na.pos + correction
                    nb.pos = nb.pos - correction
                } else if (na.pinned && !nb.pinned) {
                    nb.pos = nb.pos - correction * 2f
                } else if (!na.pinned && nb.pinned) {
                    na.pos = na.pos + correction * 2f
                }
            }

            // 3D Sphere Collider & Floor Constraints
            for (node in nodes) {
                if (node.pinned) continue
                if (colliderEnabled) {
                    val toSurf = node.pos - colliderCenter
                    val d = toSurf.length()
                    val minR = colliderRadius + 0.03f
                    if (d < minR && d > 1e-5f) {
                        val n = toSurf / d
                        node.pos = colliderCenter + n * minR
                        // Tangential friction on sphere surface
                        val motion = node.pos - node.prevPos
                        node.prevPos = node.pos - motion * 0.72f
                    }
                }
                if (node.pos.y < floorY) {
                    val motion = node.pos - node.prevPos
                    node.pos = Vec3(
                        node.pos.x.coerceIn(-1.6f, 1.6f),
                        floorY,
                        node.pos.z.coerceIn(-1.6f, 1.6f)
                    )
                    node.prevPos = node.pos - Vec3(motion.x * 0.65f, 0f, motion.z * 0.65f)
                }
            }
        }

        // Record per-node peak strain for stress heatmap visualization
        for (con in constraints) {
            if (con.broken) continue
            val s = abs(con.currentStrain)
            if (s > nodes[con.a].maxStrain) nodes[con.a].maxStrain = s
            if (s > nodes[con.b].maxStrain) nodes[con.b].maxStrain = s
        }
    }

    private fun recomputeNormals() {
        for (n in nodes) {
            n.normal = Vec3(0f, 0f, 0f)
        }
        for (r in 0 until gridRows - 1) {
            for (c in 0 until gridCols - 1) {
                val i00 = idx(r, c)
                val i10 = idx(r, c + 1)
                val i01 = idx(r + 1, c)
                val p00 = nodes[i00].pos
                val p10 = nodes[i10].pos
                val p01 = nodes[i01].pos
                val faceN = (p10 - p00).cross(p01 - p00)
                nodes[i00].normal = nodes[i00].normal + faceN
                nodes[i10].normal = nodes[i10].normal + faceN
                nodes[i01].normal = nodes[i01].normal + faceN
            }
        }
        for (n in nodes) {
            val len = n.normal.length()
            n.normal = if (len > 1e-6f) n.normal / len else Vec3(0f, 0f, 1f)
        }
    }

    fun applyShockwave(center: Vec3, strength: Float = 0.22f) {
        for (node in nodes) {
            if (node.pinned) continue
            val delta = node.pos - center
            val d = delta.length().coerceAtLeast(0.2f)
            val impulse = (strength / (0.4f + d * d)).coerceAtMost(0.32f)
            node.prevPos = node.pos - (delta / d) * impulse
        }
    }

    fun activeConstraintCount(): Int = constraints.count { !it.broken }

    fun snapshotNodes(dt: Float = 0.016f): List<ClothNodeSnapshot> =
        nodes.map { n ->
            ClothNodeSnapshot(
                index = n.index,
                row = n.row,
                col = n.col,
                position = n.pos,
                velocity = (n.pos - n.prevPos) / dt.coerceAtLeast(0.004f),
                normal = n.normal,
                pinned = n.pinned,
                maxStrain = n.maxStrain
            )
        }

    fun snapshotStructuralLinks(): List<ClothLinkSnapshot> =
        constraints.filter { !it.broken && it.isStructural }.map { c ->
            ClothLinkSnapshot(
                indexA = c.a,
                indexB = c.b,
                strain = c.currentStrain,
                isStructural = c.isStructural
            )
        }

    fun snapshotQuads(): List<ClothQuadSnapshot> {
        // Build fast lookup of intact structural links so torn quads don't render across tears
        val intactPairs = HashSet<Long>(constraints.size * 2)
        for (con in constraints) {
            if (!con.broken && con.isStructural) {
                val lo = minOf(con.a, con.b).toLong()
                val hi = maxOf(con.a, con.b).toLong()
                intactPairs.add((lo shl 16) or hi)
            }
        }
        fun hasLink(u: Int, v: Int): Boolean {
            val lo = minOf(u, v).toLong()
            val hi = maxOf(u, v).toLong()
            return intactPairs.contains((lo shl 16) or hi)
        }

        val quads = ArrayList<ClothQuadSnapshot>((gridRows - 1) * (gridCols - 1))
        for (r in 0 until gridRows - 1) {
            for (c in 0 until gridCols - 1) {
                val n00 = idx(r, c)
                val n10 = idx(r, c + 1)
                val n11 = idx(r + 1, c + 1)
                val n01 = idx(r + 1, c)
                if (hasLink(n00, n10) && hasLink(n10, n11) && hasLink(n01, n11) && hasLink(n00, n01)) {
                    val p00 = nodes[n00].pos
                    val p10 = nodes[n10].pos
                    val p11 = nodes[n11].pos
                    val p01 = nodes[n01].pos
                    val center = (p00 + p10 + p11 + p01) * 0.25f
                    val normal = (p10 - p00).cross(p01 - p00).normalized()
                    val strain = maxOf(
                        nodes[n00].maxStrain,
                        nodes[n10].maxStrain,
                        nodes[n11].maxStrain,
                        nodes[n01].maxStrain
                    )
                    quads.add(
                        ClothQuadSnapshot(
                            n00 = n00,
                            n10 = n10,
                            n11 = n11,
                            n01 = n01,
                            center = center,
                            normal = normal,
                            strain = strain
                        )
                    )
                }
            }
        }
        return quads
    }
}
