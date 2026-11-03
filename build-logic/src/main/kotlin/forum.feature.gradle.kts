// A feature module: a library that adds Ktor routes. Features depend on :core and :database, never on each other
// except through a published contract (see the README).
plugins {
    id("forum.library")
    id("io.ktor.plugin")
}

// Route inference is a compiler plugin, so it only sees routes in modules that apply the ktor plugin.
// Without it the generated TypeScript client types every call in this module as `unknown`.
ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

// The ktor plugin also adds app tasks. Only :app builds a JAR, so a root-level `./gradlew buildFatJar` must skip features.
tasks.matching { it.name in setOf("buildFatJar", "runFatJar", "shadowJar") }.configureEach {
    enabled = false
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    api(libs.findBundle("ktor-feature").get())
    testImplementation(libs.findBundle("ktor-testing").get())
}
