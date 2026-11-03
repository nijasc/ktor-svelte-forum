# Changelog

## [0.2.0](https://github.com/nijasc/ktor-svelte-forum/compare/v0.1.0...v0.2.0) (2026-11-03)


### ⚠ BREAKING CHANGES

* **accounts:** POST /api/auth/register needs an email field, CurrentUser has email and emailVerified, and sessions from before this change are invalid, so everyone is signed out once. accounts() takes a MailSender.
* **forum:** the reply endpoint can answer 423, and thread lists have a separate pinned array.
* **forum:** GET /api/categories/{slug}/threads returns { items, next } instead of a list, and GET /api/threads/{id} returns its posts as { items, next } instead of a list.
* **accounts:** session cookies from earlier versions are invalid, so everyone is signed out once. CurrentUser now has id and role. The migrations fail if two existing usernames differ only by case.
* migrations are split per module. V1__create_tables is now V1__accounts_users and the sample visits table is removed, so an existing dev database fails Flyway validation. Reset it with `docker compose down -v`. The fat JAR is now built at app/build/libs/ktor-svelte-forum-all.jar.

### Features

* **accounts:** add role checks, account administration and profiles ([8699f79](https://github.com/nijasc/ktor-svelte-forum/commit/8699f7989a0525e0d09996004b98a0f4a0243983))
* **accounts:** key users by numeric id with role and status ([6f00789](https://github.com/nijasc/ktor-svelte-forum/commit/6f00789b9cc84862c4ff90e385609616785e6538))
* **accounts:** require and confirm an email address, and add password reset ([4dadfd9](https://github.com/nijasc/ktor-svelte-forum/commit/4dadfd9e8af5f388519d445749e08cf8349d3d0a))
* **app:** add email confirmation and password reset pages ([9dde024](https://github.com/nijasc/ktor-svelte-forum/commit/9dde024d89550b78a9f2fdd76cff78f311afb18b))
* **app:** add profiles and moderation controls to the thread pages ([065ced8](https://github.com/nijasc/ktor-svelte-forum/commit/065ced82af7bbd1e7666f31289398f1d2779a813))
* **app:** add reaction buttons to posts ([8683c4a](https://github.com/nijasc/ktor-svelte-forum/commit/8683c4a332f8c20fab29aa51943149cf95ea37a8))
* **app:** add search ([e636e13](https://github.com/nijasc/ktor-svelte-forum/commit/e636e1330f7e9fc495f19426821351ffa8378e10))
* **app:** add the category page and the new thread page ([90de81d](https://github.com/nijasc/ktor-svelte-forum/commit/90de81de3da01a565b0e86b7a318b623839dc44f))
* **app:** add the notification bell, list and watch button ([4cd54ba](https://github.com/nijasc/ktor-svelte-forum/commit/4cd54ba069771ae823756c4fe54cdea132b6e82a))
* **app:** add the site header, session store and category list ([2cb7519](https://github.com/nijasc/ktor-svelte-forum/commit/2cb75197233ad56d6164ab36ad1eca0d6a894e15))
* **app:** add the staff area ([e955b19](https://github.com/nijasc/ktor-svelte-forum/commit/e955b190095cfe955ba2d1c04d356f974bdc2e67))
* **app:** add the thread page with replies, edit and delete ([7f5424a](https://github.com/nijasc/ktor-svelte-forum/commit/7f5424a37d2d726444f5e0d07234be1314404085))
* **core:** rate limit sign-in and writing ([c3de6a1](https://github.com/nijasc/ktor-svelte-forum/commit/c3de6a1b8d1ca1642eea6c3e326a00184f84dbc3))
* **forum:** add pinned, locked and hidden content and a staff contract ([ebd8e38](https://github.com/nijasc/ktor-svelte-forum/commit/ebd8e3824cc1e21dbd64ef03a0864a5bad10ad1d))
* **forum:** add reactions on posts ([b15a9b6](https://github.com/nijasc/ktor-svelte-forum/commit/b15a9b6e57a560bf2252f488f1a289288b6800d2))
* **forum:** edit and delete your own posts ([5f33a32](https://github.com/nijasc/ktor-svelte-forum/commit/5f33a32a7031c4729c15eb3787ccbdd02d5eebe9))
* **forum:** page threads and posts and track thread activity ([28ff11f](https://github.com/nijasc/ktor-svelte-forum/commit/28ff11f208a96fd33d4a38651f74c17840b5762f))
* **mail:** add an event bus and a mail module ([0848866](https://github.com/nijasc/ktor-svelte-forum/commit/084886685ab5ba30c983c4ba1dadc2151070826c))
* **moderation:** add reports, bans, the audit log and staff routes ([f2936dd](https://github.com/nijasc/ktor-svelte-forum/commit/f2936dd9314146ce62f63ed3b090db2ec14faa8b))
* **notifications:** add watching threads, mentions and notifications ([7e3780d](https://github.com/nijasc/ktor-svelte-forum/commit/7e3780de7b6a1214e55109604d63d2ee0c89054f))
* **search:** add full-text search over threads and posts ([4bd1ebe](https://github.com/nijasc/ktor-svelte-forum/commit/4bd1ebedf8196570a6a3735d65c9eb0bec9b6eb1))
* split into app and feature modules and add the forum ([6eadba4](https://github.com/nijasc/ktor-svelte-forum/commit/6eadba4023cc5e8a5f78015c6375bcd8fdcaffad))


### Bug Fixes

* **core:** find .env above the working directory ([bb04972](https://github.com/nijasc/ktor-svelte-forum/commit/bb049723df23f9752f4a2901edd54143fe6bc79c))


### Documentation

* add the forum plan ([d97c553](https://github.com/nijasc/ktor-svelte-forum/commit/d97c553b7badd44c37bc37d402e3c3ef0337fafb))
* document modules, conventions, releases and the forum API ([0a80a64](https://github.com/nijasc/ktor-svelte-forum/commit/0a80a644f0585fa9ee14dc1888d33bcb7fc0ca88))
* mark phase 2 as done and record the moderation decisions ([71d4fb8](https://github.com/nijasc/ktor-svelte-forum/commit/71d4fb816a09eb0a52759be6c9dd78e9d20498a1))
* mark the forum pages as done in the plan ([2cba592](https://github.com/nijasc/ktor-svelte-forum/commit/2cba5929ddbc3f0608387c9f31a6bc4a42b0e445))
