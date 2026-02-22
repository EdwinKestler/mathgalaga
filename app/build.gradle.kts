plugins {
    id("com.android.application")
}

android {
    namespace = "com.robocrops.mathgalaga"
    compileSdk = 36 // 36 is preview at time of writing, use 34 for stability (adjust if needed)

    defaultConfig {
        applicationId = "com.robocrops.mathgalaga"
        minSdk = 24
        targetSdk = 34 // 36 if you are targeting preview SDKs
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // If you use version catalogs (libs. ...), make sure settings/libs.versions.toml is set up.
    // Otherwise, use explicit versions as below:

    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core.v351)
}
