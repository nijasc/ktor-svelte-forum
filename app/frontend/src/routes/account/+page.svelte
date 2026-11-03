<script lang="ts">
    import { goto } from '$app/navigation';
    import { signOut } from '#lib/auth.ts';
    import { localizeHref as link, m } from '#i18n';
    import { refreshSession, session } from '#lib/session.svelte.ts';

    $effect(() => {
        if (session.ready && !session.user) goto(link('/login?next=/account'));
    });

    async function leave() {
        await signOut();
        await refreshSession();
        await goto(link('/'));
    }
</script>

<svelte:head><title>{m.accountTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.accountTitle()}</h1>
    {#if session.user}
        <p>{m.signedInAs({ username: session.user.username })}</p>
        <button class="btn btn-primary" onclick={leave}>{m.navSignOut()}</button>
    {/if}
</main>
