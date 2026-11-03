<script lang="ts">
    import '#lib/ktor-svelte/app.css';
    import { goto } from '$app/navigation';
    import { onMount } from 'svelte';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { isStaff } from '#lib/roles.ts';
    import { refreshSession, session } from '#lib/session.svelte.ts';

    let { children } = $props();

    onMount(refreshSession);

    async function leave() {
        await api.auth.logout();
        await refreshSession();
        await goto(link('/'));
    }
</script>

<header class="navbar bg-base-100 shadow-sm">
    <div class="mx-auto flex w-full max-w-4xl items-center justify-between px-4">
        <a class="text-xl font-bold" href={link('/')}>{m.siteName()}</a>
        <nav class="flex items-center gap-2">
            {#if session.user}
                {#if isStaff(session.user)}
                    <a class="btn btn-ghost btn-sm" href={link('/staff')}>{m.navStaff()}</a>
                {/if}
                <a class="btn btn-ghost btn-sm" href={link('/account')}>{session.user.username}</a>
                <button class="btn btn-sm" onclick={leave}>{m.navSignOut()}</button>
            {:else if session.ready}
                <a class="btn btn-primary btn-sm" href={link('/login')}>{m.navSignIn()}</a>
            {/if}
        </nav>
    </div>
</header>

<div class="mx-auto w-full max-w-4xl p-4">
    {@render children()}
</div>
