// Kotlin files sit directly in src/main/kotlin. This tells IntelliJ which package new files belong to:
// dev.nilsschlegel.forum for the app, dev.nilsschlegel.forum.<module> for every other module.
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings

plugins {
    idea
    id("org.jetbrains.gradle.plugin.idea-ext")
}

val rootPackage = "dev.nilsschlegel.forum"
val modulePackage = if (project.name == "app") rootPackage else "$rootPackage.${project.name}"

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = modulePackage
            packagePrefix["src/test/kotlin"] = modulePackage
        }
    }
}
