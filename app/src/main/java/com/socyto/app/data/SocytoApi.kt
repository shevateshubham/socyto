package com.socyto.app.data

import android.os.Handler
import android.os.Looper
import com.socyto.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** A single Supabase session; never stored on disk in this milestone. */
data class Session(
    var accessToken: String,
    val userId: String,
    var refreshToken: String,
    var expiresAtEpochMs: Long,
    var requiresPasswordChange: Boolean = false
)
data class BusinessSummary(val id: String, val name: String, val status: String = "pending")
data class ProjectSummary(val id: String, val businessId: String, val name: String)
data class ClientSummary(val id: String, val name: String)
data class RequirementSummary(
    val id: String,
    val title: String,
    val type: String,
    val effortScore: Int,
    val completed: Boolean,
    val requiredQuantity: Double?,
    val completedQuantity: Double,
    val unit: String?
)
data class MaterialReceiptSummary(val id: String, val materialName: String, val quantity: Double, val unit: String, val amount: Double)
data class PublishedUpdate(val progress: String, val note: String, val publishedAt: String)
data class ClientTemporaryCredentials(val email: String, val temporaryPassword: String)

sealed class AccountScope {
    data object PlatformAdmin : AccountScope()
    data class BusinessAdmin(val business: BusinessSummary) : AccountScope()
    data object Client : AccountScope()
    data object Unassigned : AccountScope()
}

class SocytoApi {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    val isConfigured: Boolean get() =
        BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.startsWith("sb_publishable_")

