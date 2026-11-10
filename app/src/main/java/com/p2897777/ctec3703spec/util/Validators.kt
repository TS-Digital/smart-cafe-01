package com.p2897777.ctec3703spec.util

/**
 * Pure input validation, kept free of Android classes so it can be unit tested on the JVM.
 * Each function returns null when the input is valid, or a user-facing error message.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 50
    const val MAX_COMMENT_LENGTH = 500
    const val MAX_QUANTITY = 20

    fun name(input: String): String? {
        val v = input.trim()
        return when {
            v.isEmpty() -> "Please enter your name"
            v.length < 2 -> "Name must be at least 2 characters"
            v.length > MAX_NAME_LENGTH -> "Name must be $MAX_NAME_LENGTH characters or fewer"
            !v.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' } ->
                "Name can only contain letters, spaces, hyphens and apostrophes"
            else -> null
        }
    }

    fun email(input: String): String? {
        val v = input.trim()
        return when {
            v.isEmpty() -> "Please enter your email"
            !EMAIL_REGEX.matches(v) -> "Please enter a valid email address"
            else -> null
        }
    }

    fun password(input: String): String? = when {
        input.isEmpty() -> "Please enter a password"
        input.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
        !input.any { it.isDigit() } -> "Password must contain at least one number"
        !input.any { it.isLetter() } -> "Password must contain at least one letter"
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? =
        if (password != confirm) "Passwords do not match" else null

    fun rating(value: Int): String? =
        if (value !in 1..5) "Please choose a rating from 1 to 5 stars" else null

    fun comment(input: String): String? =
        if (input.trim().length > MAX_COMMENT_LENGTH) "Comment must be $MAX_COMMENT_LENGTH characters or fewer" else null

    fun quantity(value: Int): String? = when {
        value < 1 -> "Quantity must be at least 1"
        value > MAX_QUANTITY -> "You can order at most $MAX_QUANTITY of one item"
        else -> null
    }

    fun normaliseEmail(input: String): String = input.trim().lowercase()
}
