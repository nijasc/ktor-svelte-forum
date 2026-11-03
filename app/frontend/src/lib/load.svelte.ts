import { errorMessage } from '#lib/errors.ts';

/** Runs [fetcher] now and again on `reload()`, and exposes its state for a page to render. */
export function loader<T>(fetcher: () => Promise<T>) {
    let loading = $state(true);
    let error = $state<string | null>(null);
    let data = $state<T | null>(null);

    async function reload() {
        loading = true;
        error = null;
        try {
            data = await fetcher();
        } catch (failure) {
            error = errorMessage(failure);
        } finally {
            loading = false;
        }
    }

    reload();

    return {
        get loading() {
            return loading;
        },
        get error() {
            return error;
        },
        get data() {
            return data;
        },
        reload
    };
}
