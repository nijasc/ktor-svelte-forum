<script lang="ts">
    // The server marks the matching words with these two control characters instead of HTML. Splitting on them and
    // drawing the pieces keeps the text from being treated as markup, whatever it contains.
    const START = '\u0001';
    const END = '\u0002';

    let { text }: { text: string } = $props();

    const parts = $derived.by(() => {
        const pieces: { text: string; match: boolean }[] = [];
        let match = false;
        for (const piece of text.split(new RegExp(`([${START}${END}])`))) {
            if (piece === START) match = true;
            else if (piece === END) match = false;
            else if (piece) pieces.push({ text: piece, match });
        }
        return pieces;
    });
</script>

<span class="break-words">
    {#each parts as part, index (index)}
        {#if part.match}<mark>{part.text}</mark>{:else}{part.text}{/if}
    {/each}
</span>
