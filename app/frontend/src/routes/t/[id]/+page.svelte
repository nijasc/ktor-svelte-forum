<script lang="ts">
    import { page } from '$app/state';
    import { api, type Category, type Post, type ThreadDetail } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo, formatDateTime, formatReplies } from '#lib/format.ts';
    import { MAX_BODY_LENGTH } from '#lib/limits.ts';
    import Markdown from '#lib/Markdown.svelte';
    import PostEditor from '#lib/PostEditor.svelte';
    import { session } from '#lib/session.svelte.ts';
    import State from '#lib/State.svelte';

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

    /** Usernames are unique ignoring case, so this tells whether a post is the reader's own. */
    function isMine(post: Post): boolean {
        return session.user?.username.toLowerCase() === post.author.toLowerCase();
    }

    $effect(() => {
        load(id);
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
        <h1 class="text-2xl font-bold break-words">{thread.title}</h1>
        <p class="mb-4 text-sm opacity-70">
            {m.byAuthor({ author: thread.author })} · {formatReplies(thread.replyCount)}
        </p>

        <ol class="flex flex-col gap-3">
            {#each posts as post (post.id)}
                <li id={`post-${post.id}`} class="card bg-base-100 shadow-sm">
                    <div class="card-body gap-2 p-4">
                        <div class="flex flex-wrap items-center justify-between gap-2 text-sm">
                            <span class="font-semibold">{post.author}</span>
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
            {#if session.user}
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
