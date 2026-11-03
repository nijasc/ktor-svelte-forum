<script lang="ts">
    import type { ThreadSummary } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { formatAgo, formatReplies } from '#lib/format.ts';

    let { thread }: { thread: ThreadSummary } = $props();
</script>

<a class="card bg-base-100 shadow-sm transition hover:shadow-md" href={link(`/t/${thread.id}`)}>
    <div class="card-body gap-1 p-4">
        <h2 class="card-title text-lg">
            {thread.title}
            {#if thread.pinned}<span class="badge badge-primary badge-sm">{m.threadPinned()}</span>{/if}
            {#if thread.locked}<span class="badge badge-sm">{m.threadLockedBadge()}</span>{/if}
        </h2>
        <p class="text-sm opacity-70">
            {m.byAuthor({ author: thread.author })} · {formatReplies(thread.replyCount)} ·
            {m.lastActivity({ time: formatAgo(thread.lastPostAt) })}
        </p>
    </div>
</a>
