plugins {
    id("forum.library")
}

dependencies {
    api(projects.core)
    api(libs.bundles.persistence)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.postgresql)
}
