export interface Activity {
    threadCount: number;
    postCount: number;
    threads: ThreadSummary[];
    posts: ActivityPost[];
}

export interface ActivityPost {
    id: number;
    threadId: number;
    threadTitle: string;
    excerpt: string;
    createdAt: string;
}

export interface Category {
    slug: string;
    title: string;
    description: string;
}

export interface Credentials {
    username: string;
    password: string;
}

export interface CurrentUser {
    id: number;
    username: string;
    role: 'member' | 'moderator' | 'admin';
}

export interface Greeting {
    message: string;
}

export interface NewPost {
    body: string;
}

export interface NewThread {
    title: string;
    body: string;
}

export interface Post {
    id: number;
    author: string;
    body: string;
    createdAt: string;
    editedAt?: string | null;
    deleted?: boolean;
    hidden?: boolean;
}

export interface PostPage {
    items: Post[];
    next?: string | null;
}

export interface Problem {
    message: string;
}

export interface Profile {
    username: string;
    role: 'member' | 'moderator' | 'admin';
    joinedAt: string;
}

export interface ThreadDetail {
    id: number;
    category: string;
    title: string;
    author: string;
    createdAt: string;
    replyCount: number;
    posts: PostPage;
    pinned?: boolean;
    locked?: boolean;
}

export interface ThreadPage {
    items: ThreadSummary[];
    next?: string | null;
    pinned?: ThreadSummary[];
}

export interface ThreadSummary {
    id: number;
    title: string;
    author: string;
    createdAt: string;
    replyCount: number;
    lastPostAt: string;
    pinned?: boolean;
    locked?: boolean;
}
