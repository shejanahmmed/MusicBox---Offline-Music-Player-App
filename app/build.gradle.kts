plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shejan.musicbox"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.shejan.musicbox"
        minSdk = 24
        targetSdk = 36
        versionCode = 26
        versionName = "2.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
    }

    lint {
        disable += setOf("ForegroundServicePermission", "SelectedPhotoAccess", "ScopedStorage")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.exifinterface)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.glide)
}

tasks.register("copyMappingFile") {
    description = "Copies the release mapping.txt to the Desktop"
    group = "build"
    // Run after bundleRelease to ensure mapping file exists
    mustRunAfter("bundleRelease") 
    
    doLast {
        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        if (mappingFile.exists()) {
            val desktopDir = File(System.getProperty("user.home"), "Desktop")
            val destFile = File(desktopDir, "mapping.txt")
            mappingFile.copyTo(destFile, overwrite = true)
            println("SUCCESS: Mapping file copied to: ${destFile.absolutePath}")
        } else {
            println("WARNING: Mapping file not found at: ${mappingFile.absolutePath}. Make sure minifyEnabled is true.")
        }
    }
}

// Ensure copyMappingFile runs when we build a bundle
afterEvaluate {
    tasks.named("bundleRelease") {
        finalizedBy("copyMappingFile")
    }
}