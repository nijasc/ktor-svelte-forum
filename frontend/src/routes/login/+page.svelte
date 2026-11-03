<script lang="ts">
    import { goto } from '$app/navigation';
    import { signIn, signUp } from '#lib/auth.ts';
    import { localizeHref as link } from '#i18n';

    let username = $state('');
    let password = $state('');
    let error = $state('');

    async function submit(action: typeof signIn) {
        error = '';
        try {
            await action(username, password);
            await goto(link('/account'));
        } catch (failure) {
            error = failure instanceof Error ? failure.message : 'Something went wrong.';
        }
    }
</script>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-8">
    <h1 class="text-2xl font-bold">Sign in</h1>
    <form
        class="flex flex-col gap-2"
        onsubmit={(event) => {
            event.preventDefault();
            submit(signIn);
        }}
    >
        <input class="input w-full" bind:value={username} placeholder="Username" autocomplete="username" required />
        <input
            class="input w-full"
            bind:value={password}
            type="password"
            placeholder="Password"
            autocomplete="current-password"
            required
        />
        {#if error}<p role="alert">{error}</p>{/if}
        <button class="btn btn-primary" type="submit">Sign in</button>
        <button class="btn" type="button" onclick={() => submit(signUp)}>Create account</button>
    </form>
</main>