    fun <T> async(operation: () -> T, completion: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(operation)
            mainHandler.post { completion(result) }
        }
    }

    /** Signup grants no business privileges until verified role provisioning. */
    fun signUp(email: String, password: String) {
        require(email.contains("@") && password.length >= 8) {
            "Enter a valid email and a password with at least 8 characters."
        }
        request("POST", "/auth/v1/signup", null,
            JSONObject().put("email", email.trim()).put("password", password))
    }

    /** A verified new account may redeem an expiring, single-use administrator invitation. */
    fun claimPlatformAdmin(session: Session, invitationCode: String) {
        require(Regex("^[a-f0-9]{64}$").matches(invitationCode.trim())) {
            "Enter the 64-character one-time activation code."
        }
        val response = request("POST", "/rest/v1/rpc/claim_platform_admin_bootstrap",
            session, JSONObject().put("one_time_code", invitationCode.trim()))
        check(response.optBoolean("_value", false)) {
            "Super Admin activation was not confirmed."
        }
    }

    /** Real server-side firm identity provisioning: no app-generated credentials. */
    fun provisionFirmAccount(session: Session, name: String, phone: String): ClientTemporaryCredentials {
        require(ProjectInputRules.validName(name) && Regex("^\\+[1-9][0-9]{7,14}$").matches(phone.trim()))
        val result = request("POST", "/functions/v1/client-onboarding", session,
            JSONObject().put("action", "provision_firm")
                .put("firm_name", name.trim()).put("phone", phone.trim()))
        return ClientTemporaryCredentials(
            result.getString("login_id"), result.getString("temporary_password"))
    }

    /** A firm administrator provisions a client without requiring a personal email address. */
    fun provisionClientAccount(
        session: Session, businessId: String, name: String, phone: String
    ): Pair<ClientSummary, ClientTemporaryCredentials> {
        require(ProjectInputRules.validName(name) && Regex("^\\+[1-9][0-9]{7,14}$").matches(phone.trim()))
        val result = request("POST", "/functions/v1/client-onboarding", session,
            JSONObject().put("action", "provision_client_account")
                .put("business_id", businessId)
                .put("client_name", name.trim()).put("phone", phone.trim()))
        return ClientSummary(result.getString("record_id"), name.trim()) to
            ClientTemporaryCredentials(result.getString("login_id"), result.getString("temporary_password"))
    }

    /** Only a platform administrator can register a new firm. */
    fun registerFirm(
        session: Session, businessName: String, administratorEmail: String, businessType: String
    ) {
        require(businessName.trim().length in 2..80)
        require(administratorEmail.contains("@"))
        request("POST", "/rest/v1/rpc/register_business_for_verified_email", session,
            JSONObject().put("new_business_name", businessName.trim())
                .put("administrator_email", administratorEmail.trim())
                .put("new_business_type", businessType))
    }

    /**
     * Provision a real, firm-scoped client login through the privileged Edge Function.
     * This password is returned exactly once, not stored by the Android app.
     */
    fun provisionClientLogin(
        session: Session, businessId: String, clientId: String
    ): ClientTemporaryCredentials {
        val result = request("POST", "/functions/v1/client-onboarding", session,
            JSONObject().put("action", "provision_client")
                .put("business_id", businessId)
                .put("client_id", clientId))
        return ClientTemporaryCredentials(
            result.getString("email"), result.getString("temporary_password"))
    }

    fun completeClientFirstLogin(session: Session, newPassword: String) {
        require(newPassword.length in 12..128 &&
            newPassword.any { it.isLetter() } &&
            newPassword.any { it.isDigit() }) {
            "Use 12 or more characters containing letters and numbers."
        }
        val result = request("POST", "/functions/v1/client-onboarding", session,
            JSONObject().put("action", "complete_first_login")
                .put("new_password", newPassword))
        check(result.optBoolean("success")) {
            "Password change was not confirmed by the server."
        }
    }

    /** Only a Business Admin can link their own client to a verified login. */
    fun linkClientLogin(session: Session, businessId: String, clientId: String) {
        request("POST", "/rest/v1/rpc/link_verified_client_login", session,
            JSONObject().put("target_business_id", businessId)
                .put("target_client_id", clientId))
    }

    /** Phone OTP sends only to a previously registered number; it never creates an account. */
    fun sendRecoveryOtp(phone: String) {
        require(Regex("^\\+[1-9][0-9]{7,14}$").matches(phone.trim()))
        request("POST", "/auth/v1/otp", null,
            JSONObject().put("phone", phone.trim()).put("create_user", false)
                .put("channel", "sms"))
    }

    /** SMS confirmation is performed by Supabase Auth, not an app-side comparison. */
    fun recoverPasswordWithOtp(phone: String, otp: String, newPassword: String) {
        require(Regex("^\\+[1-9][0-9]{7,14}$").matches(phone.trim()))
        require(Regex("^[0-9]{6,8}$").matches(otp.trim()))
        require(newPassword.length in 12..128 && newPassword.any { it.isLetter() } &&
            newPassword.any { it.isDigit() })
        val verified = request("POST", "/auth/v1/verify", null,
            JSONObject().put("phone", phone.trim())
                .put("token", otp.trim()).put("type", "sms"))
        val user = verified.getJSONObject("user")
        val session = Session(
            accessToken = verified.getString("access_token"),
            userId = user.getString("id"),
            refreshToken = verified.getString("refresh_token"),
            expiresAtEpochMs = System.currentTimeMillis() +
                verified.optLong("expires_in", 3600L) * 1000L,
            requiresPasswordChange = user.optJSONObject("app_metadata")
                ?.optBoolean("socyto_password_reset_required", false) == true
        )
        if (session.requiresPasswordChange) completeClientFirstLogin(session, newPassword)
        else request("PUT", "/auth/v1/user", session,
            JSONObject().put("password", newPassword))
    }

    fun signIn(email: String, password: String): Session {
        val login = email.trim().uppercase(java.util.Locale.ROOT)
        val authEmail = if (Regex("^[FC][0-9]{9}$").matches(login)) {
            login.lowercase(java.util.Locale.ROOT) + "@login.socyto.invalid"
        } else email.trim().lowercase(java.util.Locale.ROOT)
        val response = request("POST", "/auth/v1/token?grant_type=password", null,
            JSONObject().put("email", authEmail).put("password", password))
        return Session(
            accessToken = response.getString("access_token"),
            userId = response.getJSONObject("user").getString("id"),
            refreshToken = response.getString("refresh_token"),
            expiresAtEpochMs = System.currentTimeMillis() +
                response.optLong("expires_in", 3600L) * 1000L,
            requiresPasswordChange = response.getJSONObject("user")
                .optJSONObject("app_metadata")
                ?.optBoolean("socyto_password_reset_required", false) == true
        )
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
            val rows = getRows("businesses", "business_id,business_name,account_status",
                "business_id=eq.${encode(businessId)}", session)
            if (rows.length() > 0) {
                val business = rows.getJSONObject(0)
                return AccountScope.BusinessAdmin(BusinessSummary(businessId, business.getString("business_name"), business.getString("account_status")))
            }
        }
        // A verified user has no rights until assigned to an active firm/project.
        val assignedProjects = getRows("projects", "project_id", "limit=1", session)
        return if (assignedProjects.length() > 0) AccountScope.Client else AccountScope.Unassigned
    }

    fun businesses(session: Session): List<BusinessSummary> {
        val rows = getRows("businesses", "business_id,business_name,account_status", "order=business_name.asc", session)
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { BusinessSummary(it.getString("business_id"), it.getString("business_name"), it.getString("account_status")) }
        }
    }

    /** Only the Super Admin can change the account activation state. */
    fun setBusinessStatus(session: Session, businessId: String, status: String) {
        require(status == "active" || status == "suspended" || status == "pending")
        request("POST", "/rest/v1/rpc/set_business_account_status", session,
            JSONObject().put("target_business_id", businessId)
                .put("new_status", status))
    }

    /** RLS allows only the signed-in firm, its assigned clients, or the platform admin. */
    fun branding(session: Session, businessId: String): BusinessBranding {
        val rows = getRows(
            "business_branding",
            "business_id,display_name,logo_url,logo_storage_path,primary_color_hex,secondary_color_hex,accent_color_hex,background_color_hex,text_color_hex",
            "business_id=eq.${encode(businessId)}",
            session
        )
        check(rows.length() == 1) {
            "Business branding is unavailable. Ask an administrator to configure this workspace."
        }
        val row = rows.getJSONObject(0)
        fun stringOrNull(name: String): String? =
            if (row.isNull(name)) null else row.optString(name)
        return BusinessBranding(
            businessId = businessId,
            displayName = stringOrNull("display_name")?.trim()?.takeIf { it.isNotBlank() }
                ?: error("Business display name is missing. Ask an administrator to update branding."),
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


    /** Authorized by business-scoped RLS. The UI never accepts a free-form business ID. */
    fun clients(session: Session, businessId: String): List<ClientSummary> {
        val rows = getRows("clients", "client_id,client_name",
            "business_id=eq.${encode(businessId)}&order=client_name.asc", session)
        return (0 until rows.length()).map { index ->
            rows.getJSONObject(index).let { ClientSummary(it.getString("client_id"), it.getString("client_name")) }
        }
    }

    fun createClient(session: Session, businessId: String, name: String, email: String): ClientSummary {
        require(ProjectInputRules.validName(name) && ProjectInputRules.validEmail(email))
        val body = JSONObject()
            .put("business_id", businessId)
            .put("client_name", name.trim())
            .put("client_email", email.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val row = postRecord("clients", session, body)
        return ClientSummary(row.getString("client_id"), row.getString("client_name"))
    }

    fun createProject(
        session: Session, businessId: String, clientId: String, projectName: String,
        projectType: String, location: String
    ): ProjectSummary {
        require(ProjectInputRules.validName(projectName))
        val body = JSONObject().put("business_id", businessId)
            .put("client_id", clientId)
            .put("project_name", projectName.trim())
            .put("project_type", projectType.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("site_location", location.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val row = postRecord("projects", session, body)
        return ProjectSummary(row.getString("project_id"), row.getString("business_id"),
            row.getString("project_name"))
    }

    fun addChecklistRequirement(session: Session, project: ProjectSummary, title: String, effort: Int) {
        require(ProjectInputRules.validName(title) && effort in 1..3)
        postRecord("project_requirements", session, JSONObject()
            .put("business_id", project.businessId).put("project_id", project.id)
            .put("requirement_name", title.trim()).put("tracking_type", "checklist")
            .put("effort_score", effort))
    }

    fun addQuantityRequirement(
        session: Session, project: ProjectSummary, title: String, effort: Int,
        requiredQuantity: Double, unit: String
    ) {
        require(ProjectInputRules.validName(title) && effort in 1..3 &&
            ProjectInputRules.validQuantity(requiredQuantity) && unit.isNotBlank())
        postRecord("project_requirements", session, JSONObject()
            .put("business_id", project.businessId).put("project_id", project.id)
            .put("requirement_name", title.trim()).put("tracking_type", "quantity")
            .put("effort_score", effort).put("required_quantity", requiredQuantity)
            .put("unit_name", unit.trim()))
    }


    fun requirements(session: Session, project: ProjectSummary): List<RequirementSummary> {
        val rows = getRows("project_requirements",
            "requirement_id,requirement_name,tracking_type,effort_score,is_completed,required_quantity,completed_quantity,unit_name",
            "business_id=eq.${encode(project.businessId)}&project_id=eq.${encode(project.id)}&order=display_order.asc",
            session)
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { item ->
                RequirementSummary(
                    id = item.getString("requirement_id"),
                    title = item.getString("requirement_name"),
                    type = item.getString("tracking_type"),
                    effortScore = item.getInt("effort_score"),
                    completed = item.getBoolean("is_completed"),
                    requiredQuantity = if (item.isNull("required_quantity")) null else item.getDouble("required_quantity"),
                    completedQuantity = item.getDouble("completed_quantity"),
                    unit = if (item.isNull("unit_name")) null else item.getString("unit_name")
                )
            }
        }
    }

    fun updateRequirement(
        session: Session, project: ProjectSummary, requirement: RequirementSummary,
        completed: Boolean, completedQuantity: Double
    ) {
        require(completedQuantity.isFinite() && completedQuantity >= 0) {
            "Completed quantity must be a nonnegative finite number."
        }
        require(requirement.type == "checklist" || requirement.type == "quantity")
        if (requirement.type == "quantity") {
            val required = requireNotNull(requirement.requiredQuantity) {
                "Quantity requirement is missing a required quantity."
            }
            require(ProgressMath.isValidQuantityUpdate(required, completedQuantity)) {
                "Completed quantity must be between zero and the required quantity."
            }
        }
        val body = JSONObject()
        if (requirement.type == "checklist") body.put("is_completed", completed)
        else body.put("completed_quantity", completedQuantity)
        request("PATCH",
            "/rest/v1/project_requirements?business_id=eq.${encode(project.businessId)}" +
            "&project_id=eq.${encode(project.id)}&requirement_id=eq.${encode(requirement.id)}",
            session, body)
    }

    fun publishProgress(session: Session, project: ProjectSummary, note: String) {
        require(note.length <= 4000)
        request("POST", "/rest/v1/rpc/publish_project_progress", session,
            JSONObject().put("target_business_id", project.businessId)
                .put("target_project_id", project.id)
                .put("publication_note", note.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL))
    }

    fun materialReceipts(session: Session, project: ProjectSummary): List<MaterialReceiptSummary> {
        val rows = getRows("material_receipts",
            "material_receipt_id,material_name,received_quantity,unit_name,total_amount",
            "business_id=eq.${encode(project.businessId)}&project_id=eq.${encode(project.id)}&order=created_at.desc",
            session)
        return (0 until rows.length()).map { index ->
            rows.getJSONObject(index).let {
                MaterialReceiptSummary(it.getString("material_receipt_id"),
                    it.getString("material_name"), it.getDouble("received_quantity"),
                    it.getString("unit_name"), it.getDouble("total_amount"))
            }
        }
    }

    fun addMaterialReceipt(
        session: Session, project: ProjectSummary, materialName: String,
        quantity: Double, unit: String, rate: Double, supplier: String,
        receivedDate: String
    ) {
        require(ProjectInputRules.validName(materialName) &&
            ProjectInputRules.validQuantity(quantity) &&
            ProjectInputRules.validRate(rate) && unit.isNotBlank())
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(receivedDate))
        postRecord("material_receipts", session, JSONObject()
            .put("business_id", project.businessId).put("project_id", project.id)
            .put("material_name", materialName.trim())
            .put("received_quantity", quantity).put("unit_name", unit.trim())
            .put("unit_rate", rate)
            .put("supplier_name", supplier.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("received_date", receivedDate))
    }

    private fun postRecord(table: String, session: Session, body: JSONObject): JSONObject {
        val result = request("POST", "/rest/v1/$table", session, body, returnRecords = true)
        val rows = JSONArray(result.getString("_rows"))
        check(rows.length() == 1) { "Server did not return the saved record." }
        return rows.getJSONObject(0)
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

    private fun refreshSession(session: Session) {
        check(session.refreshToken.isNotBlank()) { "Session expired. Please sign in again." }
        val response = request(
            "POST", "/auth/v1/token?grant_type=refresh_token", null,
            JSONObject().put("refresh_token", session.refreshToken)
        )
        session.accessToken = response.getString("access_token")
        session.refreshToken = response.getString("refresh_token")
        session.expiresAtEpochMs = System.currentTimeMillis() +
            response.optLong("expires_in", 3600L) * 1000L
    }

    private fun request(method: String, path: String, session: Session?,
                        payload: JSONObject? = null, returnRecords: Boolean = false): JSONObject {
        check(isConfigured) { "Configure SOCYTO backend before signing in." }
        if (session != null && System.currentTimeMillis() + 60000 >= session.expiresAtEpochMs) {
            refreshSession(session)
        }
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
            if (returnRecords) connection.setRequestProperty("Prefer", "return=representation")
            if (session != null) connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Keep credentials and raw server responses out of the user interface.
                val authLogin = path.startsWith("/auth/v1/token?grant_type=password")
                val onboardingError = if (path == "/functions/v1/client-onboarding") {
                    runCatching {
                        val responseText = connection.errorStream
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        JSONObject(responseText).optString("error").take(220)
                    }.getOrDefault("")
                } else ""
                val message = when {
                    onboardingError.isNotBlank() -> onboardingError
                    authLogin && status == 400 ->
                        "Email or password is incorrect. Check your credentials and try again."
                    authLogin && status == 429 ->
                        "Too many sign-in attempts. Please try again later."
                    status == 401 || status == 403 ->
                        "Access denied. Check account permissions."
                    else -> "Service request failed (HTTP $status)."
                }
                throw IllegalStateException(message)
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            // PostgREST RPCs may return JSON scalar values (for example a UUID string
            // from publish_project_progress), arrays, objects or an empty 204 response.
            // A successful write must not be treated as failed just because its response
            // is a JSON string rather than a JSON object.
            if (text.isBlank()) return JSONObject()
            return when (val parsed = JSONTokener(text).nextValue()) {
                is JSONObject -> parsed
                is JSONArray -> JSONObject().put("_rows", parsed.toString())
                else -> JSONObject().put("_value", parsed)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
