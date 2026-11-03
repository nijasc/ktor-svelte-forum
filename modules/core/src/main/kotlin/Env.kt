package dev.nilsschlegel.forum.core

import java.io.File

private val localEnv: Map<String, String> by lazy {
    val file = File(".env")
    if (!file.isFile) {
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
