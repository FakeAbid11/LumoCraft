package com.lumocraft.app.domain.input

/**
 * The full set of controls of one [InputProfile]. Positions are
 * normalized so a layout designed on a tablet still fits a phone.
 */
data class ButtonLayout(
    val version: Int = LAYOUT_VERSION,
    val buttons: List<ControlButton> = emptyList()
) {
    fun find(id: String): ControlButton? = buttons.firstOrNull { it.id == id }

    fun withOpacity(opacity: Float): ButtonLayout =
        copy(buttons = buttons.map { it.copy(opacity = opacity) })

    companion object {
        const val LAYOUT_VERSION = 1
    }
}