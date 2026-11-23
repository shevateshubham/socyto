package com.socyto.app.data

import java.util.Locale

/** User-facing login handles are not database keys or authorization evidence. */
object AccountIdentifiers {
    private val accountCode = Regex("^[FC][0-9]{9}$")
    private val mobile = Regex("^\\+[1-9][0-9]{7,14}$")
    private const val reservedLoginDomain = "login.socyto.invalid"

    fun isAccountCode(input: String): Boolean =
        accountCode.matches(input.trim().uppercase(Locale.ROOT))

    fun validPhone(input: String): Boolean = mobile.matches(input.trim())

    fun authenticationEmail(input: String): String {
        val normalized = input.trim().lowercase(Locale.ROOT)
        return if (isAccountCode(input)) "$"+"{normalized}@$"+"{reservedLoginDomain}"
        else normalized
    }
}
