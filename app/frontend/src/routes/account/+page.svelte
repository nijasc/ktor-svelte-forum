<script lang="ts">
    import { goto } from '$app/navigation';
    import { api } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { refreshSession, session } from '#lib/session.svelte.ts';

    let newEmail = $state('');
    let password = $state('');
    let notice = $state('');
    let error = $state('');

    $effect(() => {
        if (session.ready && !session.user) goto(link('/login?next=/account'));
    });

    async function run(action: () => Promise<unknown>, message: string) {
        notice = '';
        error = '';
        try {
            await action();
            notice = message;
            await refreshSession();
        } catch (failure) {
            error = errorMessage(failure);
        }
    }

    async function leave() {
        await api.auth.logout();
        await refreshSession();
        await goto(link('/'));
    }
</script>

<svelte:head><title>{m.accountTitle()}</title></svelte:head>

<main class="mx-auto flex max-w-sm flex-col gap-4 p-4">
    <h1 class="text-2xl font-bold">{m.accountTitle()}</h1>
    {#if session.user}
        <p>{m.signedInAs({ username: session.user.username })}</p>

        <section class="flex flex-col gap-2">
            <h2 class="font-semibold">{m.accountEmail()}</h2>
            <p>
                {session.user.email ?? m.accountNoEmail()}
                {#if session.user.email}
                    {#if session.user.emailVerified}
                        <span class="badge badge-success badge-sm">{m.accountVerified()}</span>
                    {/if}
                {/if}
            </p>
            {#if session.user.email && !session.user.emailVerified}
                <p class="text-sm opacity-80">{m.accountUnverified()}</p>
                <div>
                    <button
                        class="btn btn-sm"
                        onclick={() => run(() => api.auth.verificationResend(), m.accountResent())}
                    >
                        {m.accountResend()}
                    </button>
                </div>
            {/if}
        </section>

        <form
            class="flex flex-col gap-2"
            onsubmit={(event) => {
                event.preventDefault();
                run(async () => {
                    await api.auth.putEmail({ email: newEmail, password });
                    newEmail = password = '';
                }, m.accountEmailChanged());
            }}
        >
            <h2 class="font-semibold">{m.accountChangeEmail()}</h2>
            <input
                class="input w-full"
                bind:value={newEmail}
                type="email"
                placeholder={m.accountNewEmail()}
                autocomplete="email"
                required
            />
            <input
                class="input w-full"
                bind:value={password}
                type="password"
                placeholder={m.accountCurrentPassword()}
                autocomplete="current-password"
                required
            />
            <div><button class="btn btn-sm" type="submit">{m.accountChangeEmailSubmit()}</button></div>
        </form>

        {#if notice}<p role="status">{notice}</p>{/if}
        {#if error}<p class="text-error" role="alert">{error}</p>{/if}

        <button class="btn btn-primary" onclick={leave}>{m.navSignOut()}</button>
    {/if}
</main>
