plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "life.absinthe.pixelota"
    compileSdk = 37

    defaultConfig {
        applicationId = "life.absinthe.pixelota"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "life.absinthe.pixelota.UiCheck"
    }

    experimentalProperties["android.experimental.r8.dex-startup-optimization"] = true

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
        resources {
            excludes += listOf(
                "DebugProbesKt.bin",
                "META-INF/*.version",
                "META-INF/androidx/**",
                "META-INF/MANIFEST.MF",
                "META-INF/versions/**",
                "kotlin/**",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}
