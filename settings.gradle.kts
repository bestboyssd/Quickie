rootProject.name = "Quickie"
include(":quickie", ":app")

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    google()
    mavenCentral()
  }
}

pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}
