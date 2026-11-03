<script lang="ts">
    import { api, type Post, type PostReactions } from '#api';
    import { m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';

    let { post, canReact, onchange }: { post: Post; canReact: boolean; onchange: (reactions: PostReactions) => void } =
        $props();

    let error = $state('');

    const kinds = [
        { kind: 'like', emoji: '👍', label: () => m.reactionLike() },
        { kind: 'love', emoji: '❤️', label: () => m.reactionLove() },
        { kind: 'laugh', emoji: '😄', label: () => m.reactionLaugh() },
        { kind: 'celebrate', emoji: '🎉', label: () => m.reactionCelebrate() }
    ] as const;

    /** Choosing the reaction you already chose takes it back. */
    async function choose(kind: (typeof kinds)[number]['kind']) {
        error = '';
        try {
            onchange(
                post.myReaction === kind
                    ? await api.posts.deleteReaction(post.id)
                    : await api.posts.putReaction(post.id, { kind })
            );
        } catch (failure) {
            error = errorMessage(failure);
        }
    }
</script>

<div class="flex flex-wrap items-center gap-1">
    {#each kinds as { kind, emoji, label } (kind)}
        {@const count = post.reactions?.[kind] ?? 0}
        {#if canReact}
            <button
                class="btn btn-xs"
                class:btn-primary={post.myReaction === kind}
                class:btn-ghost={post.myReaction !== kind}
                aria-pressed={post.myReaction === kind}
                aria-label={label()}
                title={label()}
                onclick={() => choose(kind)}
            >
                <span aria-hidden="true">{emoji}</span>
                {#if count > 0}<span>{count}</span>{/if}
            </button>
        {:else if count > 0}
            <span class="badge badge-ghost gap-1" title={label()}>
                <span aria-hidden="true">{emoji}</span>
                <span>{count}</span>
            </span>
        {/if}
    {/each}
    {#if error}<span class="text-error text-sm" role="alert">{error}</span>{/if}
</div>
