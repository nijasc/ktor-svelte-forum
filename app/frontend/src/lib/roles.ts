import type { CurrentUser } from '#api';
import { m } from '#i18n';

type Viewer = Pick<CurrentUser, 'role'> | null | undefined;

/** Moderators and admins. */
export const isStaff = (user: Viewer): boolean => user?.role === 'moderator' || user?.role === 'admin';

export const isAdmin = (user: Viewer): boolean => user?.role === 'admin';

/** The label for a role, in the reader's language. */
export function roleLabel(role: CurrentUser['role']): string {
    return { member: m.roleMember(), moderator: m.roleModerator(), admin: m.roleAdmin() }[role];
}

/** Mirrors the server's ban rules, which decide in the end. Moderators handle members, admins also moderators. */
export function canBan(actor: CurrentUser['role'], target: CurrentUser['role']): boolean {
    if (target === 'member') return actor === 'moderator' || actor === 'admin';
    if (target === 'moderator') return actor === 'admin';
    return false;
}
