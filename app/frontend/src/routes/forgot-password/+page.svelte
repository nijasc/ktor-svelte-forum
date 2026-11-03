<script lang="ts">
    import { api } from '#api';
    import { m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';

    let email = $state('');
    let sent = $state(false);
    let error = $state('');
    let busy = $state(false);

    async function submit(event: SubmitEvent) {
        event.preventDefault();
        busy = true;
        error = '';
        try {
            await api.auth.forgotPassword({ email });
            sent = true;
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            busy = false;
        }
    }
</script>

<svelte:head><title>{m.forgotTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.forgotTitle()}</h1>
    {#if sent}
        <p role="status">{m.forgotSent()}</p>
    {:else}
        <p>{m.forgotIntro()}</p>
        <form class="flex flex-col gap-2" onsubmit={submit}>
            <input
                class="input w-full"
                bind:value={email}
                type="email"
                placeholder={m.email()}
                autocomplete="email"
                required
            />
            {#if error}<p class="text-error" role="alert">{error}</p>{/if}
            <button class="btn btn-primary" type="submit" disabled={busy}>{m.forgotSend()}</button>
        </form>
    {/if}
</main>
