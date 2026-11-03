import io.github.nijasc.ktor.svelte.gradle.DaisyTheme
import io.github.nijasc.ktor.svelte.gradle.LocaleStrategy
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings

plugins {
    id("kotlin-conventions")
    alias(libs.plugins.ktor)
    application
    idea
    alias(libs.plugins.idea.ext)
    alias(libs.plugins.ktor.svelte)
}

group = "dev.nilsschlegel.forum"
version = "0.1.0"

application {
    mainClass = "dev.nilsschlegel.forum.ApplicationKt"
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = "dev.nilsschlegel.forum"
        }
    }
}

tasks.shadowJar {
    mergeServiceFiles()
}

svelte {
    libraries {
        tailwind {
            typography()
        }
        daisyUi {
            themes(DaisyTheme.Light, DaisyTheme.Dark)
            defaultTheme(DaisyTheme.Light)
            prefersDark(DaisyTheme.Dark)
        }
        lucide()
        i18n {
            locales("en", "de")
            defaultLocale = "en"
            strategy = LocaleStrategy.Cookie
        }
    }
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.logback)
}
