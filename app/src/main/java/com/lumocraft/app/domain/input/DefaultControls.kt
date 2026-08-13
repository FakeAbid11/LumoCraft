package com.lumocraft.app.domain.input

/**
 * Starter layout: movement pad plus the eight core actions every
 * Minecraft session needs. Compose-drawn, no external assets.
 * Positions are normalized; keep total button area modest so the
 * layout leaves room for the virtual mouse area.
 */
fun defaultButtonLayout(): ButtonLayout = ButtonLayout(
    buttons = listOf(
        ControlButton(
            id = "move",
            action = InputAction.MOVE_FORWARD,
            label = "Move",
            x = 0.13f, y = 0.70f,
            width = 0.34f, height = 0.24f,
            kind = ControlKind.JOYSTICK
        ),
        ControlButton(
            id = "jump",
            action = InputAction.JUMP,
            label = "Jump",
            x = 0.80f, y = 0.74f,
            width = 0.14f, height = 0.14f
        ),
        ControlButton(
            id = "sneak",
            action = InputAction.SNEAK,
            label = "Sneak",
            x = 0.62f, y = 0.87f,
            width = 0.12f, height = 0.12f
        ),
        ControlButton(
            id = "attack",
            action = InputAction.ATTACK,
            label = "Attack",
            x = 0.85f, y = 0.44f,
            width = 0.16f, height = 0.16f
        ),
        ControlButton(
            id = "use",
            action = InputAction.USE,
            label = "Use",
            x = 0.71f, y = 0.42f,
            width = 0.12f, height = 0.12f
        ),
        ControlButton(
            id = "inventory",
            action = InputAction.INVENTORY,
            label = "Inv",
            x = 0.87f, y = 0.17f,
            width = 0.11f, height = 0.11f
        ),
        ControlButton(
            id = "chat",
            action = InputAction.CHAT,
            label = "Chat",
            x = 0.08f, y = 0.10f,
            width = 0.11f, height = 0.11f
        ),
        ControlButton(
            id = "pause",
            action = InputAction.PAUSE,
            label = "Pause",
            x = 0.91f, y = 0.05f,
            width = 0.09f, height = 0.09f
        )
    )
)

/** The profile every fresh install starts with. */
fun defaultInputProfile(): InputProfile = InputProfile(
    id = DEFAULT_PROFILE_ID,
    name = "Default"
)

const val DEFAULT_PROFILE_ID = "default"