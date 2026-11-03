export type User = { username: string };

type ErrorBody = { message?: string; fields?: Record<string, string[]> };

async function request(path: string, init: RequestInit = {}): Promise<Response> {
    const response = await fetch(`/api/auth/${path}`, init);
    if (response.ok) return response;
    const body: ErrorBody = await response.json().catch(() => ({}));
    const details = Object.values(body.fields ?? {}).flat();
    throw new Error(details.length > 0 ? details.join(' ') : (body.message ?? 'Something went wrong.'));
}

function send(path: string, username: string, password: string): Promise<Response> {
    return request(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
}

export async function signIn(username: string, password: string): Promise<void> {
    await send('login', username, password);
}

export async function signUp(username: string, password: string): Promise<void> {
    await send('register', username, password);
}

export async function signOut(): Promise<void> {
    await request('logout', { method: 'POST' });
}

export async function currentUser(): Promise<User | null> {
    const response = await fetch('/api/auth/me');
    return response.ok ? response.json() : null;
}
