package com.lumocraft.app.domain.account

/** Reason why a username did not pass validation. */
enum class UsernameError {
    EMPTY,
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    DUPLICATE
}

/** Result of [AccountValidator.validateUsername]. */
data class UsernameValidation(val error: UsernameError?) {
    val isValid: Boolean get() = error == null
}

/**
 * Pure business-rule validation for offline account usernames.
 *
 * Rules: 3–16 characters, letters, numbers and underscores only,
 * no spaces, no duplicates (case-insensitive).
 */
object AccountValidator {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 16

    private val ALLOWED_CHARACTERS = Regex("^[A-Za-z0-9_]+$")

    fun validateUsername(
        username: String,
        existingUsernames: Collection<String>
    ): UsernameValidation {
        val name = username.trim()
        if (name.isEmpty()) return UsernameValidation(UsernameError.EMPTY)
        if (name.length < MIN_LENGTH) return UsernameValidation(UsernameError.TOO_SHORT)
        if (name.length > MAX_LENGTH) return UsernameValidation(UsernameError.TOO_LONG)
        if (!ALLOWED_CHARACTERS.matches(name)) {
            return UsernameValidation(UsernameError.INVALID_CHARACTERS)
        }
        if (existingUsernames.any { it.equals(name, ignoreCase = true) }) {
            return UsernameValidation(UsernameError.DUPLICATE)
        }
        return UsernameValidation(null)
    }
}
