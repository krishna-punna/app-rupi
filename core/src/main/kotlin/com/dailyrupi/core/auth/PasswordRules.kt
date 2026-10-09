package com.dailyrupi.core.auth

/** The backend's rules for a new password, so the user sees problems before sending it. */
object PasswordRules {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 72

    fun problem(username: String, current: String, new: String, confirm: String): String? = when {
        current.isEmpty() -> "Enter your current password"
        new.length < MIN_LENGTH -> "New password must be at least $MIN_LENGTH characters"
        new.length > MAX_LENGTH -> "New password can be at most $MAX_LENGTH characters"
        username.isNotBlank() && new.lowercase().contains(username.lowercase()) ->
            "New password must not contain your username"
        new == current -> "New password must be different from the current one"
        new != confirm -> "The two new passwords do not match"
        else -> null
    }
}
