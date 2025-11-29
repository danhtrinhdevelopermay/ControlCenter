plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreFile = rootProject.file("release-keystore.jks")
val useReleaseSigning = keystoreFile.exists() && keystoreFile.length() > 0

android {
    namespace = "com.example.controlcenter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.controlcenter"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    if (useReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = "android123"
                keyAlias = "release"
                keyPassword = "android123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (useReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
