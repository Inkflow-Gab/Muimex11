plugins {
    alias(libs.plugins.android.application)
}

// Release signing is provided by GitHub Actions as environment variables.
// When they are unset (e.g. building locally), the release APK stays unsigned.
val releaseKeystorePath: String? = System.getenv("MUIMEX_KEYSTORE")

android {
    namespace = "com.muimex11.official"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.muimex11.official"
        minSdk = 24
        targetSdk = 37
        versionCode = 29
        versionName = "3.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("MUIMEX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUIMEX_KEY_ALIAS")
                keyPassword = System.getenv("MUIMEX_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = if (releaseKeystorePath != null) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("dev.rikka.shizuku:api:13.1.0")
    implementation("dev.rikka.shizuku:provider:13.1.0")
    implementation("com.github.GrenderG:Toasty:1.5.2")
    implementation("com.google.mlkit:translate:17.0.3")
}