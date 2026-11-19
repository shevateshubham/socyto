package com.socyto.app.data

/**
 * Branding stays inside the authenticated business scope.
 * The neutral fallback is not a fabricated business identity.
 */
data class BusinessBranding(
    val businessId: String,
    val displayName: String,
    val logoUrl: String? = null,
    val primaryColorHex: String = "#2E4A2B",
    val secondaryColorHex: String = "#6B8E49",
    val accentColorHex: String = "#D4AF7C",
    val backgroundColorHex: String = "#F7F5F0",
    val textColorHex: String = "#1F1F1F"
)

object BrandValidator {
    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")

    fun colorOrDefault(value: String?, fallback: String): String =
        if (value != null && colorPattern.matches(value)) value.uppercase() else fallback

    fun displayNameOrFallback(value: String?, fallback: String): String =
        value?.trim()?.takeIf { it.isNotBlank() && it.length <= 80 } ?: fallback

    /** Accept only the configured Supabase project's HTTPS logo storage host. */
    fun logoUrlOrNull(value: String?, backendUrl: String): String? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val candidate = java.net.URI(value)
            val backend = java.net.URI(backendUrl)
            candidate.scheme == "https" &&
                candidate.host != null &&
                candidate.host.equals(backend.host, ignoreCase = true) &&
                candidate.port == -1 &&
                candidate.userInfo == null &&
                candidate.path.startsWith("/storage/v1/object/public/business-logos/")
        }.getOrDefault(false).let { if (it) value else null }
    }
}
