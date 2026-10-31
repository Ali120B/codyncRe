import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase project configuration is supplied per environment and intentionally stays out of git.
if (listOf("google-services.json", "src/dev/google-services.json", "src/prod/google-services.json")
        .any { file(it).exists() }) {
    apply(plugin = "com.google.gms.google-services")
}

// local.properties is gitignored; signing + clerk config are environment-specific.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseStore = System.getenv("CODYNC_KEYSTORE_FILE") ?: localProperties.getProperty("releaseStore")
val releaseStorePassword = System.getenv("CODYNC_KEYSTORE_PASSWORD") ?: localProperties.getProperty("releaseStorePassword")
val releaseKeyAlias = System.getenv("CODYNC_KEY_ALIAS") ?: localProperties.getProperty("releaseKeyAlias")
val releaseKeyPassword = System.getenv("CODYNC_KEY_PASSWORD") ?: localProperties.getProperty("releaseKeyPassword")
// Public Clerk publishable keys: the live instance for prod, the test instance for dev.
// CI does not have local.properties, so these defaults must be checked in.
val prodClerkPublishableKey = (
    System.getenv("CODYNC_CLERK_PUBLISHABLE_KEY")
        ?: localProperties.getProperty("prodClerkPublishableKey")
        ?: "pk_test_c3dlZXBpbmctbWFybW9zZXQtNjIwOC5jbGVyay5hY2NvdW50cy5kZXYk"
    ).trim()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val devClerkPublishableKey = (
    System.getenv("CLERK_PUBLISHABLE_KEY")
        ?: localProperties.getProperty("clerkPublishableKey")
        ?: "pk_test_c3dlZXBpbmctbWFybW9zZXQtNjIwOC5jbGVyay5hY2NvdW50cy5kZXYk"
    ).trim()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.pokai.codync.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pokai.Codync.android"
        minSdk = 31
        targetSdk = 36
        versionCode = 32
        versionName = "3.4.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "CLOUD_URL", "\"https://api.codync.dev\"")
        // Public Firebase client identifiers. CI does not have the untracked
        // google-services.json, so the app initializes Firebase from these values.
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"mybot-f2d34\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"318342768846\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyDzjhejtwZM67rA9Wi5zVeEzmTo02zr5UY\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"mybot-f2d34.firebasestorage.app\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:318342768846:android:ec25d1ac75f09213e5089f\"")
    }

    flavorDimensions += "channel"
    productFlavors {
        create("dev") {
            dimension = "channel"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Codync Dev")
            buildConfigField("String", "CLOUD_URL", "\"https://codync-cloud-dev.codyncre.workers.dev\"")
            buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$devClerkPublishableKey\"")
            buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:318342768846:android:1d3d5ee67906f071e5089f\"")
        }
        create("prod") {
            dimension = "channel"
            resValue("string", "app_name", "Codync")
            buildConfigField("String", "CLOUD_URL", "\"https://codync-cloud-dev.codyncre.workers.dev\"")
            buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$prodClerkPublishableKey\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseStore)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }
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
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:crypto"))
    implementation(project(":core:transport"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.clerk.android.api)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
