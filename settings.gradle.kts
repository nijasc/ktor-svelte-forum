pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ktor-svelte-forum"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Every folder in modules/ with a build file is a module, so adding one needs no change here.
file("modules")
    .listFiles { dir -> dir.resolve("build.gradle.kts").isFile }
    .orEmpty()
    .sortedBy { it.name }
    .forEach { dir ->
        include(":${dir.name}")
        project(":${dir.name}").projectDir = dir
    }
