import java.util.Locale

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.materialthemebuilder)
}

fun getGitHashCommit(): String {
    return try {
        val processBuilder = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        val process = processBuilder.start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

val gitHash: String = getGitHashCommit().uppercase(Locale.getDefault())

android {
    namespace = "com.wmods.wppenhacer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wmods.wppenhacer"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0-DEV ($gitHash)"
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
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            if (project.hasProperty("minify") && project.properties["minify"].toString()
                    .toBoolean()
            ) {
                isMinifyEnabled = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
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

    materialThemeBuilder {
        themes {
            for ((name, color) in listOf(
                "Green" to "4FAF50"
            )) {
                create("Material$name") {
                    lightThemeFormat = "ThemeOverlay.Light.%s"
                    darkThemeFormat = "ThemeOverlay.Dark.%s"
                    primaryColor = "#$color"
                }
            }
        }
        // Add Material Design 3 color tokens (such as palettePrimary100) in generated theme
        // rikka.material >= 2.0.0 provides such attributes
        generatePalette = true
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
    implementation(libs.material)
    implementation(libs.rikkax.material)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.preference)
    implementation(libs.rikkax.widget.borderview)
    implementation(libs.jstyleparser)
    implementation(libs.guava)
    implementation(libs.okhttp)
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