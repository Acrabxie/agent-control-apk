plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseStoreFilePath = providers.gradleProperty("agentControlUploadStoreFile")
    .orElse(providers.environmentVariable("AGENT_CONTROL_UPLOAD_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("agentControlUploadStorePassword")
    .orElse(providers.environmentVariable("AGENT_CONTROL_UPLOAD_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("agentControlUploadKeyAlias")
    .orElse(providers.environmentVariable("AGENT_CONTROL_UPLOAD_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("agentControlUploadKeyPassword")
    .orElse(providers.environmentVariable("AGENT_CONTROL_UPLOAD_KEY_PASSWORD"))
    .orNull
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.xiehaibo.agentcontrol"
    buildToolsVersion = "36.0.0"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.acrab.agentcontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 50
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField(
            "String",
            "AGENT_CONTROL_DEFAULT_RELAY_URL",
            providers.gradleProperty("agentControlDefaultRelayUrl")
                .orElse(providers.environmentVariable("AGENT_CONTROL_DEFAULT_RELAY_URL"))
                .orElse("")
                .get()
                .asBuildConfigString(),
        )
    }

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
            useVersion("1.10.2")
        }
        if (requested.group == "androidx.startup" && requested.name == "startup-runtime") {
            useVersion("1.2.0")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("androidx.startup:startup-runtime:1.2.0")

    testImplementation(libs.junit)
    testImplementation(libs.truth)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
