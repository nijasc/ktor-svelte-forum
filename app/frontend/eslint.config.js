import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import ts from 'typescript-eslint';

export default ts.config(
    {
        ignores: [
            'build/',
            '.svelte-kit/',
            'src/lib/api/',
            'src/lib/ktor-svelte/',
            'vite.config.ts',
            'src/hooks.ts',
            'src/hooks.server.ts'
        ]
    },
    js.configs.recommended,
    ...ts.configs.recommended,
    ...svelte.configs.recommended,
    prettier,
    ...svelte.configs.prettier,
    { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
    { files: ['**/*.svelte', '**/*.svelte.ts'], languageOptions: { parserOptions: { parser: ts.parser } } },
    { rules: { 'svelte/no-navigation-without-resolve': 'off' } }
);
