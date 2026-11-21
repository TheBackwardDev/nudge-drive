pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jogamp.org/deployment/maven") // JCEF's OpenGL bits (desktop editor)
    }
}
rootProject.name = "NudgeDrive"
include(":app", ":shared", ":desktop")
