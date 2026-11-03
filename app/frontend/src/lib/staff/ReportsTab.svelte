<script lang="ts">
    import { api, type ReportView } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo } from '#lib/format.ts';
    import Markdown from '#lib/Markdown.svelte';
    import State from '#lib/State.svelte';

    let reports = $state<ReportView[]>([]);
    let next = $state<string | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);
    let actionError = $state('');

    async function load() {
        loading = true;
        error = null;
        try {
            const first = await api.staff.getReports();
            reports = first.items;
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
            const more = await api.staff.getReports({ cursor: next });
            reports = [...reports, ...more.items];
            next = more.next ?? null;
        } catch (failure) {
            actionError = errorMessage(failure);
        } finally {
            loadingMore = false;
        }
    }

    async function act(action: () => Promise<unknown>) {
        actionError = '';
        try {
            await action();
        } catch (failure) {
            actionError = errorMessage(failure);
            return false;
        }
        return true;
    }

    async function resolve(report: ReportView, outcome: 'dismissed' | 'actioned') {
        const note = prompt(m.notePrompt());
        if (note === null) return;
        if (await act(() => api.staff.reportsResolve(report.id, { outcome, note: note.trim() }))) {
            reports = reports.filter((candidate) => candidate.id !== report.id);
        }
    }

    function hidePost(report: ReportView) {
        const reason = prompt(m.reasonPrompt())?.trim();
        if (!reason) return;
        act(() => api.staff.postsHide(report.post.id, { reason })).then((done) => {
            if (done)
                reports = reports.map((r) => (r.id === report.id ? { ...r, post: { ...r.post, hidden: true } } : r));
        });
    }

    load();
</script>

{#if actionError}<p class="text-error mb-2" role="alert">{actionError}</p>{/if}

<State {loading} {error} empty={reports.length === 0} emptyText={m.reportsEmpty()} onretry={load}>
    <ul class="flex flex-col gap-3">
        {#each reports as report (report.id)}
            <li class="card bg-base-100 shadow-sm" data-report={report.id}>
                <div class="card-body gap-2 p-4">
                    <p class="text-sm opacity-70">
                        <a class="link" href={link(`/t/${report.post.threadId}#post-${report.post.id}`)}>
                            {report.post.threadTitle}
                        </a>
                        ·
                        <a class="link" href={link(`/u/${encodeURIComponent(report.post.author)}`)}
                            >{report.post.author}</a
                        >
                    </p>
                    {#if report.post.hidden}<p class="text-sm italic opacity-60">{m.hiddenPost()}</p>{/if}
                    <Markdown source={report.post.body} />
                    <p class="text-sm">
                        <strong>{m.reportedBy({ reporter: report.reporter })}:</strong>
                        {report.reason}
                        <span class="opacity-70">· {formatAgo(report.createdAt)}</span>
                    </p>
                    <div class="flex flex-wrap gap-2">
                        {#if !report.post.hidden}
                            <button class="btn btn-error btn-sm" onclick={() => hidePost(report)}>{m.hide()}</button>
                        {/if}
                        <button class="btn btn-sm" onclick={() => resolve(report, 'dismissed')}
                            >{m.resolveDismiss()}</button
                        >
                        <button class="btn btn-primary btn-sm" onclick={() => resolve(report, 'actioned')}>
                            {m.resolveHandled()}
                        </button>
                    </div>
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
