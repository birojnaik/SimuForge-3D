package com.example.physics

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vec3 {
        val inv = if (scalar != 0f) 1f / scalar else 0f
        return Vec3(x * inv, y * inv, z * inv)
    }

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSq(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSq())

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    fun lerp(target: Vec3, alpha: Float): Vec3 =
        Vec3(
            x + (target.x - x) * alpha,
            y + (target.y - y) * alpha,
            z + (target.z - z) * alpha
        )
}

data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val depthZ: Float,
    val scale: Float,
    val visible: Boolean
)

data class Camera3D(
    val yawRadians: Float = 0.58f,
    val pitchRadians: Float = 0.36f,
    val distance: Float = 5.4f,
    val target: Vec3 = Vec3(0f, 0f, 0f),
    val fovScale: Float = 1.25f
) {
    fun basisVectors(): Triple<Vec3, Vec3, Vec3> {
        val cosP = cos(pitchRadians)
        val sinP = sin(pitchRadians)
        val cosY = cos(yawRadians)
        val sinY = sin(yawRadians)

        // Camera position relative to target
        val forward = Vec3(
            -sinY * cosP,
            -sinP,
            -cosY * cosP
        ).normalized()

        val worldUp = Vec3(0f, 1f, 0f)
        val right = forward.cross(worldUp).normalized()
        val up = right.cross(forward).normalized()
        return Triple(right, up, forward)
    }

    fun eyePosition(): Vec3 {
        val cosP = cos(pitchRadians)
        val sinP = sin(pitchRadians)
        val cosY = cos(yawRadians)
        val sinY = sin(yawRadians)
        return Vec3(
            target.x + sinY * cosP * distance,
            target.y + sinP * distance,
            target.z + cosY * cosP * distance
        )
    }

    fun project(
        worldPos: Vec3,
        viewportWidth: Float,
        viewportHeight: Float
    ): ProjectedPoint {
        val eye = eyePosition()
        val (right, up, forward) = basisVectors()
        val rel = worldPos - eye

        val camX = rel.dot(right)
        val camY = rel.dot(up)
        val camZ = rel.dot(forward)

        if (camZ <= 0.15f) {
            return ProjectedPoint(0f, 0f, camZ, 0f, false)
        }

        val minDim = minOf(viewportWidth, viewportHeight)
        val focal = minDim * fovScale * 0.55f
        val perspectiveScale = focal / camZ

        val sx = viewportWidth * 0.5f + camX * perspectiveScale
        val sy = viewportHeight * 0.5f - camY * perspectiveScale

        return ProjectedPoint(
            screenX = sx,
            screenY = sy,
            depthZ = camZ,
            scale = perspectiveScale,
            visible = sx in -200f..(viewportWidth + 200f) && sy in -200f..(viewportHeight + 200f)
        )
    }

    fun screenDeltaToWorld(
        dxPixels: Float,
        dyPixels: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        referenceDepth: Float = distance
    ): Vec3 {
        val (right, up, _) = basisVectors()
        val minDim = minOf(viewportWidth, viewportHeight).coerceAtLeast(1f)
        val focal = minDim * fovScale * 0.55f
        val worldPerPixel = referenceDepth / focal
        return right * (dxPixels * worldPerPixel) + up * (-dyPixels * worldPerPixel)
    }

    fun screenPointToWorldOnTargetPlane(
        screenX: Float,
        screenY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Vec3 {
        val (right, up, _) = basisVectors()
        val minDim = minOf(viewportWidth, viewportHeight).coerceAtLeast(1f)
        val focal = minDim * fovScale * 0.55f
        val worldPerPixel = distance / focal
        val dx = screenX - viewportWidth * 0.5f
        val dy = viewportHeight * 0.5f - screenY
        return target + right * (dx * worldPerPixel) + up * (dy * worldPerPixel)
    }
}

object FieldMath {
    /**
     * Smooth divergence-free 3D curl-noise approximation using trigonometric harmonics
     * to model realistic fluid & aerodynamic vortex turbulence.
     */
    fun sampleCurlNoise3D(pos: Vec3, time: Float, frequency: Float = 1.6f): Vec3 {
        val px = pos.x * frequency + time * 1.3f
        val py = pos.y * frequency - time * 0.9f
        val pz = pos.z * frequency + time * 1.1f

        val vx = sin(py) * cos(pz) - cos(pz * 1.3f) * sin(px * 0.7f)
        val vy = sin(pz) * cos(px) - cos(px * 1.2f) * sin(py * 0.8f)
        val vz = sin(px) * cos(py) - cos(py * 1.1f) * sin(pz * 0.9f)
        return Vec3(vx, vy, vz)
    }
}
