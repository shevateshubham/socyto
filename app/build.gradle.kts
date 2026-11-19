plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    buildFeatures { buildConfig = true }
    namespace = "com.socyto.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.socyto.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        // Publishable client keys are not secrets; never use a service-role key in an APK.
        val backendUrl = providers.environmentVariable("SOCYTO_SUPABASE_URL").orElse("").get()
        val publishableKey = providers.environmentVariable("SOCYTO_SUPABASE_PUBLISHABLE_KEY").orElse("").get()
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

 
// A production APK is never built without a real backend configuration.
tasks.register("verifyProductionBackend") {
    doLast {
        val url = providers.environmentVariable("SOCYTO_SUPABASE_URL").orElse("").get()
        val key = providers.environmentVariable("SOCYTO_SUPABASE_PUBLISHABLE_KEY").orElse("").get()
        check(url.startsWith("https://") && key.isNotBlank()) {
            "Release blocked: configure the real SOCYTO Supabase URL and publishable key."
        }
        check(!key.startsWith("sb_secret_") && !key.startsWith("service_role")) {
            "Release blocked: privileged backend secrets must never be embedded in Android."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyProductionBackend")
}
