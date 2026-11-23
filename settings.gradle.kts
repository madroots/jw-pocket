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
    }
}

rootProject.name = "jw-pocket"

// Shared and UI-free: API client, cache, database, settings, repositories.
include(":core")
// Shared playback engine: Media3 session service, player connection, downloads.
include(":media")
// The two apps. Each one only holds its own screens.
include(":app-phone")
include(":app-tv")
// Desktop launcher that runs the phone app in the Android emulator (for previews and checks).
include(":tools:preview")
