import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { API, HANDLED_STATUSES } from './http-tokens';
import { Notifier } from './notifier.service';
import { problemMessage, toProblem } from './problem';

/** Adds the bearer token and a correlation id to every API call. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API)) {
    return next(request);
  }
  const token = inject(AuthService).token();
  const headers: Record<string, string> = { 'X-Request-Id': crypto.randomUUID() };
  if (token && !request.url.startsWith(`${API}/auth/login`)) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return next(request.clone({ setHeaders: headers }));
};

/**
 * 401: the session expired or the token is invalid, so it goes to the login and back to the same screen.
 * Other errors become a message, unless the caller declared it handles that status.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifier = inject(Notifier);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const isLogin = request.url.startsWith(`${API}/auth/login`);
        if (error.status === 401 && !isLogin) {
          auth.logout();
          notifier.error('Sua sessão expirou. Entre novamente para continuar.');
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
        } else if (!request.context.get(HANDLED_STATUSES).includes(error.status) && !isLogin) {
          notifier.error(problemMessage(toProblem(error)));
        }
      }
      return throwError(() => error);
    }),
  );
};
