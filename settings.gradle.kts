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

rootProject.name = "DONA-Clone"

include(":app")

include(":core:common")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:testing")

include(":feature:login")
include(":feature:houses")
include(":feature:dashboard")
include(":feature:devices")
include(":feature:ambiences")
include(":feature:settings")
