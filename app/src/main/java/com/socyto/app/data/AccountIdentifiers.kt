package com.socyto.app.data

import java.util.Locale

/**
 * One identifier input across platform administrators, firms and clients.
 * Login handles are not authorization evidence: only Supabase Auth issues sessions.
 */
object AccountIdentifiers {
    private val accountCode = Regex("^[FC][0-9]{9}$")
    private val mobile = Regex("^\\+[1-9][0-9]{7,14}$")
    private val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private const val reservedLoginDomain = "login.socyto.invalid"

    fun isAccountCode(input: String): Boolean =
        accountCode.matches(input.trim().uppercase(Locale.ROOT))

    /** Default to India only for an unambiguous 10-digit Indian mobile number. */
    fun normalizedPhone(input: String): String {
        val trimmed = input.trim()
        return if (Regex("^[6-9][0-9]{9}$").matches(trimmed)) "+91" + trimmed else trimmed
    }

    fun validPhone(input: String): Boolean = mobile.matches(normalizedPhone(input))

    fun normalizedContact(input: String): String {
        val trimmed = input.trim()
        return if (validPhone(trimmed)) normalizedPhone(trimmed)
        else if (validEmail(trimmed)) trimmed.lowercase(Locale.ROOT)
        else trimmed
    }

    fun validEmail(input: String): Boolean =
        input.trim().length <= 254 && email.matches(input.trim())

    fun validIdentifier(input: String): Boolean =
        isAccountCode(input) || validPhone(input) || validEmail(input)

    /** Only codes and email map to Auth email; mobile routes through verified login service. */
    fun authenticationEmail(input: String): String {
        val normalized = input.trim().lowercase(Locale.ROOT)
        return if (isAccountCode(input)) normalized + "@" + reservedLoginDomain
        else normalized
    }
}
