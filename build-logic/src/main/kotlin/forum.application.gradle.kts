// The deployable module. Ktor, ktor-svelte and the fat JAR are configured in its own build file.
plugins {
    id("forum.kotlin-base")
    id("forum.package-prefix")
    application
}
