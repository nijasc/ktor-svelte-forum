plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    implementation(projects.database)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.sessions)
}
