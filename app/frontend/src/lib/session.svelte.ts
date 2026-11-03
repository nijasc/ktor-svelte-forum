import type { CurrentUser } from '#api';

/** Who is signed in. `ready` turns true once the first check has finished, so pages can wait before redirecting. */
export const session = $state<{ user: CurrentUser | null; ready: boolean }>({ user: null, ready: false });

export async function refreshSession(): Promise<void> {
    try {
        const response = await fetch('/api/auth/me');
        session.user = response.ok ? await response.json() : null;
    } catch {
        session.user = null;
    } finally {
        session.ready = true;
    }
}
