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
}

export interface PostPage {
    items: Post[];
    next?: string | null;
}

export interface Problem {
    message: string;
}

export interface ThreadDetail {
    id: number;
    category: string;
    title: string;
    author: string;
    createdAt: string;
    replyCount: number;
    posts: PostPage;
}

export interface ThreadPage {
    items: ThreadSummary[];
    next?: string | null;
}

export interface ThreadSummary {
    id: number;
    title: string;
    author: string;
    createdAt: string;
    replyCount: number;
    lastPostAt: string;
}
