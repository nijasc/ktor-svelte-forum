<script lang="ts">
    import { goto } from '$app/navigation';
    import { onMount } from 'svelte';
    import { currentUser, signOut, type User } from '#lib/auth.ts';
    import { localizeHref as link } from '#i18n';

    let user = $state<User | null>(null);

    onMount(async () => {
        user = await currentUser();
        if (!user) await goto(link('/login'));
    });

    async function leave() {
        await signOut();
        await goto(link('/login'));
    }
</script>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-8">
    <h1 class="text-2xl font-bold">Account</h1>
    {#if user}
        <p>Signed in as {user.username}.</p>
        <button class="btn btn-primary" onclick={leave}>Sign out</button>
    {/if}
</main>
