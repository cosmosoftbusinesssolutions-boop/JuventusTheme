import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cosmosoft.tvtheme"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cosmosoft.tvtheme"
        minSdk = 26
        // Google Play requires new apps and updates to target a recent API level.
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    // "play" is the Google Play build: no accessibility service and no broad photo access, per Play policy.
    // "sideload" is the APK for installing by USB stick or adb, with the Home button helper and photo grid.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "HOME_BUTTON_HELPER", "false")
            buildConfigField("boolean", "SYSTEM_PHOTO_PICKER", "true")
        }
        create("sideload") {
            dimension = "store"
            buildConfigField("boolean", "HOME_BUTTON_HELPER", "true")
            buildConfigField("boolean", "SYSTEM_PHOTO_PICKER", "false")
        }
    }

    // Release signing comes from keystore.properties in the project root, which is never committed.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 strips the thousands of unused Material icons and other dead code from release builds.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.billing)
}
