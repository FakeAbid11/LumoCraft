package com.lumocraft.app.domain.avatar

import java.security.MessageDigest

/**
 * Generates deterministic, Minecraft-style pixel avatars from a username.
 *
 * The same username always produces the same avatar: the SHA-256 digest
 * of the username bytes seeds both the color palette and the pixel pattern,
 * so no internet, no APIs and no stored assets are needed.
 */
class AvatarGenerator {

    fun generate(username: String, gridSize: Int = DEFAULT_GRID_SIZE): PixelAvatar {
        require(gridSize % PATTERN_SIZE == 0) {
            "gridSize must be a multiple of $PATTERN_SIZE, was $gridSize"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(username.toByteArray(Charsets.UTF_8))

        val hue = ((digest[0].toInt() and 0xFF) / 255f) * 360f
        val saturation = 0.5f + ((digest[1].toInt() and 0xFF) / 255f) * 0.3f
        val value = 0.55f + ((digest[2].toInt() and 0xFF) / 255f) * 0.3f

        val palette = intArrayOf(
            hsvToArgb(hue, saturation, value * 0.65f),                       // dark
            hsvToArgb(hue, saturation * 0.95f, value),                       // base
            hsvToArgb(hue, saturation * 0.85f, minOf(value + 0.18f, 1f)),    // light
            hsvToArgb((hue + 45f) % 360f, 0.35f, minOf(value + 0.15f, 1f))   // accent
        )

        // 8x8 pattern, left half seeded from the digest (32 cells = 32 bytes),
        // mirrored to the right half like a classic Minecraft face texture.
        val pattern = IntArray(PATTERN_SIZE * PATTERN_SIZE)
        val half = PATTERN_SIZE / 2
        for (y in 0 until PATTERN_SIZE) {
            for (x in 0 until half) {
                val shade = (digest[y * half + x].toInt() and 0xFF) % palette.size
                pattern[y * PATTERN_SIZE + x] = palette[shade]
                pattern[y * PATTERN_SIZE + (PATTERN_SIZE - 1 - x)] = palette[shade]
            }
        }

        // Scale the 8x8 pattern up to the requested grid size.
        val scale = gridSize / PATTERN_SIZE
        val pixels = IntArray(gridSize * gridSize)
        for (y in 0 until gridSize) {
            val sourceRow = (y / scale) * PATTERN_SIZE
            for (x in 0 until gridSize) {
                pixels[y * gridSize + x] = pattern[sourceRow + x / scale]
            }
        }

        return PixelAvatar(gridSize, pixels)
    }

    private fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            ((r + m) * 255f).toInt().shl(16) or
            ((g + m) * 255f).toInt().shl(8) or
            ((b + m) * 255f).toInt()
    }

    companion object {
        const val DEFAULT_GRID_SIZE = 16
        const val PATTERN_SIZE = 8
    }
}
