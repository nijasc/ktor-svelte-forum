package dev.nilsschlegel.forum.core

import java.io.File

/** The first `.env` in the working directory or above it. Gradle runs the app from `app/`, the file sits in the repo root. */
private fun findEnvFile(): File? =
    generateSequence(File("").absoluteFile) {
        it.parentFile
    }.map { it.resolve(".env") }.firstOrNull { it.isFile }

private val localEnv: Map<String, String> by lazy {
    val file = findEnvFile()
    if (file == null) {
        emptyMap()
    } else {
        file
            .readLines()
            .filter { "=" in it && !it.trimStart().startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .filterValues { it.isNotEmpty() }
    }
}

fun env(name: String): String? = System.getenv(name) ?: localEnv[name]

fun requireEnv(name: String): String = env(name) ?: error("Set $name in the environment or in .env. See .env.example.")
