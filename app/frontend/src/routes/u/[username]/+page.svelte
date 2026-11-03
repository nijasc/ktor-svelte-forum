<script lang="ts">
    import { page } from '$app/state';
    import { api, type Activity, type Profile, type StaffUserView } from '#api';
    import { localizeHref as link, m } from '#i18n';
    import { errorMessage } from '#lib/errors.ts';
    import { formatAgo, formatDate } from '#lib/format.ts';
    import { canBan, isAdmin, isStaff, roleLabel } from '#lib/roles.ts';
    import { session } from '#lib/session.svelte.ts';
    import State from '#lib/State.svelte';

    const username = $derived(page.params.username ?? '');

    let profile = $state<Profile | null>(null);
    let activity = $state<Activity | null>(null);
    let staffView = $state<StaffUserView | null>(null);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let actionError = $state('');

    async function load(name: string) {
        loading = true;
        error = null;
        try {
            [profile, activity] = await Promise.all([api.users.get(name), api.users.getActivity(name)]);
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loading = false;
        }
    }

    async function loadStaff(name: string) {
        staffView = await api.staff.getUsers(name).catch(() => null);
    }

    async function act(action: () => Promise<unknown>) {
        actionError = '';
        try {
            await action();
            await Promise.all([load(username), loadStaff(username)]);
        } catch (failure) {
            actionError = errorMessage(failure);
        }
    }

    function ban() {
        const reason = prompt(m.reasonPrompt())?.trim();
        if (reason) act(() => api.staff.usersBan(username, { reason }));
    }

    function changeRole(event: Event) {
        const role = (event.currentTarget as HTMLSelectElement).value as Profile['role'];
        act(() => api.staff.putUsersRole(username, { role }));
    }

    $effect(() => {
        load(username);
    });

    $effect(() => {
        if (isStaff(session.user)) loadStaff(username);
        else staffView = null;
    });
</script>

<svelte:head><title>{username}</title></svelte:head>

<State {loading} {error} onretry={() => load(username)}>
    {#if profile && activity}
        <header class="mb-6">
            <h1 class="flex flex-wrap items-center gap-2 text-2xl font-bold break-words">
                {profile.username}
                <span class="badge">{roleLabel(profile.role)}</span>
                {#if staffView?.status === 'banned'}<span class="badge badge-error">{m.banned()}</span>{/if}
            </h1>
            <p class="text-sm opacity-70">
                {m.profileJoined({ date: formatDate(profile.joinedAt) })} ·
                {m.profileStats({ threads: activity.threadCount, posts: activity.postCount })}
            </p>

            {#if staffView && session.user && session.user.username.toLowerCase() !== profile.username.toLowerCase()}
                <div class="mt-3 flex flex-wrap items-center gap-2">
                    {#if canBan(session.user.role, staffView.role)}
                        {#if staffView.status === 'banned'}
                            <button class="btn btn-sm" onclick={() => act(() => api.staff.usersUnban(username))}>
                                {m.unban()}
                            </button>
                        {:else}
                            <button class="btn btn-error btn-sm" onclick={ban}>{m.ban()}</button>
                        {/if}
                    {/if}
                    {#if isAdmin(session.user)}
                        <label class="flex items-center gap-2 text-sm">
                            {m.roleLabel()}
                            <select class="select select-sm" value={staffView.role} onchange={changeRole}>
                                {#each ['member', 'moderator', 'admin'] as const as role (role)}
                                    <option value={role}>{roleLabel(role)}</option>
                                {/each}
                            </select>
                        </label>
                    {/if}
                </div>
                {#if actionError}<p class="text-error mt-2" role="alert">{actionError}</p>{/if}
            {/if}
        </header>

        <section class="mb-8">
            <h2 class="mb-2 text-lg font-semibold">{m.profileRecentThreads()}</h2>
            {#if activity.threads.length === 0}
                <p class="opacity-70">{m.profileNothing()}</p>
            {:else}
                <ul class="flex flex-col gap-2">
                    {#each activity.threads as thread (thread.id)}
                        <li>
                            <a class="link" href={link(`/t/${thread.id}`)}>{thread.title}</a>
                            <span class="text-sm opacity-70">· {formatAgo(thread.createdAt)}</span>
                        </li>
                    {/each}
                </ul>
            {/if}
        </section>

        <section>
            <h2 class="mb-2 text-lg font-semibold">{m.profileRecentPosts()}</h2>
            {#if activity.posts.length === 0}
                <p class="opacity-70">{m.profileNothing()}</p>
            {:else}
                <ul class="flex flex-col gap-3">
                    {#each activity.posts as post (post.id)}
                        <li class="card bg-base-100 shadow-sm">
                            <div class="card-body gap-1 p-4">
                                <a class="link font-medium" href={link(`/t/${post.threadId}#post-${post.id}`)}>
                                    {post.threadTitle}
                                </a>
                                <p class="break-words">{post.excerpt}</p>
                                <p class="text-sm opacity-70">{formatAgo(post.createdAt)}</p>
                            </div>
                        </li>
                    {/each}
                </ul>
            {/if}
        </section>
    {/if}
</State>
