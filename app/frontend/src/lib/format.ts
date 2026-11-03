import { getLocale } from '#i18n';

/** A date and time in the reader's language, from the ISO text the API returns. */
export function formatDateTime(iso: string): string {
    return new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}

/** How long ago, such as "3 hours ago", in the reader's language. */
export function formatAgo(iso: string): string {
    const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
    const units: [Intl.RelativeTimeFormatUnit, number][] = [
        ['year', 31_536_000],
        ['month', 2_592_000],
        ['day', 86_400],
        ['hour', 3_600],
        ['minute', 60]
    ];
    const format = new Intl.RelativeTimeFormat(getLocale(), { numeric: 'auto' });
    for (const [unit, size] of units) {
        if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit);
    }
    return format.format(Math.min(seconds, 0), 'second');
}
