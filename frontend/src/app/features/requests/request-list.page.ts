import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap, tap } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import {
  CATEGORIES,
  Category,
  Page,
  RequestQuery,
  RequestStatus,
  RequestSummary,
  STATUSES,
} from '../../core/api/models';
import { problemMessage, toProblem } from '../../core/http/problem';
import { CATEGORY_LABELS, STATUS_LABELS } from '../../shared/labels';
import { DATE_TIME } from '../../shared/date-formats';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageState } from '../../shared/page-state';
import { StatusChip } from '../../shared/status-chip';

const DEFAULT_SIZE = 20;
const DEFAULT_SORT = 'createdAt,desc';
const FILTER_KEYS = [
  'status',
  'category',
  'costCenterId',
  'q',
  'createdFrom',
  'createdTo',
  'minAmount',
  'maxAmount',
] as const;

/** The URL is the source of truth: filters, page and sort live in the query string, so a link is shareable. */
export function queryFromUrl(params: ParamMap): RequestQuery {
  const number = (key: string, fallback: number) => {
    const value = Number(params.get(key));
    return params.has(key) && Number.isInteger(value) && value >= 0 ? value : fallback;
  };
  return {
    status: params.get('status') as RequestStatus | null,
    category: params.get('category') as Category | null,
    costCenterId: params.has('costCenterId') ? Number(params.get('costCenterId')) : null,
    q: params.get('q'),
    createdFrom: params.get('createdFrom'),
    createdTo: params.get('createdTo'),
    minAmount: params.get('minAmount'),
    maxAmount: params.get('maxAmount'),
    page: number('page', 0),
    size: number('size', DEFAULT_SIZE) || DEFAULT_SIZE,
    sort: params.get('sort') ?? DEFAULT_SORT,
  };
}

interface ListState {
  loading: boolean;
  error: string | null;
  page: Page<RequestSummary> | null;
}

@Component({
  selector: 'app-request-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MoneyPipe,
    StatusChip,
    PageState,
  ],
  template: `
    <header class="page-header">
      <h1>Solicitações</h1>
      <a matButton="filled" routerLink="/requests/new">
        <mat-icon aria-hidden="true">add</mat-icon> Nova solicitação
      </a>
    </header>

    <form class="filters" [formGroup]="filters" (ngSubmit)="applyFilters()" aria-label="Filtros">
      <mat-form-field>
        <mat-label>Buscar</mat-label>
        <input matInput type="search" formControlName="q" title="Busca no título e na descrição" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Status</mat-label>
        <mat-select formControlName="status">
          <mat-option value="">Todos</mat-option>
          @for (status of statuses; track status) {
            <mat-option [value]="status">{{ statusLabels[status] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Categoria</mat-label>
        <mat-select formControlName="category">
          <mat-option value="">Todas</mat-option>
          @for (category of categories; track category) {
            <mat-option [value]="category">{{ categoryLabels[category] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Criada de</mat-label>
        <input matInput type="date" formControlName="createdFrom" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Criada até</mat-label>
        <input matInput type="date" formControlName="createdTo" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Valor mínimo</mat-label>
        <input matInput inputmode="decimal" formControlName="minAmount" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Valor máximo</mat-label>
        <input matInput inputmode="decimal" formControlName="maxAmount" />
      </mat-form-field>
      <div class="filter-actions">
        <button matButton="tonal" type="submit">Filtrar</button>
        <button matButton type="button" (click)="clearFilters()">Limpar</button>
      </div>
    </form>

    <app-page-state
      [loading]="state().loading"
      [error]="state().error"
      [empty]="state().page?.totalElements === 0"
      emptyMessage="Nenhuma solicitação encontrada com esses filtros."
      (retry)="reload()"
    >
      @if (state().page; as page) {
        <div class="table-wrapper">
          <table
            mat-table
            [dataSource]="page.data"
            matSort
            [matSortActive]="sortField()"
            [matSortDirection]="sortDirection()"
            matSortDisableClear
            (matSortChange)="changeSort($event)"
          >
            <ng-container matColumnDef="id">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>#</th>
              <td mat-cell *matCellDef="let row">{{ row.id }}</td>
            </ng-container>
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Título</th>
              <td mat-cell *matCellDef="let row">
                <a [routerLink]="['/requests', row.id]">{{ row.title }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="category">
              <th mat-header-cell *matHeaderCellDef>Categoria</th>
              <td mat-cell *matCellDef="let row">{{ categoryLabels[row.category] }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef class="numeric">Total</th>
              <td mat-cell *matCellDef="let row" class="numeric">{{ row.total | money }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Status</th>
              <td mat-cell *matCellDef="let row"><app-status-chip [status]="row.status" /></td>
            </ng-container>
            <ng-container matColumnDef="owner">
              <th mat-header-cell *matHeaderCellDef>Solicitante</th>
              <td mat-cell *matCellDef="let row">{{ row.ownerName }}</td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Criada em</th>
              <td mat-cell *matCellDef="let row">{{ row.createdAt | date: dateTime }}</td>
            </ng-container>
            <ng-container matColumnDef="updatedAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Atualizada em</th>
              <td mat-cell *matCellDef="let row">{{ row.updatedAt | date: dateTime }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
        <mat-paginator
          [length]="page.totalElements"
          [pageIndex]="page.page"
          [pageSize]="page.size"
          [pageSizeOptions]="[10, 20, 50, 100]"
          showFirstLastButtons
          (page)="changePage($event)"
        />
      }
    </app-page-state>
  `,
  styles: `
    .filters {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 0 12px;
      align-items: start;
    }
    .filter-actions {
      display: flex;
      gap: 8px;
      padding-top: 8px;
    }
  `,
})
export class RequestListPage {
  protected readonly dateTime = DATE_TIME;
  private readonly api = inject(ApiService);

  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  protected readonly statuses = STATUSES;

