plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    filter {
        exclude { it.file.invariantSeparatorsPath.contains("/build/") }
    }
}
