<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { MAX_BODY_LENGTH, MAX_TITLE_LENGTH } from '#lib/limits.ts';
    import PostEditor from '#lib/PostEditor.svelte';
    import { session } from '#lib/session.svelte.ts';
    import VerifyNotice from '#lib/VerifyNotice.svelte';

    const slug = $derived(page.params.slug ?? '');

    let title = $state('');
    let body = $state('');
    let error = $state('');
    let busy = $state(false);

    $effect(() => {
        if (session.ready && !session.user) goto(link(`/login?next=${encodeURIComponent(page.url.pathname)}`));
    });

    async function submit(event: SubmitEvent) {
        event.preventDefault();
        busy = true;
        error = '';
        try {
            const thread = await api.categories.threads(slug, { title, body });
            await goto(link(`/t/${thread.id}`));
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            busy = false;
        }
    }
</script>

<svelte:head><title>{m.threadNew()}</title></svelte:head>

<nav class="mb-2 text-sm"><a class="link" href={link(`/c/${slug}`)}>{m.backToCategory({ category: slug })}</a></nav>
<h1 class="mb-4 text-2xl font-bold">{m.threadNew()}</h1>

{#if session.user && !session.user.emailVerified}
    <VerifyNotice />
{:else}
    <form class="flex flex-col gap-4" onsubmit={submit}>
        <label class="flex flex-col gap-2">
            <span class="font-medium">{m.threadTitleLabel()}</span>
            <input class="input w-full" bind:value={title} maxlength={MAX_TITLE_LENGTH} required />
        </label>
        <PostEditor
            id="thread-body"
            label={m.threadBodyLabel()}
            bind:value={body}
            maxLength={MAX_BODY_LENGTH}
            rows={10}
        />
        {#if error}<p class="text-error" role="alert">{error}</p>{/if}
        <div>
            <button class="btn btn-primary" type="submit" disabled={busy || !title.trim() || !body.trim()}>
                {m.threadCreate()}
            </button>
        </div>
    </form>
{/if}
