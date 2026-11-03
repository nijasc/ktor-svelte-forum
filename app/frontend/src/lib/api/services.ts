import { ApiError, request, type RequestOptions } from './client.js';
import type { ActionReason, Activity, AuditPage, Category, CategoryOrder, Credentials, CurrentUser, Greeting, MoveThread, NewCategory, NewPost, NewReport, NewThread, Post, Problem, Profile, ReportPage, ResolveReport, RoleChange, StaffCategory, StaffUserView, ThreadDetail, ThreadPage, UpdateCategory } from './types.js';

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
    reports: (id: string | number, body: NewReport, options?: RequestOptions) => request<void>('POST', `/api/posts/${encodeURIComponent(id)}/reports`, { ...options, body, operation: 'posts.reports' }),
};

export const staff = {
    getAudit: (query?: { cursor?: string; limit?: string }, options?: RequestOptions) => request<AuditPage>('GET', '/api/staff/audit', { ...options, query, operation: 'staff.getAudit' }),
    getCategories: (options?: RequestOptions) => request<StaffCategory[]>('GET', '/api/staff/categories', { ...options, operation: 'staff.getCategories' }),
    categories: (body: NewCategory, options?: RequestOptions) => request<StaffCategory>('POST', '/api/staff/categories', { ...options, body, operation: 'staff.categories' }),
    putCategoriesOrder: (body: CategoryOrder, options?: RequestOptions) => request<void>('PUT', '/api/staff/categories/order', { ...options, body, operation: 'staff.putCategoriesOrder' }),
    patchCategories: (slug: string | number, body: UpdateCategory, options?: RequestOptions) => request<StaffCategory>('PATCH', `/api/staff/categories/${encodeURIComponent(slug)}`, { ...options, body, operation: 'staff.patchCategories' }),
    postsHide: (id: string | number, body: ActionReason, options?: RequestOptions) => request<void>('POST', `/api/staff/posts/${encodeURIComponent(id)}/hide`, { ...options, body, operation: 'staff.postsHide' }),
    postsRestore: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/posts/${encodeURIComponent(id)}/restore`, { ...options, operation: 'staff.postsRestore' }),
    getReports: (query?: { cursor?: string; limit?: string }, options?: RequestOptions) => request<ReportPage>('GET', '/api/staff/reports', { ...options, query, operation: 'staff.getReports' }),
    reportsResolve: (id: string | number, body: ResolveReport, options?: RequestOptions) => request<void>('POST', `/api/staff/reports/${encodeURIComponent(id)}/resolve`, { ...options, body, operation: 'staff.reportsResolve' }),
    threadsHide: (id: string | number, body: ActionReason, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/hide`, { ...options, body, operation: 'staff.threadsHide' }),
    threadsLock: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/lock`, { ...options, operation: 'staff.threadsLock' }),
    threadsMove: (id: string | number, body: MoveThread, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/move`, { ...options, body, operation: 'staff.threadsMove' }),
    threadsPin: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/pin`, { ...options, operation: 'staff.threadsPin' }),
    threadsRestore: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/restore`, { ...options, operation: 'staff.threadsRestore' }),
    threadsUnlock: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/unlock`, { ...options, operation: 'staff.threadsUnlock' }),
    threadsUnpin: (id: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/threads/${encodeURIComponent(id)}/unpin`, { ...options, operation: 'staff.threadsUnpin' }),
    getUsers: (username: string | number, options?: RequestOptions) => request<StaffUserView>('GET', `/api/staff/users/${encodeURIComponent(username)}`, { ...options, operation: 'staff.getUsers' }),
    usersBan: (username: string | number, body: ActionReason, options?: RequestOptions) => request<void>('POST', `/api/staff/users/${encodeURIComponent(username)}/ban`, { ...options, body, operation: 'staff.usersBan' }),
    putUsersRole: (username: string | number, body: RoleChange, options?: RequestOptions) => request<void>('PUT', `/api/staff/users/${encodeURIComponent(username)}/role`, { ...options, body, operation: 'staff.putUsersRole' }),
    usersUnban: (username: string | number, options?: RequestOptions) => request<void>('POST', `/api/staff/users/${encodeURIComponent(username)}/unban`, { ...options, operation: 'staff.usersUnban' }),
};

export const threads = {
    get: (id: string | number, query?: { limit?: string; cursor?: string }, options?: RequestOptions) => request<ThreadDetail>('GET', `/api/threads/${encodeURIComponent(id)}`, { ...options, query, operation: 'threads.get' }),
    posts: (id: string | number, body: NewPost, options?: RequestOptions) => request<Post>('POST', `/api/threads/${encodeURIComponent(id)}/posts`, { ...options, body, operation: 'threads.posts' }),
};

export const users = {
    get: (username: string | number, options?: RequestOptions) => request<Profile>('GET', `/api/users/${encodeURIComponent(username)}`, { ...options, operation: 'users.get' }),
    getActivity: (username: string | number, options?: RequestOptions) => request<Activity>('GET', `/api/users/${encodeURIComponent(username)}/activity`, { ...options, operation: 'users.getActivity' }),
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

export const api = { auth, categories, hello, posts, staff, threads, users };
