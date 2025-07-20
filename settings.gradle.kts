pluginManagement {
    repositories {
        google()
        gradlePluginPortal() // REQUIRED for Kotlin plugin
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyQRApp"
include(":app")
