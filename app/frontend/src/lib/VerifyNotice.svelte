<script lang="ts">
    import { localizeHref as link, m } from '#i18n';
</script>

<div class="alert" role="status">
    <span>{m.verifyNotice()}</span>
    <a class="link" href={link('/account')}>{m.verifyNoticeLink()}</a>
</div>
