plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

dependencies {
    kover(project(":app"))
    kover(project(":core:common"))
    kover(project(":core:model"))
    kover(project(":core:network"))
    kover(project(":core:database"))
    kover(project(":core:domain"))
    kover(project(":core:data"))
    kover(project(":core:designsystem"))
    kover(project(":feature:login"))
    kover(project(":feature:houses"))
    kover(project(":feature:dashboard"))
    kover(project(":feature:devices"))
    kover(project(":feature:ambiences"))
    kover(project(":feature:settings"))
}
