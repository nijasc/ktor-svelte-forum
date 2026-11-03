<script lang="ts">
    import { page } from '$app/state';
    import { api, type Category, type ThreadSummary } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { session } from '#lib/session.svelte.ts';
    import State from '#lib/State.svelte';
    import ThreadRow from '#lib/ThreadRow.svelte';

    const slug = $derived(page.params.slug ?? '');

    let category = $state<Category | null>(null);
    let threads = $state<ThreadSummary[]>([]);
    let pinned = $state<ThreadSummary[]>([]);
    let next = $state<string | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);

    async function load(forSlug: string) {
        loading = true;
        error = null;
        threads = [];
        pinned = [];
        next = null;
        try {
            const [categories, first] = await Promise.all([api.categories.list(), api.categories.getThreads(forSlug)]);
            category = categories.find((candidate) => candidate.slug === forSlug) ?? null;
            threads = first.items;
            pinned = first.pinned ?? [];
            next = first.next ?? null;
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
            const more = await api.categories.getThreads(slug, { cursor: next });
            threads = [...threads, ...more.items];
            next = more.next ?? null;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    $effect(() => {
        load(slug);
    });
</script>

<svelte:head><title>{category?.title ?? slug}</title></svelte:head>

<nav class="mb-2 text-sm"><a class="link" href={link('/')}>{m.navAllCategories()}</a></nav>

<div class="mb-4 flex items-start justify-between gap-4">
    <div>
        <h1 class="text-2xl font-bold">{category?.title ?? slug}</h1>
        {#if category}<p class="opacity-70">{category.description}</p>{/if}
    </div>
    {#if session.user}
        <a class="btn btn-primary btn-sm" href={link(`/c/${slug}/new`)}>{m.threadNew()}</a>
    {:else if session.ready}
        <a class="link text-sm" href={link(`/login?next=${encodeURIComponent(`/c/${slug}/new`)}`)}>
            {m.signInToStart()}
        </a>
    {/if}
</div>

<State
    {loading}
    {error}
    empty={threads.length === 0 && pinned.length === 0}
    emptyText={m.threadsEmpty()}
    onretry={() => load(slug)}
>
    <ul class="flex flex-col gap-3">
        {#each [...pinned, ...threads] as thread (thread.id)}
            <li><ThreadRow {thread} /></li>
        {/each}
    </ul>
    {#if next}
        <div class="mt-4 flex justify-center">
            <button class="btn" onclick={loadMore} disabled={loadingMore}>{m.loadMore()}</button>
        </div>
    {/if}
</State>
