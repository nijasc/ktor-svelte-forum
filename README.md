# ktor-svelte-forum

A forum built from Gradle modules, with a SvelteKit frontend, served from one JAR on one port by [ktor-svelte](https://github.com/nijasc/ktor-svelte).

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

After you add or change a route, run `./gradlew svelteGenerateTs` to update the TypeScript client in `app/frontend/src/lib/api`.

## Build

```bash
./gradlew buildFatJar
java -jar app/build/libs/ktor-svelte-forum-all.jar
```

## Docker

```bash
docker build -t ktor-svelte-forum .
docker run -p 8080:8080 --env-file .env ktor-svelte-forum
```

Inside a container, `localhost` is the container itself, so point `DATABASE_URL` at your database host.

## Release

Write commits as [conventional commits](https://www.conventionalcommits.org), like `feat(forum): add reactions` or `fix(accounts): trim usernames`. [release-please](https://github.com/googleapis/release-please) reads them on every push to `main` and keeps one open pull request that bumps `version` in `gradle.properties` and updates `CHANGELOG.md`.

To release, merge that pull request. The `Release` workflow then tags `v<version>`, runs every check, pushes `ghcr.io/<owner>/<repo>:<version>` and `:latest`, and attaches the JAR to the GitHub release. Until 1.0, `feat` bumps the minor version, `fix` bumps the patch, and a `!` or `BREAKING CHANGE` also bumps the minor.

One setting is needed once: in the repository, under Settings, Actions, General, turn on "Allow GitHub Actions to create and approve pull requests". Do not edit the version by hand.

## Configuration

The app reads the environment first, then a `.env` file in the working directory. `.env.example` lists every setting, and `.env` is not committed.

| Setting | Purpose |
|---|---|
| `PORT` | HTTP port, 8080 by default |
| `SESSION_SECRET` | Signs session cookies, at least 32 characters. Without it, a new one is made at each start and everyone is signed out |
| `COOKIE_SECURE` | Send the session cookie over HTTPS only. Defaults to `true`, so set `false` only for local HTTP |
| `DATABASE_URL` | JDBC URL of the Postgres database |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Used by `compose.yaml` and by the app |

## Modules

```
app/            Entry point, ktor-svelte setup and the frontend
modules/
  core/         Environment, error handling and shared types
  database/     Connection pool, Flyway and the dbQuery helper
  accounts/     Register, login, sessions and users
  forum/        Categories, threads and posts
build-logic/    Convention plugins
```

Dependencies point one way: `app` depends on every module, `forum` depends on `accounts`, and `accounts` and `forum` depend on `database`, which depends on `core`. Features do not reach into each other's tables. `app` is the only place that knows every module, in `Application.module()`.

Each feature exposes one function that installs it (`accounts()`, `forum()`) and keeps everything else `internal`. Routes talk to an interface such as `ForumRepository`, and the Exposed class behind it is the only code that touches tables. That is why `ForumRoutesTest` runs without a database.

To add a module, create `modules/<name>/build.gradle.kts` with `id("forum.feature")` for a module that serves routes or `id("forum.library")` for one that does not. `settings.gradle.kts` finds it by itself. Then add `implementation(projects.<name>)` to the modules that use it and call its install function from `Application.module()`.

## Conventions

`build-logic` holds the convention plugins, so a module's build file only lists its dependencies.

| Plugin | For | Adds |
|---|---|---|
| `forum.kotlin-base` | Every module | Kotlin, serialization, ktlint, JUnit 5, JDK 21, warnings as errors |
| `forum.library` | Plain modules | `forum.kotlin-base` and `java-library`, so `api` and `implementation` differ |
| `forum.feature` | Modules with routes | `forum.library` and the ktor plugin, the Ktor dependencies and test host |
| `forum.application` | `app` | `forum.kotlin-base` and `application` |
| `forum.package-prefix` | Applied by the others | The IntelliJ package prefix for each module |

Every version lives in `gradle/libs.versions.toml`. Use `libs.` for a library, `libs.bundles.` for a group that always goes together, and `projects.` for another module. Never write a version in a build file.

The ktor plugin in `forum.feature` matters. The TypeScript client is generated from the routes, and the route inference only sees modules that apply the plugin. Without it every call in the module is typed `unknown`.

## Sign in

`modules/accounts/.../Auth.kt` has the register, login, logout and current user routes, with passwords hashed by PBKDF2. To require a signed-in user in any module, wrap routes in `authenticate(SESSION_AUTH)` and read `call.principal<UserSession>()`. Registration is open and login has no rate limit, so restrict both before going live. The pages in `app/frontend/src/routes/login` and `app/frontend/src/routes/account` use `app/frontend/src/lib/auth.ts`.

## Forum

| Route | Purpose |
|---|---|
| `GET /api/categories` | List categories |
| `GET /api/categories/{slug}/threads` | List the threads in a category, most recently active first. Takes `limit` and `cursor` |
| `GET /api/threads/{id}` | A thread with a page of its posts. Takes `limit` and `cursor` |
| `POST /api/categories/{slug}/threads` | Start a thread. Needs a session |
| `POST /api/threads/{id}/posts` | Reply to a thread. Needs a session |

Two categories, `general` and `help`, are created by the first migration. Reading is public and writing needs a session. The frontend does not have forum pages yet. The roadmap, with the phases and open decisions, is in [docs/forum-plan.md](docs/forum-plan.md).

## Database

`docker compose up -d` starts Postgres on this machine, using the settings in `.env`. `configureDatabase()` connects when the app starts and Flyway runs any new migration found in `src/main/resources/db/migration` of any module. Add a new file for each change and never edit one that has already run.

All modules share one Flyway history, so version numbers must be unique across them. Name files `V<number>__<module>_<change>.sql` and take the next free number. Today `V1` is accounts and `V2` is the forum. Check with `find modules -name 'V*.sql'`.

## Project layout

Kotlin files sit directly in `src/main/kotlin`, without package folders. `forum.package-prefix` sets the package for IntelliJ, `dev.nilsschlegel.forum` for `app` and `dev.nilsschlegel.forum.<module>` for the rest, so new files get the right package. Reload the Gradle project if IntelliJ does not pick it up.
