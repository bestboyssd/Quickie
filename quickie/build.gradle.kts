plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
}

android {
  namespace = "io.github.g00fy2.quickie"
  resourcePrefix = "quickie"
  defaultConfig {
    consumerProguardFiles("consumer-proguard-rules.pro")
  }
  buildFeatures {
    viewBinding = true
  }
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)

  implementation(libs.androidx.camera)
  implementation(libs.androidx.cameraLifecycle)
  implementation(libs.androidx.cameraPreview)

  implementation(libs.mlkit.barcodeScanning)

  testImplementation(libs.test.junit)
  testRuntimeOnly(libs.test.junit.platformLauncher)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
