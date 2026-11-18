import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats of every date shown to the user. The API exchanges ISO values (2026-11-18, 2026-11, UTC instants);
 * these patterns are for display only.
 */
export const DATE = 'dd/MM/yyyy';
export const DATE_TIME = 'dd/MM/yyyy HH:mm';
export const DATE_TIME_SECONDS = 'dd/MM/yyyy HH:mm:ss';

/** "2026-11" (competence month in the API) → "11/2026". */
export function formatMonth(month: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})$/.exec(month ?? '');
  return match ? `${match[2]}/${match[1]}` : '—';
}

@Pipe({ name: 'month' })
export class MonthPipe implements PipeTransform {
  transform(month: string | null | undefined): string {
    return formatMonth(month);
  }
}
