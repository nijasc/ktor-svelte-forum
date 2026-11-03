<script lang="ts">
    import { api, type StaffCategory } from '#api';
    import { m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import State from '#lib/State.svelte';

    let categories = $state<StaffCategory[]>([]);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let actionError = $state('');

    let slug = $state('');
    let title = $state('');
    let description = $state('');

    async function load() {
        loading = true;
        error = null;
        try {
            categories = await api.staff.getCategories();
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loading = false;
        }
    }

    async function act(action: () => Promise<unknown>) {
        actionError = '';
        try {
            await action();
            categories = await api.staff.getCategories();
        } catch (failure) {
            actionError = errorMessage(failure);
        }
    }

    function create(event: SubmitEvent) {
        event.preventDefault();
        act(async () => {
            await api.staff.categories({ slug, title, description });
            slug = title = description = '';
        });
    }

    function rename(category: StaffCategory) {
        const next = prompt(m.categoryTitle(), category.title)?.trim();
        if (next && next !== category.title) act(() => api.staff.patchCategories(category.slug, { title: next }));
    }

    function shift(index: number, by: -1 | 1) {
        const slugs = categories.map((category) => category.slug);
        const target = index + by;
        if (target < 0 || target >= slugs.length) return;
        [slugs[index], slugs[target]] = [slugs[target], slugs[index]];
        act(() => api.staff.putCategoriesOrder({ slugs }));
    }

    load();
</script>

{#if actionError}<p class="text-error mb-2" role="alert">{actionError}</p>{/if}

<State {loading} {error} onretry={load}>
    <ul class="mb-8 flex flex-col gap-2">
        {#each categories as category, index (category.slug)}
            <li class="card bg-base-100 shadow-sm" data-category={category.slug}>
                <div class="card-body flex-row flex-wrap items-center justify-between gap-2 p-3">
                    <div>
                        <p class="font-semibold">
                            {category.title}
                            {#if category.archived}<span class="badge badge-sm">{m.archived()}</span>{/if}
                        </p>
                        <p class="text-sm opacity-70">/{category.slug} · {category.description}</p>
                    </div>
                    <div class="flex gap-1">
                        <button class="btn btn-ghost btn-xs" onclick={() => shift(index, -1)} disabled={index === 0}>
                            {m.moveUp()}
                        </button>
                        <button
                            class="btn btn-ghost btn-xs"
                            onclick={() => shift(index, 1)}
                            disabled={index === categories.length - 1}
                        >
                            {m.moveDown()}
                        </button>
                        <button class="btn btn-ghost btn-xs" onclick={() => rename(category)}>{m.edit()}</button>
                        <button
                            class="btn btn-ghost btn-xs"
                            onclick={() =>
                                act(() => api.staff.patchCategories(category.slug, { archived: !category.archived }))}
                        >
                            {category.archived ? m.unarchive() : m.archive()}
                        </button>
                    </div>
                </div>
            </li>
        {/each}
    </ul>

    <form class="flex flex-col gap-2" onsubmit={create}>
        <h2 class="text-lg font-semibold">{m.categoryNew()}</h2>
        <input class="input w-full" bind:value={slug} placeholder={m.categorySlug()} required />
        <input class="input w-full" bind:value={title} placeholder={m.categoryTitle()} required />
        <input class="input w-full" bind:value={description} placeholder={m.categoryDescription()} />
        <div><button class="btn btn-primary" type="submit">{m.categoryCreate()}</button></div>
    </form>
</State>
