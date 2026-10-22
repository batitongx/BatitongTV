plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.batitong.batitongtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.batitong.batitongtv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -Wall -Wextra"
            }
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Placeholder, production replaces with release keystore
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        dataBinding = false
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX & Material 3 TV UI
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // AndroidX Media3 (Semua versi terpin eksplisit 1.5.1)
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtmp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    // Justifikasi: Bridge resmi DataSource Media3 dengan OkHttpClient ber-CertificatePinner (§7-C4)
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Jaringan & Serialisasi
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Image loading untuk tvg-logo channel
    // Justifikasi: Library pemuat gambar modern, ringan, hemat alokasi bitmap untuk hardware Android TV
    implementation("io.coil-kt:coil:2.7.0")

    // Play Integrity API (§7-C3)
    // Justifikasi: Menghasilkan token integritas resmi Google Play untuk verifikasi integritas di gateway
    implementation("com.google.android.play:integrity:1.4.0")

    // Unit Testing (JVM)
    testImplementation("junit:junit:4.13.2")
    // Justifikasi: org.json di JVM environment untuk menjalankan unit test G1 (#EXTHTTP) tanpa mocking Android framework
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
