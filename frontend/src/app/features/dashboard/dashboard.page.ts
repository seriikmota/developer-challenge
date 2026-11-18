import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Budget, DashboardSummary, STATUSES } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage, toProblem } from '../../core/http/problem';
import { STATUS_LABELS } from '../../shared/labels';
import { MonthPipe } from '../../shared/date-formats';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageState } from '../../shared/page-state';

@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatCardModule, MatProgressBarModule, MoneyPipe, MonthPipe, PageState],
  template: `
    <h1>Dashboard</h1>
    <p class="subtitle">{{ scope() }}</p>

    <app-page-state [loading]="loading()" [error]="error()" (retry)="load()">
      @if (summary(); as data) {
        <section aria-label="Totais por status" class="totals">
          @for (status of statuses; track status) {
            <a
              class="total-card"
              [class]="'total-card status-border-' + status"
              routerLink="/requests"
              [queryParams]="{ status }"
            >
              <span class="total-value">{{ data.totalsByStatus[status] ?? 0 }}</span>
              <span class="total-label">{{ statusLabels[status] }}</span>
            </a>
          }
        </section>

        <h2>Orçamento de {{ data.month | month }}</h2>
        @if (data.budgets.length === 0) {
          <p class="state state-empty">Nenhum centro de custo para mostrar.</p>
        }
        <section class="budgets">
          @for (budget of data.budgets; track budget.costCenterId) {
            <mat-card appearance="outlined">
              <mat-card-header>
                <mat-card-title>{{ budget.costCenterCode }}</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <mat-progress-bar
                  mode="determinate"
                  [value]="usage(budget)"
                  [attr.aria-label]="'Consumido ' + usage(budget) + '%'"
                />
                <dl class="budget-figures">
                  <div>
                    <dt>Orçado</dt>
                    <dd>{{ budget.amount | money }}</dd>
                  </div>
                  <div>
                    <dt>Consumido</dt>
                    <dd>{{ budget.consumed | money }}</dd>
                  </div>
                  <div>
                    <dt>Disponível</dt>
                    <dd [class.low]="usage(budget) >= 90">{{ budget.available | money }}</dd>
                  </div>
                </dl>
              </mat-card-content>
            </mat-card>
          }
        </section>
      }
    </app-page-state>
  `,
  styles: `
    .totals {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px;
      margin-bottom: 32px;
    }
    .total-card {
      display: flex;
      flex-direction: column;
      padding: 16px;
      border-radius: 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-left: 6px solid var(--status-color, var(--mat-sys-outline));
      color: inherit;
      text-decoration: none;
      background: var(--mat-sys-surface-container-low);
    }
    .total-card:hover {
      background: var(--mat-sys-surface-container);
    }
    .total-value {
      font-size: 32px;
      font-weight: 500;
    }
    .budgets {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 16px;
    }
    .budget-figures {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
      margin: 16px 0 0;
    }
    dt {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
      font-weight: 500;
    }
    dd.low {
      color: var(--mat-sys-error);
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);

  private readonly auth = inject(AuthService);

  protected readonly statuses = STATUSES;

  protected readonly statusLabels = STATUS_LABELS;

  protected readonly summary = signal<DashboardSummary | null>(null);

  protected readonly loading = signal(false);

  protected readonly error = signal<string | null>(null);

  /** The API already scopes the totals by role; this only tells the user what they are looking at. */
  protected readonly scope = computed(() => {
    switch (this.auth.user()?.role) {
      case 'ADMIN':
        return 'Todas as solicitações e todos os centros de custo.';
      case 'APROVADOR':
        return 'Solicitações do seu centro de custo.';
      default:
        return 'Suas solicitações e o orçamento do seu centro de custo.';
    }
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(problemMessage(toProblem(error)));
        this.loading.set(false);
      },
    });
  }

  protected usage(budget: Budget): number {
    const amount = Number(budget.amount);
    return amount > 0 ? Math.min(100, Math.round((Number(budget.consumed) / amount) * 100)) : 0;
  }
}
