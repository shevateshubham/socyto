package com.socyto.app.data

import android.os.Handler
import android.os.Looper
import com.socyto.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** A single Supabase session; never stored on disk in this milestone. */
data class Session(val accessToken: String, val userId: String)
data class BusinessSummary(val id: String, val name: String)
data class ProjectSummary(val id: String, val businessId: String, val name: String)
data class PublishedUpdate(val progress: String, val note: String, val publishedAt: String)

sealed class AccountScope {
    data object PlatformAdmin : AccountScope()
    data class BusinessAdmin(val business: BusinessSummary) : AccountScope()
    data object Client : AccountScope()
}

class SocytoApi {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun <T> async(operation: () -> T, completion: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(operation)
            mainHandler.post { completion(result) }
        }
    }

    fun signIn(email: String, password: String): Session {
        val response = request("POST", "/auth/v1/token?grant_type=password", null,
            JSONObject().put("email", email).put("password", password))
        return Session(response.getString("access_token"),
            response.getJSONObject("user").getString("id"))
    }

    /** Role is determined from authenticated database queries; the app never trusts user-supplied role text. */
    fun accountScope(session: Session): AccountScope {
        val isPlatformAdmin = getRows("platform_administrators", "user_id",
            "user_id=eq.${encode(session.userId)}", session).length() > 0
        if (isPlatformAdmin) return AccountScope.PlatformAdmin
        val memberships = getRows("business_members", "business_id",
            "user_id=eq.${encode(session.userId)}&member_role=eq.business_admin", session)
        if (memberships.length() > 0) {
            val businessId = memberships.getJSONObject(0).getString("business_id")
            val rows = getRows("businesses", "business_id,business_name",
                "business_id=eq.${encode(businessId)}", session)
            if (rows.length() > 0) {
                val business = rows.getJSONObject(0)
                return AccountScope.BusinessAdmin(BusinessSummary(businessId, business.getString("business_name")))
            }
        }
        // Client access is still verified by project-specific RLS, never by assumed ownership.
        return AccountScope.Client
    }

    fun businesses(session: Session): List<BusinessSummary> {
        val rows = getRows("businesses", "business_id,business_name", "order=business_name.asc", session)
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { BusinessSummary(it.getString("business_id"), it.getString("business_name")) }
        }
    }

    /** RLS allows only the signed-in firm, its assigned clients, or the platform admin. */
    fun branding(session: Session, businessId: String): BusinessBranding {
        val rows = getRows(
            "business_branding",
            "business_id,display_name,logo_url,logo_storage_path,primary_color_hex,secondary_color_hex,accent_color_hex,background_color_hex,text_color_hex",
            "business_id=eq.${encode(businessId)}",
            session
        )
        if (rows.length() == 0) {
            // Use the actual business name for admins; clients receive a neutral fallback.
            return BusinessBranding(businessId, "Project workspace")
        }
        val row = rows.getJSONObject(0)
        fun stringOrNull(name: String): String? =
            if (row.isNull(name)) null else row.optString(name)
        return BusinessBranding(
            businessId = businessId,
            displayName = BrandValidator.displayNameOrFallback(stringOrNull("display_name"), "Project workspace"),
            logoUrl = stringOrNull("logo_storage_path")?.let { logoPath ->
                BrandValidator.logoUrlOrNull(
                    BuildConfig.SUPABASE_URL.trimEnd('/') +
                        "/storage/v1/object/public/business-logos/" + logoPath,
                    BuildConfig.SUPABASE_URL
                )
            } ?: BrandValidator.logoUrlOrNull(stringOrNull("logo_url"), BuildConfig.SUPABASE_URL),
            logoStoragePath = stringOrNull("logo_storage_path"),
            primaryColorHex = BrandValidator.colorOrDefault(stringOrNull("primary_color_hex"), "#2E4A2B"),
            secondaryColorHex = BrandValidator.colorOrDefault(stringOrNull("secondary_color_hex"), "#6B8E49"),
            accentColorHex = BrandValidator.colorOrDefault(stringOrNull("accent_color_hex"), "#D4AF7C"),
            backgroundColorHex = BrandValidator.colorOrDefault(stringOrNull("background_color_hex"), "#F7F5F0"),
            textColorHex = BrandValidator.colorOrDefault(stringOrNull("text_color_hex"), "#1F1F1F")
        )
    }

    /** Restricted by storage bucket RLS to Super Admin and at most 2 MB. */
    fun uploadBusinessLogo(
        session: Session, businessId: String,
        imageBytes: ByteArray, contentType: String
    ): String {
        check(imageBytes.isNotEmpty() && imageBytes.size <= 2 * 1024 * 1024) {
            "Logo must be smaller than 2 MB."
        }
        val extension = when (contentType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> error("Choose PNG, JPG, or WebP.")
        }
        val businessUuid = java.util.UUID.fromString(businessId).toString()
        val objectName = businessUuid + "/" + java.util.UUID.randomUUID().toString() + "." + extension
        val url = URL(
            BuildConfig.SUPABASE_URL.trimEnd('/') +
                "/storage/v1/object/business-logos/" + objectName
        )
        check(url.protocol == "https") { "HTTPS connection required." }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("x-upsert", "false")
            connection.doOutput = true
            connection.outputStream.use { it.write(imageBytes) }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Logo upload was rejected. Check file and administrator permissions.")
            }
            return objectName
        } finally {
            connection.disconnect()
        }
    }

    /** Database RPC authorizes writes; caller role and business ID are never trusted. */
    fun saveBranding(session: Session, brand: BusinessBranding) {
        val payload = JSONObject()
            .put("target_business_id", brand.businessId)
            .put("new_display_name", brand.displayName)
            .put("new_primary_color_hex", brand.primaryColorHex)
            .put("new_secondary_color_hex", brand.secondaryColorHex)
            .put("new_accent_color_hex", brand.accentColorHex)
            .put("new_background_color_hex", brand.backgroundColorHex)
            .put("new_text_color_hex", brand.textColorHex)
            .put("new_logo_storage_path", brand.logoStoragePath ?: JSONObject.NULL)
        request("POST", "/rest/v1/rpc/save_business_branding", session, payload)
    }

    fun projects(session: Session, businessId: String? = null): List<ProjectSummary> {
        val filter = if (businessId != null) "business_id=eq.${encode(businessId)}&order=created_at.desc" else "order=created_at.desc"
        val rows = getRows("projects", "project_id,business_id,project_name", filter, session)
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let {
                ProjectSummary(it.getString("project_id"), it.getString("business_id"), it.getString("project_name"))
            }
        }
    }

    /** Only immutable published snapshots; draft requirements and receipts are never requested by clients. */
    fun publications(session: Session, project: ProjectSummary): List<PublishedUpdate> {
        val filter = "business_id=eq.${encode(project.businessId)}&project_id=eq.${encode(project.id)}&order=published_at.desc"
        val rows = getRows("project_publications", "overall_progress_percent,public_update_note,published_at", filter, session)
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let {
                PublishedUpdate(
                    if (it.isNull("overall_progress_percent")) "Not calculated" else
                        "${it.getDouble("overall_progress_percent")}% ",
                    it.optString("public_update_note", "").takeIf { note -> note != "null" } ?: "",
                    it.optString("published_at")
                )
            }
        }
    }

    private fun getRows(table: String, fields: String, filters: String, session: Session): JSONArray {
        val path = "/rest/v1/$table?select=${encode(fields)}&$filters"
        return JSONArray(request("GET", path, session).getString("_rows"))
    }

    private fun request(method: String, path: String, session: Session?,
                        payload: JSONObject? = null): JSONObject {
        check(isConfigured) { "Configure SOCYTO backend before signing in." }
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = URL(base + path)
        check(url.protocol == "https" && url.host == URL(base).host) { "Backend must use HTTPS." }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (session != null) connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Never leak credentials, raw server details or private records to the UI.
                throw IllegalStateException(if (status == 401 || status == 403) "Access denied. Check account permissions."
                    else "Service request failed (HTTP $status).")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return when {
                text.isBlank() -> JSONObject()
                text.startsWith("[") -> JSONObject().put("_rows", text)
                else -> JSONObject(text)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
