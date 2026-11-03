<script lang="ts">
    import { api, type AuditEntry } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatDateTime } from '#lib/format.ts';
    import State from '#lib/State.svelte';

    let entries = $state<AuditEntry[]>([]);
    let next = $state<string | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);

    async function load() {
        loading = true;
        error = null;
        try {
            const first = await api.staff.getAudit();
            entries = first.items;
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
            const more = await api.staff.getAudit({ cursor: next });
            entries = [...entries, ...more.items];
            next = more.next ?? null;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    load();
</script>

<State {loading} {error} empty={entries.length === 0} emptyText={m.auditEmpty()} onretry={load}>
    <ul class="flex flex-col gap-2">
        {#each entries as entry (entry.id)}
            <li class="card bg-base-100 shadow-sm">
                <div class="card-body gap-0 p-3 text-sm">
                    <p>
                        <a class="link font-semibold" href={link(`/u/${encodeURIComponent(entry.actor)}`)}
                            >{entry.actor}</a
                        >
                        <code class="badge badge-sm">{entry.action}</code>
                        {entry.targetType}
                        {entry.targetId}
                    </p>
                    {#if entry.detail}<p class="opacity-80">{entry.detail}</p>{/if}
                    <p class="opacity-60">{formatDateTime(entry.createdAt)}</p>
                </div>
            </li>
        {/each}
    </ul>
    {#if next}
        <div class="mt-4 flex justify-center">
            <button class="btn" onclick={loadMore} disabled={loadingMore}>{m.loadMore()}</button>
        </div>
    {/if}
</State>
