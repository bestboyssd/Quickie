import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.BasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.parcelize) apply false
  alias(libs.plugins.ksp) apply false
}

buildscript {
  dependencies {
    classpath(libs.kotlin.plugin)
  }
}

subprojects {
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      allWarningsAsErrors = false
      progressiveMode = false
      jvmTarget = JvmTarget.JVM_17
    }
  }

  plugins.withType<BasePlugin>().configureEach {
    extensions.configure<CommonExtension> {
      compileSdk = libs.versions.androidconfig.compileSdk.get().toInt()
      buildToolsVersion = libs.versions.androidconfig.buildTools.get()

      when (this@configure) {
        is LibraryExtension -> {
          defaultConfig {
            minSdk = libs.versions.androidconfig.minSdk.get().toInt()
          }
        }
        is ApplicationExtension -> {
          defaultConfig {
            minSdk = libs.versions.androidconfig.minSdk.get().toInt()
            targetSdk = libs.versions.androidconfig.targetSdk.get().toInt()
          }
        }
      }
      compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
      }
    }
  }
}
