<script lang="ts">
    import { onMount } from 'svelte';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';

    let token = $state('');
    let password = $state('');
    let done = $state(false);
    let error = $state('');
    let busy = $state(false);

    onMount(() => {
        // Read from the address in the browser, because this page is built ahead of time.
        token = new URLSearchParams(location.search).get('token') ?? '';
    });

    async function submit(event: SubmitEvent) {
        event.preventDefault();
        busy = true;
        error = '';
        try {
            await api.auth.resetPassword({ token, password });
            done = true;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            busy = false;
        }
    }
</script>

<svelte:head><title>{m.resetTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.resetTitle()}</h1>
    {#if done}
        <p role="status">{m.resetDone()}</p>
        <a class="btn btn-primary" href={link('/login')}>{m.signInTitle()}</a>
    {:else if !token}
        <p class="text-error" role="alert">{m.resetMissing()}</p>
    {:else}
        <form class="flex flex-col gap-2" onsubmit={submit}>
            <input
                class="input w-full"
                bind:value={password}
                type="password"
                placeholder={m.resetNewPassword()}
                autocomplete="new-password"
                required
            />
            {#if error}<p class="text-error" role="alert">{error}</p>{/if}
            <button class="btn btn-primary" type="submit" disabled={busy}>{m.resetSubmit()}</button>
        </form>
    {/if}
</main>
