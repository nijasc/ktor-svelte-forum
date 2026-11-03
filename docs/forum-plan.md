# Forum plan

Where the forum is, what it should become, and the order to get there. Update this file when a decision changes, and tick items off as they ship.

## Where we are

- Accounts: register, login, logout, current user. Users are keyed by `username`, with a PBKDF2 hash and nothing else.
- Forum API: list categories, list threads in a category, read a thread, start a thread, reply. Reading is public, writing needs a session.
- Storage: `categories`, `threads`, `posts` in Postgres. Two categories are seeded by `V2`.
- Frontend: sign in and account pages only. No forum pages.

## Gaps in what exists

These are cheap to fix now and expensive once there is real data, so they come first.

| Gap | Why it matters |
|---|---|
| `users` has `username` as the primary key, and `threads.author` and `posts.author` reference it | Renaming or deleting a user means rewriting every post. Needs a `BIGINT` id |
| No role, email, `created_at` or status on users | Moderation, password reset and bans have nothing to hang on |
| Session stores only the username | Role changes and bans do not take effect until the cookie expires. Store the user id and load the user per request |
| Thread lists and posts are unpaginated | One busy thread returns everything |
| No edit or delete, no reply count, no last activity | A thread list cannot be sorted by activity |
| Registration is open and login has no rate limit | Spam and credential stuffing |
| Post bodies are raw text with no rendering rules | Decide on Markdown and sanitising before any page renders them |
| Repository tests only run against a fake | `ExposedForumRepository` and the SQL are only checked by hand |

## Goals and non-goals

Goals: a small, fast, readable forum for one community, with categories, threads, replies, basic moderation and notifications, served from one JAR.

Non-goals for now: federation, private messages, image uploads, a plugin system, multiple forums per install, native apps.

## Domain model

```
User ──< Thread >── Category
  │         │
  └───────< Post
```

| Concept | Notes |
|---|---|
| User | `id`, `username` (unique, case-insensitive), `email`, `role`, `status`, `created_at` |
| Role | `member`, `moderator`, `admin`. A column, not a table, until we need custom roles |
| Category | `slug`, `title`, `description`, `position`. Managed by admins. Later: read and write role per category |
| Thread | Belongs to one category. `pinned`, `locked`, `deleted_at`, `last_post_at`, `reply_count` |
| Post | Belongs to one thread. The first post is the thread body. `edited_at`, `deleted_at` |
| Reaction | One per user and post, from a fixed set. Later |
| Report | A user flags a post. A moderator resolves it. Later |
| Notification | Reply to my thread, mention, moderator action. Later |

Deletes are soft (`deleted_at`) so moderators can undo them and replies keep their context. A user who deletes their account is anonymised, not removed, so their posts stay.

## Phases

Each phase ends in something usable. Each item is one or a few commits, using the scope of the module it touches.

### Phase 0: fix the foundations

Do this before building pages, while there is no data to migrate.

- [x] Users get a numeric id, email, role, status and `created_at` (`accounts`). Email is stored but not collected until Phase 3
- [x] Posts and threads reference `user_id`. Responses still return the username (`forum`)
- [x] Session holds the user id, and the user is loaded per request (`accounts`)
- [x] Keyset pagination on threads and posts: `?cursor=&limit=`, returning `{ items, next }` (`forum`)
- [x] `reply_count` and `last_post_at` on threads, newest activity first (`forum`)
- [x] Rate limiting on register, login and posting (`core`). Per client address for sign-in, per user for writing. Behind a reverse proxy it needs forwarded headers first, see Phase 4
- [x] Repository tests against real Postgres with Testcontainers (`database`), including a migration test over old rows

### Phase 1: a forum you can use

- [ ] Pages: category list, thread list, thread with replies, new thread, reply box (`app`)
- [ ] Markdown in posts, rendered safely. Decide where it is sanitised (see open decisions)
- [ ] Edit and delete your own posts, with an "edited" mark
- [ ] Thread titles and bodies validated the same way on client and server
- [ ] German and English strings for every page (`app/frontend/messages`)
- [ ] Empty, loading and error states for each page

### Phase 2: community and moderation

