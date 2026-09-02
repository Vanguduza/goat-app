plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.farmos.core.design"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(platform("androidx.compose:compose-bom:2026.08.00"))
    api("androidx.compose.material3:material3")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.runtime:runtime")
}
