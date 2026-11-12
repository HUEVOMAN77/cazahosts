plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreBase64 = providers.environmentVariable("KS_BASE64").orNull
val keystorePassword = providers.environmentVariable("KS_PASS").orNull
val keystoreAlias = providers.environmentVariable("KS_ALIAS").orNull ?: "cazahosts"

val releaseRefType = providers.environmentVariable("GITHUB_REF_TYPE").orNull
val releaseTag = providers.environmentVariable("GITHUB_REF_NAME").orNull
val semanticVersion = releaseTag
    ?.takeIf { releaseRefType == "tag" }
    ?.removePrefix("v")
    ?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) }
val releaseVersionCode = semanticVersion
    ?.split(".")
    ?.map { it.toInt() }
    ?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
    ?: 4
val releaseVersionName = semanticVersion ?: "1.1.0"

android {
    namespace = "com.huevoman77.cazahosts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.huevoman77.cazahosts"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (keystoreBase64 != null && keystorePassword != null) {
            create("upload") {
                storeFile = rootProject.file(
                    providers.environmentVariable("KS_FILE").orNull ?: "upload-keystore.jks"
                )
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreBase64 != null) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
