import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Public client configuration comes from CI or the local build environment.
// Never embed an environment-specific production endpoint or key in source.
val socytoBackendUrl = providers.environmentVariable("SOCYTO_SUPABASE_URL").orNull.orEmpty().trim()
val socytoPublishableKey = providers.environmentVariable("SOCYTO_SUPABASE_PUBLISHABLE_KEY").orNull.orEmpty().trim()

android {
    buildFeatures { buildConfig = true }
    namespace = "com.socyto.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.socyto.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.8.7"
        // Publishable client keys are not secrets; never use a service-role key in an APK.
        val backendUrl = socytoBackendUrl
        val publishableKey = socytoPublishableKey
        buildConfigField("String", "SUPABASE_URL", "\""+backendUrl.replace("\\","\\\\").replace("\"","\\\"")+"\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\""+publishableKey.replace("\\","\\\\").replace("\"","\\\"")+"\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

 
// A production APK is never built without a real backend configuration.
tasks.register("verifyProductionBackend") {
    doLast {
        val url = socytoBackendUrl
        val key = socytoPublishableKey
        check(url.startsWith("https://") && runCatching {
            val parsed = URI(url)
            parsed.host?.endsWith(".supabase.co") == true && parsed.userInfo == null &&
                parsed.query == null && parsed.fragment == null
        }.getOrDefault(false)) {
            "Release blocked: supply SOCYTO_SUPABASE_URL as a valid HTTPS Supabase project URL."
        }
        check(key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length) {
            "Release blocked: supply SOCYTO_SUPABASE_PUBLISHABLE_KEY through the build environment."
        }
        check(!key.startsWith("sb_secret_") && !key.startsWith("service_role")) {
            "Release blocked: privileged backend secrets must never be embedded in Android."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyProductionBackend")
}
