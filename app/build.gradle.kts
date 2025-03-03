plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.martiproject"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.martiproject"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Hilt
    implementation(libs.hilt.android.v248)
    kapt(libs.hilt.android.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.adapter.rxjava2)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.logging.interceptor)
    implementation(libs.rxandroid)
    implementation(libs.rxjava)

    // Lifecycle
    implementation(libs.androidx.lifecycle.livedata.ktx.v283)

    // Google Maps ve Konum servisleri
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Google Maps
    implementation(libs.play.services.maps.v1810)
    implementation(libs.play.services.location.v2101)
    implementation(libs.gson)
}

kapt {
    correctErrorTypes = true
}