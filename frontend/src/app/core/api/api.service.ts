import { HttpClient, HttpContext, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API, HANDLED_STATUSES } from '../http/http-tokens';
import {
  Action,
  Budget,
  CostCenter,
  DashboardSummary,
  LoginResponse,
  Page,
  RequestDetail,
  RequestEvent,
  RequestPayload,
  RequestQuery,
  RequestSummary,
  User,
  Version,
  VersionSummary,
} from './models';

const ACTION_PATHS: Record<Exclude<Action, 'EDIT'>, string> = {
  SUBMIT: 'submit',
  APPROVE: 'approve',
  REJECT: 'reject',
  REQUEST_CHANGES: 'request-changes',
  CANCEL: 'cancel',
};

export type TransitionAction = keyof typeof ACTION_PATHS;

/** Forms show 400 errors next to each field and their own 409 message, so the global one is skipped. */
const FORM_ERRORS = () => new HttpContext().set(HANDLED_STATUSES, [400, 409]);

const NOT_FOUND_HANDLED = () => new HttpContext().set(HANDLED_STATUSES, [404]);

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API}/auth/login`, { email, password });
  }

  me(): Observable<User> {
    return this.http.get<User>(`${API}/auth/me`);
  }

  listRequests(query: RequestQuery): Observable<Page<RequestSummary>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<RequestSummary>>(`${API}/requests`, { params });
  }

  /** The detail page shows its own "not found" state, so 404 is not a global message. */
  getRequest(id: number): Observable<RequestDetail> {
    return this.http.get<RequestDetail>(`${API}/requests/${id}`, { context: NOT_FOUND_HANDLED() });
  }

  /** Create with an idempotency key: a retried or double-clicked submit never creates two requests. */
  createRequest(payload: RequestPayload, idempotencyKey: string): Observable<RequestDetail> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<RequestDetail>(`${API}/requests`, payload, {
      headers,
      context: FORM_ERRORS(),
    });
  }

  updateRequest(id: number, payload: RequestPayload): Observable<RequestDetail> {
    return this.http.put<RequestDetail>(`${API}/requests/${id}`, payload, {
      context: FORM_ERRORS(),
    });
  }

  transition(
    id: number,
    action: TransitionAction,
    lockVersion: number,
    comment: string | null,
  ): Observable<RequestDetail> {
    return this.http.post<RequestDetail>(`${API}/requests/${id}/${ACTION_PATHS[action]}`, {
      lockVersion,
      comment: comment || null,
    });
  }

  versions(id: number): Observable<VersionSummary[]> {
    return this.http.get<VersionSummary[]>(`${API}/requests/${id}/versions`, {
      context: NOT_FOUND_HANDLED(),
    });
  }

  version(id: number, number: number): Observable<Version> {
    return this.http.get<Version>(`${API}/requests/${id}/versions/${number}`);
  }

  history(id: number): Observable<RequestEvent[]> {
    return this.http.get<RequestEvent[]>(`${API}/requests/${id}/history`, {
      context: NOT_FOUND_HANDLED(),
    });
  }

  inbox(page: number, size: number): Observable<Page<RequestSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<RequestSummary>>(`${API}/approvals/inbox`, { params });
  }

  dashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${API}/dashboard/summary`);
  }

  costCenters(): Observable<CostCenter[]> {
    return this.http.get<CostCenter[]>(`${API}/cost-centers`);
  }

  budget(costCenterId: number, month: string): Observable<Budget> {
    return this.http.get<Budget>(`${API}/cost-centers/${costCenterId}/budgets/${month}`);
  }
}
