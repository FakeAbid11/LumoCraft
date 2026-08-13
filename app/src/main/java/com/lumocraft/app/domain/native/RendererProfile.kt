package com.lumocraft.app.domain.native

/**
 * Launcher-side renderer profiles. These are launcher preferences only —
 * they tune how the renderer glue (a later phase) will configure the game
 * window; vanilla itself only receives the resolution tokens.
 */
enum class RendererType {
    /** Safest settings, best compatibility with low-end GPUs. */
    COMPATIBILITY,

    /** Maximum performance: lowest resolution, capped FPS, no extras. */
    PERFORMANCE,

    /** Higher quality: full resolution, uncapped FPS, mipmaps. */
    EXPERIMENTAL
}

/** Manual resolution scales applied on top of the base window size. */
enum class ResolutionScale(val percent: Int, val factor: Float) {
    PERCENT_50(50, 0.5f),
    PERCENT_75(75, 0.75f),
    PERCENT_100(100, 1f)
}

/** Effective game window size. */
data class Resolution(val width: Int, val height: Int)

/**
 * Renderer configuration for one launch session.
 * [fpsLimit] is null when unlimited. [mipmaps] is the mipmap level count.
 */
data class RendererProfile(
    val renderer: RendererType = RendererType.COMPATIBILITY,
    val resolutionScale: ResolutionScale = ResolutionScale.PERCENT_75,
    val fpsLimit: Int? = null,
    val vsync: Boolean = false,
    val mipmaps: Int = 0
) {

    /** Applies the scale to the base window size (854x480 default). */
    fun effectiveResolution(base: Resolution = DEFAULT_WINDOW): Resolution =
        Resolution(
            width = (base.width * resolutionScale.factor).toInt().coerceAtLeast(MIN_WIDTH),
            height = (base.height * resolutionScale.factor).toInt().coerceAtLeast(MIN_HEIGHT)
        )

    companion object {
        val DEFAULT_WINDOW = Resolution(854, 480)
        const val MIN_WIDTH = 320
        const val MIN_HEIGHT = 240

        /** Sensible defaults per profile; users can fine-tune afterwards. */
        fun preset(type: RendererType): RendererProfile = when (type) {
            RendererType.COMPATIBILITY -> RendererProfile(
                renderer = type,
                resolutionScale = ResolutionScale.PERCENT_75,
                fpsLimit = 30,
                vsync = false,
                mipmaps = 0
            )
            RendererType.PERFORMANCE -> RendererProfile(
                renderer = type,
                resolutionScale = ResolutionScale.PERCENT_50,
                fpsLimit = 30,
                vsync = false,
                mipmaps = 0
            )
            RendererType.EXPERIMENTAL -> RendererProfile(
                renderer = type,
                resolutionScale = ResolutionScale.PERCENT_100,
                fpsLimit = null,
                vsync = true,
                mipmaps = 4
            )
        }
    }
}