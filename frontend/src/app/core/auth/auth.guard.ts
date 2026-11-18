import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../api/models';
import { AuthService } from './auth.service';

/** Requires a valid session; otherwise goes to the login and comes back to the requested route. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.hasValidSession()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Hides screens that make no sense for a role (e.g. the approval inbox for a requester). This is navigation
 * only: what each user may do on a request always comes from the API (allowedActions).
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.hasRole(...roles) ? true : inject(Router).createUrlTree(['/']);
  };
}
