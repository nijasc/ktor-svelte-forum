plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    implementation(projects.database)
    implementation(projects.accounts)

    // The search reads the forum's tables, so its tests need the forum's migrations and routes.
    testImplementation(projects.forum)
    testImplementation(testFixtures(projects.database))
}
