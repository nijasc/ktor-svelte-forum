plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    implementation(projects.database)
    implementation(projects.accounts)
    testImplementation(testFixtures(projects.database))
}
