// The root project only coordinates. Code lives in app/ and modules/.
plugins {
    // Puts the Kotlin plugin on one shared classpath, so the ktor plugin in :app does not load a second copy.
    alias(libs.plugins.kotlin.jvm) apply false
}