  protected readonly categories = CATEGORIES;

  protected readonly statusLabels = STATUS_LABELS;

  protected readonly categoryLabels: Record<string, string> = CATEGORY_LABELS;

  protected readonly columns = [
    'id',
    'title',
    'category',
    'total',
    'status',
    'owner',
    'createdAt',
    'updatedAt',
  ];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: [''],
    status: [''],
    category: [''],
    createdFrom: [''],
    createdTo: [''],
    minAmount: [''],
    maxAmount: [''],
  });

  private readonly query = toSignal(this.route.queryParamMap.pipe(map(queryFromUrl)), {
    requireSync: true,
  });

  private readonly reloads = new Subject<void>();

  protected readonly state = signal<ListState>({ loading: true, error: null, page: null });

  protected readonly sortField = () => (this.query().sort ?? DEFAULT_SORT).split(',')[0];

  protected readonly sortDirection = () =>
    (this.query().sort ?? DEFAULT_SORT).split(',')[1] === 'asc' ? 'asc' : 'desc';

  constructor() {
    combineLatest([this.route.queryParamMap, this.reloads.pipe(startWith(undefined))])
      .pipe(
        map(([params]) => queryFromUrl(params)),
        tap((query) => {
          this.filters.patchValue(
            {
              q: query.q ?? '',
              status: query.status ?? '',
              category: query.category ?? '',
              createdFrom: query.createdFrom ?? '',
              createdTo: query.createdTo ?? '',
              minAmount: query.minAmount ?? '',
              maxAmount: query.maxAmount ?? '',
            },
            { emitEvent: false },
          );
          this.state.update((state) => ({ ...state, loading: true, error: null }));
        }),
        switchMap((query) =>
          this.api.listRequests(query).pipe(
            map((page) => ({ loading: false, error: null, page }) as ListState),
            catchError((error: unknown) =>
              of({ loading: false, error: problemMessage(toProblem(error)), page: null }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected applyFilters(): void {
    const values = this.filters.getRawValue();
    const params: Record<string, string | null> = {};
    for (const key of FILTER_KEYS) {
      const value = key in values ? values[key as keyof typeof values].trim() : '';
      params[key] = value === '' ? null : value;
    }
    params['costCenterId'] = this.route.snapshot.queryParamMap.get('costCenterId');
    this.navigate({ ...params, page: null });
  }

  protected clearFilters(): void {
    this.filters.reset();
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected changeSort(sort: Sort): void {
    this.navigate({ sort: `${sort.active},${sort.direction || 'desc'}`, page: null });
  }

  protected changePage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex || null, size: event.pageSize });
  }

  protected reload(): void {
    this.reloads.next();
  }

  private navigate(params: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }
}
