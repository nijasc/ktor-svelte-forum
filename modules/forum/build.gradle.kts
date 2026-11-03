plugins {
    id("forum.feature")
}

dependencies {
    api(projects.core)
    implementation(projects.database)
    implementation(projects.accounts)
    testImplementation(testFixtures(projects.database))
}

// FrontendLimitsTest reads this file, so Gradle must rerun the tests when it changes.
tasks.test {
    inputs.file(rootProject.file("app/frontend/src/lib/limits.ts")).withPropertyName("frontendLimits")
}
