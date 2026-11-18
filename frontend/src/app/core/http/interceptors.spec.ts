import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { API, HANDLED_STATUSES } from './http-tokens';
import { authInterceptor, errorInterceptor } from './interceptors';
import { Notifier } from './notifier.service';
import { CONFLICT_MESSAGE } from './problem';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let notifier: { error: ReturnType<typeof vi.fn>; success: ReturnType<typeof vi.fn> };
  let auth: { token: () => string | null; logout: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    notifier = { error: vi.fn(), success: vi.fn() };
    auth = { token: () => 'jwt-token', logout: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: notifier },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function failWith(status: number, body: object, context?: HttpContext): unknown {
    let received: unknown;
    http
      .post(`${API}/requests/1/approve`, {}, { context })
      .subscribe({ error: (error) => (received = error) });
    backend.expectOne(`${API}/requests/1/approve`).flush(body, { status, statusText: 'error' });
    return received;
  }

  it('sends the bearer token and a request id', () => {
    http.get(`${API}/requests`).subscribe();

    const request = backend.expectOne(`${API}/requests`).request;
    expect(request.headers.get('Authorization')).toBe('Bearer jwt-token');
    expect(request.headers.get('X-Request-Id')).toMatch(/[0-9a-f-]{36}/);
  });

  it('409 tells the user that someone acted first', () => {
    const error = failWith(409, { code: 'CONCURRENT_MODIFICATION', detail: 'lockVersion 3 != 4' });

    expect(notifier.error).toHaveBeenCalledWith(CONFLICT_MESSAGE);
    expect(error).toBeTruthy();
  });

  it('422 shows the business rule explained by the API', () => {
    const detail =
      'Não é possível aprovar: o centro de custo MKT tem R$ 200,00 disponíveis em 2026-11; a solicitação exige R$ 900,00.';

    failWith(422, { code: 'BUDGET_EXCEEDED', detail });

    expect(notifier.error).toHaveBeenCalledWith(detail);
  });

  it('stays quiet for statuses the caller handles itself', () => {
    failWith(
      409,
      { code: 'CONCURRENT_MODIFICATION' },
      new HttpContext().set(HANDLED_STATUSES, [409]),
    );

    expect(notifier.error).not.toHaveBeenCalled();
  });

  it('401 ends the session and goes to the login, returning to the same screen', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    failWith(401, { code: 'UNAUTHORIZED' });

    expect(auth.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });
});
