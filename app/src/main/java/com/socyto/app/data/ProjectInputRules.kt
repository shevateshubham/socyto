package com.socyto.app.data

/** Explicit input validation before sending anything to the server. Database RLS is authoritative. */
object ProjectInputRules {
    fun validName(name: String): Boolean = name.isNotBlank() && name.trim().length <= 120
    fun validEmail(email: String): Boolean =
        email.isBlank() || (email.length <= 254 && email.contains("@") &&
            !email.contains(' ') && email.substringAfter("@").contains('.'))
    fun validQuantity(value: Double): Boolean = value.isFinite() && value > 0.0
    fun validRate(value: Double): Boolean = value.isFinite() && value >= 0.0
}
