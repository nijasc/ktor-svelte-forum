<script lang="ts">
    import { m } from '#i18n';
    import Markdown from '#lib/Markdown.svelte';

    let {
        value = $bindable(''),
        maxLength,
        label,
        id,
        rows = 6
    }: { value: string; maxLength: number; label: string; id: string; rows?: number } = $props();

    let preview = $state(false);
</script>

<div class="flex flex-col gap-2">
    <div class="flex items-center justify-between">
        <label class="font-medium" for={id}>{label}</label>
        <div role="tablist" class="tabs tabs-box tabs-sm">
            <button type="button" role="tab" class="tab" class:tab-active={!preview} onclick={() => (preview = false)}>
                {m.write()}
            </button>
            <button type="button" role="tab" class="tab" class:tab-active={preview} onclick={() => (preview = true)}>
                {m.preview()}
            </button>
        </div>
    </div>
    {#if preview}
        <div class="border-base-300 min-h-32 rounded-lg border p-3">
            {#if value.trim()}
                <Markdown source={value} />
            {:else}
                <p class="opacity-60">{m.nothingToPreview()}</p>
            {/if}
        </div>
    {:else}
        <textarea {id} class="textarea w-full" {rows} maxlength={maxLength} bind:value></textarea>
    {/if}
    <p class="text-sm opacity-70">{m.markdownHint()} · {m.charactersLeft({ count: maxLength - value.length })}</p>
</div>
