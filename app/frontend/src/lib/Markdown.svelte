<script lang="ts">
    import DOMPurify from 'dompurify';
    import { marked } from 'marked';

    let { source }: { source: string } = $props();

    // Posts are written by strangers, so the HTML is cut down to a short list of safe tags before it is shown.
    const allowedTags = [
        'a',
        'p',
        'br',
        'hr',
        'strong',
        'em',
        'del',
        'code',
        'pre',
        'blockquote',
        'ul',
        'ol',
        'li',
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',
        'table',
        'thead',
        'tbody',
        'tr',
        'th',
        'td'
    ];

    DOMPurify.addHook('afterSanitizeAttributes', (node) => {
        if (node.tagName === 'A') {
            node.setAttribute('target', '_blank');
            node.setAttribute('rel', 'noopener noreferrer nofollow ugc');
        }
    });

    marked.use({ renderer: { html: () => '' } });

    const html = $derived(
        DOMPurify.sanitize(marked.parse(source, { async: false, gfm: true, breaks: true }), {
            ALLOWED_TAGS: allowedTags,
            ALLOWED_ATTR: ['href']
        })
    );
</script>

<!-- The HTML above has been sanitised. -->
<!-- eslint-disable-next-line svelte/no-at-html-tags -->
<div class="prose max-w-none break-words">{@html html}</div>
