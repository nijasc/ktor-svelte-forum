<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { refreshSession } from '#lib/session.svelte.ts';

    let creating = $state(false);
    let username = $state('');
    let email = $state('');
    let password = $state('');
    let error = $state('');
    let busy = $state(false);

    /** Only follow a `next` that stays on this site. */
    function destination(): string {
        const next = page.url.searchParams.get('next');
        return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
    }

    async function submit(event: SubmitEvent) {
        event.preventDefault();
        busy = true;
        error = '';
        try {
            if (creating) await api.auth.register({ username, password, email });
            else await api.auth.login({ username, password });
            await refreshSession();
            await goto(link(destination()));
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            busy = false;
        }
    }
</script>

<svelte:head><title>{creating ? m.createAccount() : m.signInTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{creating ? m.createAccount() : m.signInTitle()}</h1>
    <form class="flex flex-col gap-2" onsubmit={submit}>
        <input class="input w-full" bind:value={username} placeholder={m.username()} autocomplete="username" required />
        {#if creating}
            <input
                class="input w-full"
                bind:value={email}
                type="email"
                placeholder={m.email()}
                autocomplete="email"
                required
            />
        {/if}
        <input
            class="input w-full"
            bind:value={password}
            type="password"
            placeholder={m.password()}
            autocomplete={creating ? 'new-password' : 'current-password'}
            required
        />
        {#if error}<p class="text-error" role="alert">{error}</p>{/if}
        <button class="btn btn-primary" type="submit" disabled={busy}>
            {creating ? m.createAccount() : m.signInTitle()}
        </button>
        <button class="btn" type="button" onclick={() => (creating = !creating)}>
            {creating ? m.haveAccount() : m.createAccount()}
        </button>
        {#if !creating}
            <a class="link text-sm" href={link('/forgot-password')}>{m.forgotPasswordLink()}</a>
        {/if}
    </form>
</main>
