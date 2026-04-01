plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alissgmr.vibboost"
    compileSdk = 35 // Android 15/16 desteği için güncellendi

    defaultConfig {
        applicationId = "com.alissgmr.vibboost"
        minSdk = 28 // DynamicsProcessing (Wavelet Motoru) için şart
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-PRO-WAVELET"

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
}

dependencies {
    // AndroidX Core ve UI Bileşenleri
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Arka plan servisi ve bildirimler için gerekli
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
}
