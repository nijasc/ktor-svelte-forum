import { ApiError, isValidationError } from '#api';
import { m } from '#i18n';

/** A sentence to show a reader for a failed request. */
export function errorMessage(error: unknown): string {
    if (isValidationError(error)) {
        const details = Object.values(error.body.fields).flat();
        if (details.length > 0) return details.join(' ');
    }
    if (error instanceof ApiError) {
        if (error.status === 429) return m.errorRateLimited();
        if (error.status === 404) return m.errorNotFound();
        const body = error.body as { message?: unknown } | null;
        if (typeof body?.message === 'string') return body.message;
    }
    return m.errorGeneric();
}
