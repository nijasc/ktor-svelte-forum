# ktor-svelte-forum

A Ktor app with a SvelteKit frontend, served from one JAR on one port by [ktor-svelte](https://github.com/nijasc/ktor-svelte).

## Develop

```bash
docker compose up -d
./gradlew svelteDevAll
```

Then open http://localhost:8080. In IntelliJ, run the `Dev` configuration. On Windows, use `gradlew.bat`.

## Check

```bash
./gradlew check svelteCheck
./gradlew svelteRun --script=lint
```

Format with `./gradlew ktlintFormat` and `./gradlew svelteRun --script=format`.

## Build

```bash
./gradlew buildFatJar
java -jar build/libs/ktor-svelte-forum-all.jar
```

## Docker

```bash
docker build -t ktor-svelte-forum .
docker run -p 8080:8080 --env-file .env ktor-svelte-forum
```

Inside a container, `localhost` is the container itself, so point `DATABASE_URL` at your database host.

## Configuration

The app reads the environment first, then a `.env` file in the working directory. `.env.example` lists every setting, and `.env` is not committed.

| Setting | Purpose |
|---|---|
| `PORT` | HTTP port, 8080 by default |
| `SESSION_SECRET` | Signs session cookies, at least 32 characters. Without it, a new one is made at each start and everyone is signed out |
| `COOKIE_SECURE` | Send the session cookie over HTTPS only. Defaults to `true`, so set `false` only for local HTTP |
| `DATABASE_URL` | JDBC URL of the Postgres database |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Used by `compose.yaml` and by the app |

## Sign in

`Auth.kt` has the register, login, logout and current user routes, with passwords hashed by PBKDF2. Wrap your own routes in `authenticate("session")`. Users are stored in Postgres. Registration is open and login has no rate limit, so restrict both before going live. The pages in `frontend/src/routes/login` and `frontend/src/routes/account` use `frontend/src/lib/auth.ts`.

## Database

`docker compose up -d` starts Postgres on this machine, using the settings in `.env`. `Database.kt` connects when the app starts and Flyway runs any new migration in `src/main/resources/db/migration`. Add a new file for each change and never edit one that has already run. `POST /api/visits` shows the pattern with a small example table, so replace it with your own.

## Project layout

Kotlin files sit directly in `src/main/kotlin`, without package folders. The `idea-ext` plugin in `build.gradle.kts` sets a package prefix, so IntelliJ creates new files in the right package. Reload the Gradle project if IntelliJ does not pick it up.

`build-logic` holds the `kotlin-conventions` plugin with the Kotlin and ktlint setup. To add a module, add it to `settings.gradle.kts` and apply `id("kotlin-conventions")` in its build file.

