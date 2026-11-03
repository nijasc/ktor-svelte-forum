<script lang="ts">
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { loader } from '#lib/load.svelte.ts';
    import State from '#lib/State.svelte';

    const categories = loader(() => api.categories.list());
</script>

<svelte:head><title>{m.siteName()}</title></svelte:head>

<h1 class="mb-4 text-2xl font-bold">{m.categoriesTitle()}</h1>

<State
    loading={categories.loading}
    error={categories.error}
    empty={categories.data?.length === 0}
    emptyText={m.categoriesEmpty()}
    onretry={categories.reload}
>
    <ul class="flex flex-col gap-3">
        {#each categories.data ?? [] as category (category.slug)}
            <li>
                <a class="card bg-base-100 shadow-sm transition hover:shadow-md" href={link(`/c/${category.slug}`)}>
                    <div class="card-body p-4">
                        <h2 class="card-title">{category.title}</h2>
                        <p class="opacity-70">{category.description}</p>
                    </div>
                </a>
            </li>
        {/each}
    </ul>
</State>
