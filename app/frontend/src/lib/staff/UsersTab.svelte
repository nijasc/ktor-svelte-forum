<script lang="ts">
    import { goto } from '$app/navigation';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';

    let username = $state('');
    let notFound = $state(false);

    async function find(event: SubmitEvent) {
        event.preventDefault();
        notFound = false;
        const name = username.trim();
        try {
            await api.users.get(name);
            await goto(link(`/u/${encodeURIComponent(name)}`));
        } catch {
            notFound = true;
        }
    }
</script>

<form class="flex max-w-sm flex-col gap-2" onsubmit={find}>
    <input class="input w-full" bind:value={username} placeholder={m.userSearch()} required />
    {#if notFound}<p class="text-error" role="alert">{m.userNotFound()}</p>{/if}
    <div><button class="btn btn-primary" type="submit">{m.userFind()}</button></div>
</form>
