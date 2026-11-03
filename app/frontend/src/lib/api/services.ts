import { ApiError, request, type RequestOptions } from './client.js';
import type { Category, Credentials, CurrentUser, Greeting, NewPost, NewThread, Post, Problem, ThreadDetail, ThreadPage } from './types.js';

export const auth = {
    login: (body: Credentials, options?: RequestOptions) => request<CurrentUser>('POST', '/api/auth/login', { ...options, body, operation: 'auth.login' }),
    logout: (options?: RequestOptions) => request<void>('POST', '/api/auth/logout', { ...options, operation: 'auth.logout' }),
    getMe: (options?: RequestOptions) => request<CurrentUser>('GET', '/api/auth/me', { ...options, operation: 'auth.getMe' }),
    register: (body: Credentials, options?: RequestOptions) => request<CurrentUser>('POST', '/api/auth/register', { ...options, body, operation: 'auth.register' }),
};

export const categories = {
    list: (options?: RequestOptions) => request<Category[]>('GET', '/api/categories', { ...options, operation: 'categories.list' }),
    getThreads: (slug: string | number, query?: { limit?: string; cursor?: string }, options?: RequestOptions) => request<ThreadPage>('GET', `/api/categories/${encodeURIComponent(slug)}/threads`, { ...options, query, operation: 'categories.getThreads' }),
    threads: (slug: string | number, body: NewThread, options?: RequestOptions) => request<ThreadDetail>('POST', `/api/categories/${encodeURIComponent(slug)}/threads`, { ...options, body, operation: 'categories.threads' }),
};

export const hello = {
    list: (options?: RequestOptions) => request<Greeting>('GET', '/api/hello', { ...options, operation: 'hello.list' }),
};

export const posts = {
    patch: (id: string | number, body: NewPost, options?: RequestOptions) => request<Post>('PATCH', `/api/posts/${encodeURIComponent(id)}`, { ...options, body, operation: 'posts.patch' }),
    delete: (id: string | number, options?: RequestOptions) => request<void>('DELETE', `/api/posts/${encodeURIComponent(id)}`, { ...options, operation: 'posts.delete' }),
};

export const threads = {
    get: (id: string | number, query?: { limit?: string; cursor?: string }, options?: RequestOptions) => request<ThreadDetail>('GET', `/api/threads/${encodeURIComponent(id)}`, { ...options, query, operation: 'threads.get' }),
    posts: (id: string | number, body: NewPost, options?: RequestOptions) => request<Post>('POST', `/api/threads/${encodeURIComponent(id)}/posts`, { ...options, body, operation: 'threads.posts' }),
};

export interface ApiErrors {
    'auth.login': Problem;
    'auth.register': Problem;
}

const errorStatuses: Record<string, number[]> = {
    'auth.login': [401, 403],
    'auth.register': [409],
};

export function isApiError<K extends keyof ApiErrors>(error: unknown, operation: K): error is ApiError<ApiErrors[K]> {
    return error instanceof ApiError && error.operation === operation && (errorStatuses[operation] ?? []).includes(error.status);
}

export const api = { auth, categories, hello, posts, threads };
