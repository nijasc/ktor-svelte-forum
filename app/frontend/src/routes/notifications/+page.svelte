<script lang="ts">
    import { goto } from '$app/navigation';
    import { api, type NotificationView } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo } from '#lib/format.ts';
    import { refreshUnread, session } from '#lib/session.svelte.ts';
    import State from '#lib/State.svelte';

    let items = $state<NotificationView[]>([]);
    let next = $state<string | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);

    async function load() {
        loading = true;
        error = null;
        try {
            const first = await api.notifications.list();
            items = first.items;
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
            const more = await api.notifications.list({ cursor: next });
            items = [...items, ...more.items];
            next = more.next ?? null;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    async function markAllRead() {
        await api.notifications.read();
        items = items.map((item) => ({ ...item, read: true }));
        await refreshUnread();
    }

    /** Opening a notification marks it read. The page it opens does not wait for that. */
    function open(item: NotificationView) {
        if (!item.read) api.notifications.readId(item.id).then(refreshUnread, () => {});
    }

    function text(item: NotificationView): string {
        if (item.kind === 'mention') return m.notifMention({ actor: item.actor, thread: item.threadTitle });
        return item.count > 1
            ? m.notifReplyMany({ count: item.count, thread: item.threadTitle })
            : m.notifReply({ actor: item.actor, thread: item.threadTitle });
    }

    $effect(() => {
        if (session.ready && !session.user) goto(link('/login?next=/notifications'));
        else if (session.user) load();
    });
</script>

<svelte:head><title>{m.notificationsTitle()}</title></svelte:head>

<div class="mb-4 flex items-center justify-between">
    <h1 class="text-2xl font-bold">{m.notificationsTitle()}</h1>
    {#if items.some((item) => !item.read)}
        <button class="btn btn-sm" onclick={markAllRead}>{m.markAllRead()}</button>
    {/if}
</div>

<State {loading} {error} empty={items.length === 0} emptyText={m.notificationsEmpty()} onretry={load}>
    <ul class="flex flex-col gap-2">
        {#each items as item (item.id)}
            <li>
                <a
                    class="card bg-base-100 shadow-sm transition hover:shadow-md"
                    class:opacity-60={item.read}
                    href={link(`/t/${item.threadId}${item.postId ? `#post-${item.postId}` : ''}`)}
                    onclick={() => open(item)}
                >
                    <div class="card-body gap-0 p-3">
                        <p class:font-semibold={!item.read}>{text(item)}</p>
                        <p class="text-sm opacity-70">{formatAgo(item.createdAt)}</p>
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
