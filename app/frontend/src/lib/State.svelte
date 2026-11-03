<script lang="ts">
    import type { Snippet } from 'svelte';
    import { m } from '#i18n';

    let {
        loading,
        error,
        empty = false,
        emptyText = '',
        onretry,
        children
    }: {
        loading: boolean;
        error: string | null;
        empty?: boolean;
        emptyText?: string;
        onretry?: () => void;
        children: Snippet;
    } = $props();
</script>

{#if loading}
    <div class="flex justify-center p-8" role="status" aria-label={m.loading()}>
        <span class="loading loading-spinner"></span>
    </div>
{:else if error}
    <div role="alert" class="alert alert-error">
        <span>{error}</span>
        {#if onretry}<button class="btn btn-sm" onclick={onretry}>{m.retry()}</button>{/if}
    </div>
{:else if empty}
    <p class="p-8 text-center opacity-70">{emptyText}</p>
{:else}
    {@render children()}
{/if}
