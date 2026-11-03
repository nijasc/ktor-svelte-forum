export interface ActionReason {
    reason: string;
}

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

export interface AuditEntry {
    id: number;
    actor: string;
    action: string;
    targetType: string;
    targetId: string;
    detail: string;
    createdAt: string;
}

export interface AuditPage {
    items: AuditEntry[];
    next?: string | null;
}

export interface Category {
    slug: string;
    title: string;
    description: string;
}

export interface CategoryOrder {
    slugs: string[];
}

export interface ChangeEmail {
    email: string;
    password: string;
}

export interface Credentials {
    username: string;
    password: string;
}

export interface CurrentUser {
    id: number;
    username: string;
    role: 'member' | 'moderator' | 'admin';
    email?: string | null;
    emailVerified?: boolean;
}

export interface ForgotPassword {
    email: string;
}

export interface Greeting {
    message: string;
}

export interface MoveThread {
    category: string;
}

export interface NewCategory {
    slug: string;
    title: string;
    description?: string;
}

export interface NewPost {
    body: string;
}

export interface NewReaction {
    kind: 'like' | 'love' | 'laugh' | 'celebrate';
}

export interface NewReport {
    reason: string;
}

export interface NewThread {
    title: string;
    body: string;
}

export interface NotificationPage {
    items: NotificationView[];
    next?: string | null;
    unread: number;
}

export interface NotificationView {
    id: number;
    kind: 'reply' | 'mention';
    actor: string;
    threadId: number;
    threadTitle: string;
    postId: number;
    count: number;
    read: boolean;
    createdAt: string;
}

export interface Post {
    id: number;
    author: string;
    body: string;
    createdAt: string;
    editedAt?: string | null;
    deleted?: boolean;
    hidden?: boolean;
    reactions?: Record<string, number>;
    myReaction?: string | null;
}

export interface PostPage {
    items: Post[];
    next?: string | null;
}

export interface PostReactions {
    reactions: Record<string, number>;
    myReaction?: string | null;
}

export interface Problem {
    message: string;
}

export interface Profile {
    username: string;
    role: 'member' | 'moderator' | 'admin';
    joinedAt: string;
}

export interface Registration {
    username: string;
    password: string;
    email: string;
}

export interface ReportPage {
    items: ReportView[];
    next?: string | null;
}

export interface ReportView {
    id: number;
    post: ReportedPost;
    reporter: string;
    reason: string;
    createdAt: string;
}

export interface ReportedPost {
    id: number;
    threadId: number;
    threadTitle: string;
    author: string;
    body: string;
    hidden: boolean;
}

export interface ResetPassword {
    token: string;
    password: string;
}

export interface ResolveReport {
    outcome: 'dismissed' | 'actioned';
    note?: string;
}

export interface RoleChange {
    role: 'member' | 'moderator' | 'admin';
}

export interface StaffCategory {
    slug: string;
    title: string;
    description: string;
    position: number;
    archived: boolean;
}

export interface StaffUserView {
    username: string;
    role: 'member' | 'moderator' | 'admin';
    status: 'active' | 'banned';
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

export interface UnreadCount {
    count: number;
}

export interface UpdateCategory {
    title?: string | null;
    description?: string | null;
    archived?: boolean | null;
}

export interface VerifyEmail {
    token: string;
}

export interface WatchState {
    watching: boolean;
}
