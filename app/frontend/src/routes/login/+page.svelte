<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import { signIn, signUp } from '#lib/auth.ts';
    import { localizeHref as link, m } from '#i18n';
    import { refreshSession } from '#lib/session.svelte.ts';

    let username = $state('');
    let password = $state('');
    let error = $state('');

    /** Only follow a `next` that stays on this site. */
    function destination(): string {
        const next = page.url.searchParams.get('next');
        return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
    }

    async function submit(action: typeof signIn) {
        error = '';
        try {
            await action(username, password);
            await refreshSession();
            await goto(link(destination()));
        } catch (failure) {
            error = failure instanceof Error ? failure.message : m.errorGeneric();
        }
    }
</script>

<svelte:head><title>{m.signInTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.signInTitle()}</h1>
    <form
        class="flex flex-col gap-2"
        onsubmit={(event) => {
            event.preventDefault();
            submit(signIn);
        }}
    >
        <input class="input w-full" bind:value={username} placeholder={m.username()} autocomplete="username" required />
        <input
            class="input w-full"
            bind:value={password}
            type="password"
            placeholder={m.password()}
            autocomplete="current-password"
            required
        />
        {#if error}<p class="text-error" role="alert">{error}</p>{/if}
        <button class="btn btn-primary" type="submit">{m.signInTitle()}</button>
        <button class="btn" type="button" onclick={() => submit(signUp)}>{m.createAccount()}</button>
    </form>
</main>
