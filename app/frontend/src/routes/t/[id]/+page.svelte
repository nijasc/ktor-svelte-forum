<script lang="ts">
    import { page } from '$app/state';
    import { api, type Category, type Post, type ThreadDetail } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo, formatDateTime, formatReplies } from '#lib/format.ts';
    import { MAX_BODY_LENGTH } from '#lib/limits.ts';
    import Markdown from '#lib/Markdown.svelte';
    import PostEditor from '#lib/PostEditor.svelte';
    import { isStaff } from '#lib/roles.ts';
    import { session } from '#lib/session.svelte.ts';
    import State from '#lib/State.svelte';
    import VerifyNotice from '#lib/VerifyNotice.svelte';

    const id = $derived(page.params.id ?? '');

    let thread = $state<ThreadDetail | null>(null);
    let category = $state<Category | null>(null);
    let posts = $state<Post[]>([]);
    let next = $state<string | null>(null);
    let firstPostId = $state<number | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);

    let reply = $state('');
    let replyError = $state('');
    let replying = $state(false);

    let editing = $state<number | null>(null);
    let draft = $state('');
    let editError = $state('');

    async function load(threadId: string) {
        loading = true;
        error = null;
        editing = null;
        try {
            const [detail, categories] = await Promise.all([api.threads.get(threadId), api.categories.list()]);
            thread = detail;
            category = categories.find((candidate) => candidate.slug === detail.category) ?? null;
            posts = detail.posts.items;
            next = detail.posts.next ?? null;
            firstPostId = detail.posts.items[0]?.id ?? null;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loading = false;
        }
    }

    async function loadMore() {
        if (!next) return;
        loadingMore = true;
        try {
            const more = await api.threads.get(id, { cursor: next });
            posts = [...posts, ...more.posts.items];
            next = more.posts.next ?? null;
            if (thread) thread.replyCount = more.replyCount;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    async function submitReply(event: SubmitEvent) {
        event.preventDefault();
        replying = true;
        replyError = '';
        try {
            const created = await api.threads.posts(id, { body: reply });
            reply = '';
            if (thread) thread.replyCount += 1;
            if (next) {
                // Posts are in order, so load up to the end instead of putting the new one after a gap.
                while (next) await loadMore();
            } else {
                posts = [...posts, created];
            }
        } catch (failure) {
            replyError = errorMessage(failure);
        } finally {
            replying = false;
        }
    }

    function startEdit(post: Post) {
        editing = post.id;
        draft = post.body;
        editError = '';
    }

    async function saveEdit(event: SubmitEvent) {
        event.preventDefault();
        if (editing === null) return;
        try {
            const saved = await api.posts.patch(editing, { body: draft });
            posts = posts.map((post) => (post.id === saved.id ? saved : post));
            editing = null;
        } catch (failure) {
            editError = errorMessage(failure);
        }
    }

    async function remove(post: Post) {
        if (!confirm(m.confirmDelete())) return;
        try {
            await api.posts.delete(post.id);
            posts = posts.map((candidate) =>
                candidate.id === post.id ? { ...candidate, body: '', deleted: true } : candidate
            );
            if (thread) thread.replyCount -= 1;
        } catch (failure) {
            error = errorMessage(failure);
        }
    }

    let actionError = $state('');
    let notice = $state('');
    let watching = $state(false);

    async function loadWatch(threadId: string) {
        watching = session.user ? (await api.threads.getWatch(threadId).catch(() => null))?.watching === true : false;
    }

    async function toggleWatch() {
        actionError = '';
        try {
            if (watching) await api.threads.deleteWatch(id);
            else await api.threads.putWatch(id);
            watching = !watching;
        } catch (failure) {
            actionError = errorMessage(failure);
        }
    }

    /** Runs a moderation or reporting action, then shows the result. [reload] fetches the thread again afterwards. */
    async function act(action: () => Promise<unknown>, reload = true) {
        actionError = '';
        notice = '';
        try {
            await action();
            if (reload) await load(id);
        } catch (failure) {
            actionError = errorMessage(failure);
        }
    }

    /** Asks for a reason, and does nothing if none is given. */
    function withReason(action: (reason: string) => Promise<unknown>) {
        const reason = prompt(m.reasonPrompt())?.trim();
        if (reason) act(() => action(reason));
    }

    function report(post: Post) {
        const reason = prompt(m.reportPrompt())?.trim();
        if (!reason) return;
        act(async () => {
            await api.posts.reports(post.id, { reason });
            notice = m.reportSent();
        }, false);
    }

    function move() {
        const category = prompt(m.movePrompt())?.trim();
        if (category && thread) act(() => api.staff.threadsMove(thread!.id, { category }));
    }

    function isAuthorLink(post: Post): boolean {
        return post.author !== 'deleted';
    }

    /** Usernames are unique ignoring case, so this tells whether a post is the reader's own. */
    function isMine(post: Post): boolean {
        return session.user?.username.toLowerCase() === post.author.toLowerCase();
    }

    $effect(() => {
        load(id);
    });

    $effect(() => {
        if (session.ready) loadWatch(id);
    });
</script>

<svelte:head><title>{thread?.title ?? m.loading()}</title></svelte:head>

<State {loading} {error} onretry={() => load(id)}>
    {#if thread}
        <nav class="mb-2 text-sm">
            <a class="link" href={link(`/c/${thread.category}`)}>
                {m.backToCategory({ category: category?.title ?? thread.category })}
            </a>
        </nav>
        <h1 class="flex flex-wrap items-center gap-2 text-2xl font-bold break-words">
            {thread.title}
            {#if thread.pinned}<span class="badge badge-primary">{m.threadPinned()}</span>{/if}
            {#if thread.locked}<span class="badge">{m.threadLockedBadge()}</span>{/if}
        </h1>
        <p class="mb-4 text-sm opacity-70">
            {m.byAuthor({ author: thread.author })} · {formatReplies(thread.replyCount)}
        </p>

        {#if session.user}
            <button class="btn btn-sm mb-4" class:btn-primary={watching} onclick={toggleWatch} aria-pressed={watching}>
                {watching ? m.watching() : m.watch()}
            </button>
        {/if}

        {#if isStaff(session.user)}
            <div class="mb-4 flex flex-wrap gap-2">
                {#if thread.pinned}
                    <button class="btn btn-sm" onclick={() => act(() => api.staff.threadsUnpin(thread!.id))}>
                        {m.unpin()}
                    </button>
                {:else}
                    <button class="btn btn-sm" onclick={() => act(() => api.staff.threadsPin(thread!.id))}>
                        {m.pin()}
                    </button>
                {/if}
                {#if thread.locked}
                    <button class="btn btn-sm" onclick={() => act(() => api.staff.threadsUnlock(thread!.id))}>
                        {m.unlock()}
                    </button>
                {:else}
                    <button class="btn btn-sm" onclick={() => act(() => api.staff.threadsLock(thread!.id))}>
                        {m.lock()}
                    </button>
                {/if}
                <button class="btn btn-sm" onclick={move}>{m.move()}</button>
                <button
                    class="btn btn-error btn-sm"
                    onclick={() => withReason((reason) => api.staff.threadsHide(thread!.id, { reason }))}
                >
                    {m.threadHide()}
                </button>
            </div>
        {/if}
        {#if actionError}<p class="text-error mb-2" role="alert">{actionError}</p>{/if}
        {#if notice}<p class="mb-2" role="status">{notice}</p>{/if}

        <ol class="flex flex-col gap-3">
            {#each posts as post (post.id)}
                <li id={`post-${post.id}`} class="card bg-base-100 shadow-sm">
                    <div class="card-body gap-2 p-4">
                        <div class="flex flex-wrap items-center justify-between gap-2 text-sm">
                            {#if isAuthorLink(post)}
                                <a class="font-semibold" href={link(`/u/${encodeURIComponent(post.author)}`)}>
                                    {post.author}
                                </a>
                            {:else}
                                <span class="font-semibold">{post.author}</span>
                            {/if}
                            <span class="opacity-70">
                                <time datetime={post.createdAt} title={formatDateTime(post.createdAt)}>
                                    {formatAgo(post.createdAt)}
                                </time>
                                {#if post.editedAt}
                                    ·
                                    <span title={formatDateTime(post.editedAt)}>{m.edited()}</span>
                                {/if}
                            </span>
                        </div>
                        {#if post.deleted}
                            <p class="italic opacity-60">{m.postDeleted()}</p>
                        {:else if post.hidden}
                            <p class="italic opacity-60">{m.hiddenPost()}</p>
                            {#if isStaff(session.user)}
                                <div>
                                    <button
                                        class="btn btn-ghost btn-xs"
                                        onclick={() => act(() => api.staff.postsRestore(post.id))}
                                    >
                                        {m.restore()}
                                    </button>
                                </div>
                            {/if}
                        {:else if editing === post.id}
                            <form class="flex flex-col gap-2" onsubmit={saveEdit}>
                                <PostEditor
                                    id={`edit-${post.id}`}
                                    label={m.edit()}
                                    bind:value={draft}
                                    maxLength={MAX_BODY_LENGTH}
                                />
                                {#if editError}<p class="text-error" role="alert">{editError}</p>{/if}
                                <div class="flex gap-2">
                                    <button class="btn btn-primary btn-sm" type="submit" disabled={!draft.trim()}>
                                        {m.save()}
                                    </button>
                                    <button class="btn btn-sm" type="button" onclick={() => (editing = null)}>
                                        {m.cancel()}
                                    </button>
                                </div>
                            </form>
                        {:else}
                            <Markdown source={post.body} />
                            {#if isMine(post)}
                                <div class="flex gap-2">
                                    <button class="btn btn-ghost btn-xs" onclick={() => startEdit(post)}
                                        >{m.edit()}</button
                                    >
                                    {#if post.id !== firstPostId}
                                        <button class="btn btn-ghost btn-xs" onclick={() => remove(post)}
                                            >{m.delete()}</button
                                        >
                                    {/if}
                                </div>
                            {:else if session.user}
                                <div class="flex gap-2">
                                    <button class="btn btn-ghost btn-xs" onclick={() => report(post)}
                                        >{m.report()}</button
                                    >
                                    {#if isStaff(session.user) && post.id !== firstPostId}
                                        <button
                                            class="btn btn-ghost btn-xs"
                                            onclick={() =>
                                                withReason((reason) => api.staff.postsHide(post.id, { reason }))}
                                        >
                                            {m.hide()}
                                        </button>
                                    {/if}
                                </div>
                            {/if}
                        {/if}
                    </div>
                </li>
            {/each}
        </ol>

        {#if next}
            <div class="mt-4 flex justify-center">
                <button class="btn" onclick={loadMore} disabled={loadingMore}>{m.loadMore()}</button>
            </div>
        {/if}

        <section class="mt-8">
            {#if thread.locked}
                <p class="alert">{m.threadLocked()}</p>
            {:else if session.user && !session.user.emailVerified}
                <VerifyNotice />
            {:else if session.user}
                <form class="flex flex-col gap-3" onsubmit={submitReply}>
                    <PostEditor id="reply" label={m.replyTitle()} bind:value={reply} maxLength={MAX_BODY_LENGTH} />
                    {#if replyError}<p class="text-error" role="alert">{replyError}</p>{/if}
                    <div>
                        <button class="btn btn-primary" type="submit" disabled={replying || !reply.trim()}>
                            {m.replySubmit()}
                        </button>
                    </div>
                </form>
            {:else if session.ready}
                <a class="link" href={link(`/login?next=${encodeURIComponent(`/t/${thread.id}`)}`)}>
                    {m.signInToReply()}
                </a>
            {/if}
        </section>
    {/if}
</State>
