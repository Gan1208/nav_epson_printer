import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** NAV SQL connection, read from local.properties so it never reaches git. */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun navSql(key: String, default: String = ""): String =
    "\"" + localProperties.getProperty("navSql.$key", default)
        .replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.kowloondairy.navprint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kowloondairy.navprint"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "NAV_SQL_HOST", navSql("host"))
        buildConfigField("int", "NAV_SQL_PORT", localProperties.getProperty("navSql.port", "1433").ifBlank { "1433" })
        buildConfigField("String", "NAV_SQL_INSTANCE", navSql("instance"))
        buildConfigField("String", "NAV_SQL_DATABASE", navSql("database", "KDLNAV_HD"))
        buildConfigField("String", "NAV_SQL_USER", navSql("user"))
        buildConfigField("String", "NAV_SQL_PASSWORD", navSql("password"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.jtds)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
