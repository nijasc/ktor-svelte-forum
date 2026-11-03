<script lang="ts">
    import Icon from '#icon';
    import { getLocale, localizeHref, m } from '#i18n';
</script>

<main class="mx-auto max-w-2xl p-8">
    <h1 class="flex items-center gap-2 text-3xl font-bold">
        <Icon name="Rocket" />
        {m.welcome({ language: getLocale() })}
    </h1>
    <p>Edit <code>frontend/src/routes/+page.svelte</code>. Your API is at <code>/api/hello</code>.</p>
    <a class="btn btn-primary mt-4" href="https://github.com/nijasc/ktor-svelte">Read the docs</a>
    <a class="btn mt-4" href={localizeHref('/login')}>Sign in</a>
</main>
