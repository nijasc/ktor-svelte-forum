plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    api(projects.mail)
    implementation(projects.database)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.sessions)
    testImplementation(testFixtures(projects.database))
}
