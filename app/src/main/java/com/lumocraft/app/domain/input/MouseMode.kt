package com.lumocraft.app.domain.input

/**
 * Virtual mouse modes. TOUCH follows the finger directly, RELATIVE
 * turns drags into cursor movement, LOCKED hides the cursor and turns
 * drags into pure relative deltas (Minecraft camera style).
 */
enum class MouseMode {
    TOUCH,
    RELATIVE,
    LOCKED
}