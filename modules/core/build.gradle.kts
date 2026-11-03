plugins {
    id("forum.feature")
}

dependencies {
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.auth)
}
