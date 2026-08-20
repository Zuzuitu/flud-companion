plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "media.alexlab.fludremote"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "media.alexlab.fludremote"
        minSdk = 23
        targetSdk = 33
        versionCode = 31
        versionName = "0.24.1"
    }

    buildTypes {
        debug {
            // Uses Android's generated debug signing key. No project keystore is committed.
        }
        release {
            isMinifyEnabled = false

            // Optional release signing supplied by CI/local environment only.
            val keystorePath = System.getenv("FLUD_SIGNING_KEYSTORE_PATH")
            val storePasswordValue = System.getenv("FLUD_SIGNING_STORE_PASSWORD")
            val keyAliasValue = System.getenv("FLUD_SIGNING_KEY_ALIAS")
            val keyPasswordValue = System.getenv("FLUD_SIGNING_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() &&
                !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                signingConfig = signingConfigs.create("releaseCi") {
                    storeFile = file(keystorePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
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
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
}
