plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")version "4.4.4"
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.app.findback"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.findback"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("org.json:json:20210307")

    //config cho AI
    // Gemini SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Gson (parse JSON)
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp (nếu dùng REST API thay vì SDK)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // FusedLocation (cho gửi vị trí)
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")


    // Firebase Auth
    implementation("com.google.firebase:firebase-auth")

    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database")

    // Fragment KTX — cần cho viewModels() trong Fragment
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    // Activity KTX — cần cho viewModels() trong Activity
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.onesignal:OneSignal:[5.1.6, 5.9.99]")

    implementation("de.hdodenhof:circleimageview:3.1.0")
    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.cloudinary:cloudinary-android:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")



}