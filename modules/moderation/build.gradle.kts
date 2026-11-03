plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    implementation(projects.database)
    implementation(projects.accounts)
    implementation(projects.forum)
    testImplementation(testFixtures(projects.database))
}
