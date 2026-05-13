pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "AICompanion"

// App
include(":app")

// Core modules
include(":core:core-common")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-database")
include(":core:core-di")

// Domain & Data
include(":domain")
include(":data")

// Features
include(":feature:feature-chat")
include(":feature:feature-apiconfig")
include(":feature:feature-persona")
include(":feature:feature-memory")
include(":feature:feature-live2d")
include(":feature:feature-voice")
include(":feature:feature-settings")
