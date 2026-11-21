plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.muyassir.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.muyassir.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("MYS_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("MYS_VERSION_NAME") ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("MYS_KS") ?: "upload.jks")
            storePassword = System.getenv("MYS_KS_PASS")
            keyAlias = "upload"
            keyPassword = System.getenv("MYS_KS_PASS")
            enableV1Signing = true; enableV2Signing = true
        }
        // Public, fixed key for preview APKs only (never used for the store), so updates install over each other.
        create("preview") {
            storeFile = file("preview.jks")
            storePassword = "muyassir-preview"
            keyAlias = "preview"
            keyPassword = "muyassir-preview"
            enableV1Signing = true; enableV2Signing = true
        }
    }

    // One APK per processor type keeps the download small (about a third of the universal APK).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (!System.getenv("MYS_KS_PASS").isNullOrEmpty()) signingConfigs.getByName("release")
            else signingConfigs.getByName("preview")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    androidResources { noCompress += "traineddata" }

    lint {
        checkOnly += setOf("NewApi", "InlinedApi")
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(libs.pdfbox)
    implementation(libs.zxing)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.docscan)
    implementation(libs.mlkit.codescan)
    implementation(libs.mlkit.segment)
    implementation(libs.mlkit.selfie)
    implementation(libs.gms.base)
    implementation(libs.tesseract)
    implementation(libs.exifinterface)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
