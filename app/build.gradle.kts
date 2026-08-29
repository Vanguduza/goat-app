plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val supabaseUrl = providers.gradleProperty("FARM_OS_SUPABASE_URL")
    .orElse(providers.environmentVariable("FARM_OS_SUPABASE_URL"))
    .orElse("")
val supabasePublishableKey = providers.gradleProperty("FARM_OS_SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("FARM_OS_SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")
val meiliHost = providers.gradleProperty("FARM_OS_MEILI_HOST")
    .orElse(providers.environmentVariable("FARM_OS_MEILI_HOST"))
    .orElse("")

android {
    namespace = "com.farmos.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.farmos.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-foundation"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.get()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabasePublishableKey.get()}\"")
        buildConfigField("String", "MEILI_HOST", "\"${meiliHost.get()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:sync"))
    implementation(project(":core:design"))
    implementation(project(":domain:goat"))
    implementation(project(":feature:goat"))

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    testImplementation(kotlin("test"))
}
