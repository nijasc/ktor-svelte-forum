import { ApiError, request, type RequestOptions } from './client.js';
import type { Credentials, CurrentUser, Greeting, Problem, VisitCount } from './types.js';

export const auth = {
    login: (body: Credentials, options?: RequestOptions) => request<CurrentUser>('POST', '/api/auth/login', { ...options, body, operation: 'auth.login' }),
    logout: (options?: RequestOptions) => request<void>('POST', '/api/auth/logout', { ...options, operation: 'auth.logout' }),
    getMe: (options?: RequestOptions) => request<CurrentUser>('GET', '/api/auth/me', { ...options, operation: 'auth.getMe' }),
    register: (body: Credentials, options?: RequestOptions) => request<CurrentUser>('POST', '/api/auth/register', { ...options, body, operation: 'auth.register' }),
};

export const hello = {
    list: (options?: RequestOptions) => request<Greeting>('GET', '/api/hello', { ...options, operation: 'hello.list' }),
};

export const visits = {
    create: (options?: RequestOptions) => request<VisitCount>('POST', '/api/visits', { ...options, operation: 'visits.create' }),
};

export interface ApiErrors {
    'auth.login': Problem;
    'auth.register': Problem;
}

const errorStatuses: Record<string, number[]> = {
    'auth.login': [401],
    'auth.register': [409],
};

export function isApiError<K extends keyof ApiErrors>(error: unknown, operation: K): error is ApiError<ApiErrors[K]> {
    return error instanceof ApiError && error.operation === operation && (errorStatuses[operation] ?? []).includes(error.status);
}

export const api = { auth, hello, visits };
