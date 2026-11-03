plugins {
    id("forum.library")
    `java-test-fixtures`
}

dependencies {
    api(projects.core)
    api(libs.bundles.persistence)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.postgresql)
}

// A shared Postgres for repository tests: `testImplementation(testFixtures(projects.database))`.
dependencies {
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.bundles.testing)
}
