import io.github.nijasc.ktor.svelte.gradle.DaisyTheme
import io.github.nijasc.ktor.svelte.gradle.LocaleStrategy

plugins {
    id("forum.application")
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktor.svelte)
}

application {
    mainClass = "dev.nilsschlegel.forum.ApplicationKt"
}

ktor {
    fatJar {
        archiveFileName = "ktor-svelte-forum-all.jar"
    }
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
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
    implementation(projects.core)
    implementation(projects.database)
    implementation(projects.accounts)
    implementation(projects.forum)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback)
}
