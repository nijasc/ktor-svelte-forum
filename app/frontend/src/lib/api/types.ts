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
    username: string;
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

export interface Problem {
    message: string;
}

export interface ThreadDetail {
    id: number;
    category: string;
    title: string;
    author: string;
    createdAt: string;
    posts: Post[];
}

export interface ThreadSummary {
    id: number;
    title: string;
    author: string;
    createdAt: string;
}
