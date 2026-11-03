<script lang="ts">
    import { onMount } from 'svelte';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { refreshSession } from '#lib/session.svelte.ts';

    let status = $state<'working' | 'done' | 'failed'>('working');
    let error = $state('');

    onMount(async () => {
        // Read from the address in the browser, because this page is built ahead of time.
        const token = new URLSearchParams(location.search).get('token') ?? '';
        try {
            await api.auth.verify({ token });
            await refreshSession();
            status = 'done';
        } catch (failure) {
            error = errorMessage(failure);
            status = 'failed';
        }
    });
</script>

<svelte:head><title>{m.verifyTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.verifyTitle()}</h1>
    {#if status === 'working'}
        <span class="loading loading-spinner" role="status" aria-label={m.verifyWorking()}></span>
    {:else if status === 'done'}
        <p role="status">{m.verifyDone()}</p>
        <a class="btn btn-primary" href={link('/')}>{m.verifyContinue()}</a>
    {:else}
        <p class="text-error" role="alert">{error}</p>
        <a class="link" href={link('/account')}>{m.accountResend()}</a>
    {/if}
</main>
