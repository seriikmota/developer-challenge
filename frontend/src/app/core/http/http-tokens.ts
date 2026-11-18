import { HttpContextToken } from '@angular/common/http';

/** Prefix of every API call. In development the dev-server proxies it; in Docker, nginx does. */
export const API = '/api';

/**
 * Statuses the caller handles itself, so the global interceptor stays quiet for them (e.g. a form shows 400
 * errors next to each field, a page shows its own "not found" state).
 */
export const HANDLED_STATUSES = new HttpContextToken<number[]>(() => []);
