plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.answufeng.net"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

ktlint {
    android.set(true)
    ignoreFailures = false
}

dependencies {
    api(libs.okhttp)
    api(libs.okhttp.logging.interceptor)
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}

apply(from = "${rootDir}/gradle/publish.gradle.kts")

