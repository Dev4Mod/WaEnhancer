plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.wmods.wppenhacer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wmods.wppenhacer"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        signingConfigs.create("config") {
            val androidStoreFile = project.findProperty("androidStoreFile") as String?
            if (!androidStoreFile.isNullOrEmpty()) {
                storeFile = rootProject.file(androidStoreFile)
                storePassword = project.property("androidStorePassword") as String
                keyAlias = project.property("androidKeyAlias") as String
                keyPassword = project.property("androidKeyPassword") as String
            }
        }

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }


    }

    packaging {
        resources {
            excludes += "META-INF/**"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
        }
    }

    buildTypes {
        all {
            signingConfig = if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.colorpicker)
    implementation(libs.dexkit)
    compileOnly(libs.libxposed.legacy)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.rikkax.appcompat)
    implementation(libs.rikkax.core)
    implementation(libs.rikkax.material)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.preference)
    implementation(libs.rikkax.widget.borderview)

//    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)

}

configurations.all {
    exclude("org.jetbrains", "annotations")
    exclude("androidx.appcompat", "appcompat")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}