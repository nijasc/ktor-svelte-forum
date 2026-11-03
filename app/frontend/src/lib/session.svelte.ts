import { api, type CurrentUser } from '#api';

/** Who is signed in. `ready` turns true once the first check has finished, so pages can wait before redirecting. */
export const session = $state<{ user: CurrentUser | null; ready: boolean; unread: number }>({
    user: null,
    ready: false,
    unread: 0
});

export async function refreshSession(): Promise<void> {
    try {
        const response = await fetch('/api/auth/me');
        session.user = response.ok ? await response.json() : null;
    } catch {
        session.user = null;
    } finally {
        session.ready = true;
    }
    await refreshUnread();
}

/** How many notifications are waiting. Quietly keeps the old number if the request fails. */
export async function refreshUnread(): Promise<void> {
    if (!session.user) {
        session.unread = 0;
        return;
    }
    try {
        session.unread = (await api.notifications.getUnread()).count;
    } catch {
        // The bell can be a little out of date, which is better than an error on every page.
    }
}
