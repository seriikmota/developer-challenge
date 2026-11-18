import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ApiService } from '../api/api.service';
import { LoginResponse, Role, User } from '../api/models';

const STORAGE_KEY = 'purchase.session';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

/**
 * Holds the JWT and the logged user. The session lives in sessionStorage: it survives a reload but not
 * closing the tab, and it is dropped as soon as the token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);

  readonly isLoggedIn = computed(() => this.session() !== null);

  token(): string | null {
    const session = this.session();
    if (session && this.expired(session)) {
      this.logout();
      return null;
    }
    return session?.token ?? null;
  }

  hasValidSession(): boolean {
    return this.token() !== null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.user();
    return user !== null && roles.includes(user.role);
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.api.login(email, password).pipe(
      tap((response) => {
        const session: Session = {
          token: response.accessToken,
          expiresAt: response.expiresAt,
          user: response.user,
        };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      }),
    );
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  private expired(session: Session): boolean {
    return Date.parse(session.expiresAt) <= Date.now();
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      const session = raw ? (JSON.parse(raw) as Session) : null;
      return session && !this.expired(session) ? session : null;
    } catch {
      return null;
    }
  }
}
