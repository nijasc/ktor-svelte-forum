// Applied to every module: Kotlin, serialization, ktlint, tests and the JDK.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(libs.findBundle("testing").get())
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude { it.file.invariantSeparatorsPath.contains("/build/") }
    }
}