- [ ] Public profile page: username, join date, recent threads and posts
- [ ] Roles enforced in routes. Moderators can pin, lock, move, hide and delete
- [ ] Reports: flag a post, moderator queue, resolve with a note
- [ ] Ban and suspend a user. Banned users cannot write, and their session stops working
- [ ] Audit log of moderator actions (`moderation` module)
- [ ] Admin: create, reorder and archive categories

### Phase 3: keeping people coming back

- [ ] Email verification and password reset (needs a mail sender, see open decisions)
- [ ] Notifications for replies and mentions, with an unread count (`notifications` module)
- [ ] Watch a thread
- [ ] Reactions on posts
- [ ] Search across threads and posts with Postgres full-text search (`search` module)

### Phase 4: running it for real

- [ ] Health and readiness endpoints, structured logs
- [ ] Backups of the database, and a documented restore
- [ ] Config for a reverse proxy: forwarded headers, secure cookies, CORS stance
- [ ] Spam controls: new-account limits, link limits, optional approval queue
- [ ] Load check on the thread list and the search query

## Architecture

Keep the dependency rule from the README: features depend on `database` and `core`, never on each other's tables. When a feature needs another's data, it asks through a small public interface.

New modules, added only when the phase needs them:

| Module | Phase | Owns |
|---|---|---|
| `moderation` | 2 | Reports, bans, audit log. Depends on `accounts` and `forum` through interfaces |
| `notifications` | 3 | Notification table and delivery. Listens to events from `forum` |
| `search` | 3 | The search query and its indexes |
| `mail` | 3 | Sending email behind an interface, with a log-only implementation for development |

`forum` stays one module until it is hard to hold in your head. If moderation actions keep pulling `forum` code around, that is the sign to split.

To avoid `forum` calling `notifications` directly, `forum` emits domain events (`ThreadCreated`, `PostCreated`) on a small in-process bus in `core`. Modules subscribe in their install function. This stays in-process on purpose. Add a queue only if delivery has to survive a restart.

Migrations follow the README rule: shared version numbers, one file per change. Planned: `V3` users rework, `V4` posts and threads by user id, `V5` thread counters, then one per feature.

## API conventions

- JSON in and out, errors as `{ "message": ..., "fields": ... }`, as today
- Lists return `{ "items": [...], "next": "<cursor>" | null }`, so adding a field later does not break clients
- 401 for no session, 403 for a session without permission, 404 for missing or hidden, 422 for validation
- Ids in URLs are numeric. Categories also have a slug
- Run `./gradlew svelteGenerateTs` after any route change, and commit the generated client

## Security checklist

- Posts are untrusted input. Sanitise on the server and escape on the client; never insert unsanitised HTML
- Cookies: `HttpOnly`, `SameSite=Lax`, `Secure` outside local dev. Add a CSRF token if any state change ever uses `GET` or a cross-site form
- Passwords: keep PBKDF2 parameters in one place, store the iteration count with the hash, and rehash on login when it is raised
- Do not reveal whether a username exists on login or on password reset
- Every moderator action is written to the audit log
- Rate limits keyed by account and by IP

## Testing

- Route tests with a fake repository (as in `ForumRoutesTest`) for status codes and permissions
- Repository tests against Postgres in a container for SQL, constraints and pagination
- A few end-to-end checks of the register, post and reply path, run in CI
- Frontend: `svelteCheck` and lint in CI today. Add component tests for the post editor when it exists

## Open decisions

| Question | Options | Leaning |
|---|---|---|
| Markdown rendering | Render on the server and store HTML, or store Markdown and render in the browser | Store Markdown, render in the browser with a sanitiser. Keeps the source editable |
| Ids in URLs | Numeric, or numeric plus a slug (`/t/42-title`) | Numeric plus slug for readable links |
| Email sending | SMTP, or a provider such as Resend | Provider behind the `mail` interface |
| Who can register | Open, invite only, or approval | Open with rate limits, with a setting to close it |
| Account deletion | Hard delete, or anonymise | Anonymise |
| Realtime updates | Polling, or server-sent events | Polling first. Revisit after Phase 3 |

## Next steps

Start Phase 0 with the users rework. It touches `accounts`, then `forum`, and every later item builds on it.
