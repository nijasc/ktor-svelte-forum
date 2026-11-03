plugins {
    id("forum.library")
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
