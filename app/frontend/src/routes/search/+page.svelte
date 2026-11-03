<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import { api, type SearchHit } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo } from '#lib/format.ts';
    import Snippet from '#lib/Snippet.svelte';
    import State from '#lib/State.svelte';

    const query = $derived(page.url.searchParams.get('q')?.trim() ?? '');

    let box = $state('');
    let hits = $state<SearchHit[]>([]);
    let next = $state<string | null>(null);
    let loading = $state(false);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);

    async function load(text: string) {
        error = null;
        hits = [];
        next = null;
        if (!text) return;
        loading = true;
        try {
            const first = await api.search.list({ q: text });
            hits = first.items;
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
            const more = await api.search.list({ q: query, cursor: next });
            hits = [...hits, ...more.items];
            next = more.next ?? null;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    function submit(event: SubmitEvent) {
        event.preventDefault();
        goto(link(`/search?q=${encodeURIComponent(box.trim())}`));
    }

    $effect(() => {
        box = query;
        load(query);
    });
</script>

<svelte:head><title>{query ? `${m.searchTitle()}: ${query}` : m.searchTitle()}</title></svelte:head>

<h1 class="mb-4 text-2xl font-bold">{m.searchTitle()}</h1>

<form class="mb-2 flex gap-2" role="search" onsubmit={submit}>
    <input
        class="input w-full"
        type="search"
        bind:value={box}
        placeholder={m.searchPlaceholder()}
        aria-label={m.searchTitle()}
        maxlength="200"
    />
    <button class="btn btn-primary" type="submit">{m.searchTitle()}</button>
</form>
<p class="mb-4 text-sm opacity-70">{m.searchHint()}</p>

{#if query}
    <State {loading} {error} empty={hits.length === 0} emptyText={m.searchEmpty()} onretry={() => load(query)}>
        <ul class="flex flex-col gap-3">
            {#each hits as hit (`${hit.threadId}-${hit.postId ?? 'thread'}`)}
                <li>
                    <a
                        class="card bg-base-100 shadow-sm transition hover:shadow-md"
                        href={link(`/t/${hit.threadId}${hit.postId ? `#post-${hit.postId}` : ''}`)}
                    >
                        <div class="card-body gap-1 p-4">
                            <p class="flex flex-wrap items-center gap-2 font-semibold">
                                <span class="badge badge-sm" class:badge-primary={hit.kind === 'thread'}>
                                    {hit.kind === 'thread' ? m.searchThreadHit() : m.searchPostHit()}
                                </span>
                                {hit.threadTitle}
                            </p>
                            <p><Snippet text={hit.snippet} /></p>
                            <p class="text-sm opacity-70">
                                {m.byAuthor({ author: hit.author })} · {m.searchIn({ category: hit.category })} · {formatAgo(
                                    hit.createdAt
                                )}
                            </p>
                        </div>
                    </a>
                </li>
            {/each}
        </ul>
        {#if next}
            <div class="mt-4 flex justify-center">
                <button class="btn" onclick={loadMore} disabled={loadingMore}>{m.loadMore()}</button>
            </div>
        {/if}
    </State>
{/if}
