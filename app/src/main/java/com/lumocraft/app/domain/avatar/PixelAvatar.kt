package com.lumocraft.app.domain.avatar

/**
 * A generated avatar as a flat array of ARGB pixels, row-major.
 */
data class PixelAvatar(
    val size: Int,
    val pixels: IntArray
) {
    init {
        require(size > 0) { "Avatar size must be positive" }
        require(pixels.size == size * size) { "Pixel count must be size * size" }
    }

    override fun equals(other: Any?): Boolean =
        other is PixelAvatar &&
            other.size == size &&
            other.pixels.contentEquals(pixels)

    override fun hashCode(): Int = 31 * size + pixels.contentHashCode()

    override fun toString(): String = "PixelAvatar(size=$size)"
}
