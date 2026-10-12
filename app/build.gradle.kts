plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
}

android {
  namespace = "io.github.g00fy2.quickiesample"
  defaultConfig {
    applicationId = "com.aistudio.quickie.xwtrxv"
    versionCode = 1
    versionName = "1.0"
  }
  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }
  buildTypes {
    getByName("debug") {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    getByName("release") {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    buildConfig = true
    viewBinding = true
  }
  lint {
    abortOnError = false
    checkDependencies = false
  }
}

dependencies {
  implementation(project(":quickie"))
  implementation(libs.google.materialDesign)
  implementation(libs.androidx.core.splashscreen)

  implementation(libs.androidx.camera)
  implementation(libs.androidx.cameraLifecycle)
  implementation(libs.androidx.cameraPreview)
  implementation(libs.mlkit.barcodeScanning)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
}
