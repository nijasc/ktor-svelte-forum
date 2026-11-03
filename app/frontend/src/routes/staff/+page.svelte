<script lang="ts">
    import { goto } from '$app/navigation';
    import { localizeHref as link, m } from '#i18n';
    import { isAdmin, isStaff } from '#lib/roles.ts';
    import { session } from '#lib/session.svelte.ts';
    import AuditTab from '#lib/staff/AuditTab.svelte';
    import CategoriesTab from '#lib/staff/CategoriesTab.svelte';
    import ReportsTab from '#lib/staff/ReportsTab.svelte';
    import UsersTab from '#lib/staff/UsersTab.svelte';

    type Tab = 'reports' | 'audit' | 'categories' | 'users';
    let tab = $state<Tab>('reports');

    $effect(() => {
        if (session.ready && !isStaff(session.user)) goto(link('/login?next=/staff'));
    });
</script>

<svelte:head><title>{m.staffTitle()}</title></svelte:head>

<h1 class="mb-4 text-2xl font-bold">{m.staffTitle()}</h1>

{#if isStaff(session.user)}
    <div role="tablist" class="tabs tabs-border mb-4">
        <button role="tab" class="tab" class:tab-active={tab === 'reports'} onclick={() => (tab = 'reports')}>
            {m.staffReports()}
        </button>
        <button role="tab" class="tab" class:tab-active={tab === 'audit'} onclick={() => (tab = 'audit')}>
            {m.staffAudit()}
        </button>
        {#if isAdmin(session.user)}
            <button role="tab" class="tab" class:tab-active={tab === 'categories'} onclick={() => (tab = 'categories')}>
                {m.staffCategories()}
            </button>
        {/if}
        <button role="tab" class="tab" class:tab-active={tab === 'users'} onclick={() => (tab = 'users')}>
            {m.staffUsers()}
        </button>
    </div>

    {#if tab === 'reports'}
        <ReportsTab />
    {:else if tab === 'audit'}
        <AuditTab />
    {:else if tab === 'categories'}
        <CategoriesTab />
    {:else}
        <UsersTab />
    {/if}
{/if}
