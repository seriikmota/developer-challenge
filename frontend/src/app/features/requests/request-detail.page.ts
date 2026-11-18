import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, TransitionAction } from '../../core/api/api.service';
import {
  Action,
  RequestDetail,
  RequestEvent,
  Version,
  VersionSummary,
} from '../../core/api/models';
import { Notifier } from '../../core/http/notifier.service';
import { problemMessage, toProblem } from '../../core/http/problem';
import { CommentDialog, CommentDialogData } from '../../shared/comment-dialog';
import {
  ACTION_LABELS,
  CATEGORY_LABELS,
  EVENT_LABELS,
  LEVEL_LABELS,
  ROLE_LABELS,
  STATUS_LABELS,
  STEP_STATUS_LABELS,
  outcomeLabel,
} from '../../shared/labels';
import { DATE_TIME, DATE, MonthPipe } from '../../shared/date-formats';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageState } from '../../shared/page-state';
import { RequestActions } from '../../shared/request-actions';
import { StatusChip } from '../../shared/status-chip';

const SUCCESS_MESSAGES: Record<TransitionAction, string> = {
  SUBMIT: 'Solicitação enviada para aprovação.',
  APPROVE: 'Etapa aprovada.',
  REJECT: 'Solicitação rejeitada.',
  REQUEST_CHANGES: 'Solicitação devolvida para ajuste.',
  CANCEL: 'Solicitação cancelada.',
};

/** How each action is confirmed. SUBMIT goes straight through; decisions and cancel open the dialog. */
const CONFIRMATIONS: Partial<Record<Action, Omit<CommentDialogData, 'confirmLabel'>>> = {
  APPROVE: {
    title: 'Aprovar etapa',
    message: 'Confirma a aprovação da etapa atual?',
    commentRequired: false,
  },
  REJECT: {
    title: 'Rejeitar solicitação',
    message: 'A rejeição encerra a solicitação. Explique o motivo para o solicitante.',
    commentRequired: true,
  },
  REQUEST_CHANGES: {
    title: 'Devolver para ajuste',
    message: 'O solicitante poderá editar e reenviar, gerando uma nova versão.',
    commentRequired: true,
  },
  CANCEL: {
    title: 'Cancelar solicitação',
    message: 'A solicitação será cancelada e não poderá ser reaberta.',
    commentRequired: false,
  },
};

@Component({
  selector: 'app-request-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MoneyPipe,
    MonthPipe,
    StatusChip,
    PageState,
    RequestActions,
  ],
  template: `
    <a matButton routerLink="/requests"
      ><mat-icon aria-hidden="true">arrow_back</mat-icon> Solicitações</a
    >

    @if (notFound()) {
      <div class="state state-empty" role="alert">
        <mat-icon aria-hidden="true">search_off</mat-icon>
        <p>Solicitação não encontrada, ou você não tem acesso a ela.</p>
      </div>
    } @else {
      <app-page-state [loading]="loading()" [error]="error()" (retry)="load()">
        @if (request(); as request) {
          <header class="page-header">
            <div>
              <h1>#{{ request.id }} · {{ request.version.title }}</h1>
              <p class="subtitle">
                <app-status-chip [status]="request.status" />
                · {{ request.ownerName }} · criada em {{ request.createdAt | date: dateTime }}
              </p>
            </div>
            <app-request-actions
              [allowed]="request.allowedActions"
              [busy]="acting()"
              (selected)="run($event)"
            />
          </header>

          @if (shown(); as version) {
            @if (version.number !== request.currentVersion) {
              <p class="banner" role="status">
                Você está vendo a versão {{ version.number }}, que não muda mais.
                <button matButton type="button" (click)="showVersion(request.currentVersion)">
                  Ver versão atual
                </button>
              </p>
            }

            <div class="layout">
              <div class="main">
                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Dados · v{{ version.number }}</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <dl class="facts">
                      <div>
                        <dt>Categoria</dt>
                        <dd>{{ categoryLabels[version.category] }}</dd>
                      </div>
                      <div>
                        <dt>Total</dt>
                        <dd class="strong">{{ version.total | money }}</dd>
                      </div>
                      <div>
                        <dt>Necessário até</dt>
                        <dd>
                          {{ version.neededBy ? (version.neededBy | date: dateOnly : 'UTC') : '—' }}
                        </dd>
                      </div>
                      <div>
                        <dt>Enviada em</dt>
                        <dd>
                          {{
                            version.submittedAt
                              ? (version.submittedAt | date: dateTime)
                              : 'Não enviada'
                          }}
                        </dd>
                      </div>
                      <div>
                        <dt>Mês de competência</dt>
                        <dd>{{ version.competenceMonth | month }}</dd>
                      </div>
                    </dl>
                    @if (version.description) {
                      <p class="description">{{ version.description }}</p>
                    }
                    <div class="items-scroll">
                      <table mat-table [dataSource]="version.items" class="items">
                        <ng-container matColumnDef="description">
                          <th mat-header-cell *matHeaderCellDef>Item</th>
                          <td mat-cell *matCellDef="let item">{{ item.description }}</td>
                        </ng-container>
                        <ng-container matColumnDef="quantity">
                          <th mat-header-cell *matHeaderCellDef class="numeric">Qtd.</th>
                          <td mat-cell *matCellDef="let item" class="numeric">
                            {{ item.quantity }}
                          </td>
                        </ng-container>
                        <ng-container matColumnDef="unitPrice">
                          <th mat-header-cell *matHeaderCellDef class="numeric">Unitário</th>
                          <td mat-cell *matCellDef="let item" class="numeric">
                            {{ item.unitPrice | money }}
                          </td>
                        </ng-container>
                        <ng-container matColumnDef="lineTotal">
                          <th mat-header-cell *matHeaderCellDef class="numeric">Total</th>
                          <td mat-cell *matCellDef="let item" class="numeric">
                            {{ item.lineTotal | money }}
                          </td>
                        </ng-container>
                        <tr mat-header-row *matHeaderRowDef="itemColumns"></tr>
                        <tr mat-row *matRowDef="let row; columns: itemColumns"></tr>
                      </table>
                    </div>
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-header>
                    <mat-card-title>Cadeia de aprovação · v{{ version.number }}</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    @if (version.steps.length === 0) {
                      <p class="muted">
                        A cadeia é montada quando a solicitação é enviada para aprovação.
                      </p>
                    } @else {
                      <ol class="chain">
                        @for (step of version.steps; track step.order) {
                          <li [class]="'step step-' + step.status" [class.current]="step.current">
                            <span class="step-title">
                              {{ step.order }}. {{ levelLabels[step.level] }}
                              <small>{{
                                step.current ? 'Etapa atual' : stepLabels[step.status]
                              }}</small>
                            </span>
                            @if (step.decidedByName) {
                              <span class="muted">
                                {{ step.decidedByName }} · {{ step.decidedAt | date: dateTime }}
                              </span>
                            }
                            @if (step.comment) {
                              <q>{{ step.comment }}</q>
                            }
                          </li>
                        }
                      </ol>
                    }
                  </mat-card-content>
                </mat-card>
              </div>

              <aside class="side">
                <mat-card appearance="outlined">
                  <mat-card-header><mat-card-title>Versões</mat-card-title></mat-card-header>
                  <mat-card-content>
                    <ul class="versions">
                      @for (summary of versions(); track summary.number) {
                        <li>
                          <button
                            matButton
                            type="button"
                            [class.selected]="summary.number === version.number"
                            [attr.aria-current]="summary.number === version.number"
                            (click)="showVersion(summary.number)"
                          >
                            v{{ summary.number }} · {{ summary.total | money }}
                          </button>
                          <small class="muted">{{ outcome(summary.outcome) }}</small>
                        </li>
                      }
                    </ul>
                  </mat-card-content>
                </mat-card>

                <mat-card appearance="outlined">
                  <mat-card-header><mat-card-title>Histórico</mat-card-title></mat-card-header>
                  <mat-card-content>
                    <ol class="timeline">
                      @for (event of history(); track event.id) {
                        <li>
                          <strong>{{ eventLabels[event.action] }}</strong>
                          @if (event.stepOrder) {
                            <span> a etapa {{ event.stepOrder }}</span>
                          }
                          <span class="muted"> · v{{ event.versionNumber }}</span>
                          <div class="muted">
                            {{ event.actorName }} ({{ roleLabels[event.actorRole] }}) ·
                            {{ event.occurredAt | date: dateTime }}
                          </div>
                          @if (event.fromStatus && event.fromStatus !== event.toStatus) {
                            <div class="muted">
                              {{ statusLabels[event.fromStatus] }} →
                              {{ statusLabels[event.toStatus] }}
                            </div>
                          }
                          @if (event.comment) {
                            <q>{{ event.comment }}</q>
                          }
                        </li>
                      }
                    </ol>
                  </mat-card-content>
                </mat-card>
              </aside>
            </div>
          }
        }
      </app-page-state>
    }
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
      gap: 16px;
    }
    .main,
    .side {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    @media (max-width: 900px) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: 12px;
      margin: 16px 0;
    }
    dt {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
    }
    .strong {
      font-weight: 500;
    }
    .description {
      white-space: pre-line;
    }
    .items-scroll {
      overflow-x: auto;
    }
    .main,
    .side,
    mat-card {
      min-width: 0;
    }
    .chain,
    .timeline,
    .versions {
      list-style: none;
      padding: 0;
      margin: 8px 0 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .step {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 12px;
      border-left: 4px solid var(--mat-sys-outline-variant);
    }
    .step.current {
      border-left-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
    .step-APPROVED {
      border-left-color: #2e7d32;
    }
    .step-REJECTED {
      border-left-color: var(--mat-sys-error);
    }
    .step-CHANGES_REQUESTED {
      border-left-color: #ef6c00;
    }
    .step-title small {
      margin-left: 8px;
      color: var(--mat-sys-on-surface-variant);
    }
    .timeline li {
      padding-left: 12px;
      border-left: 2px solid var(--mat-sys-outline-variant);
    }
    .versions li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .versions .selected {
      background: var(--mat-sys-secondary-container);
    }
    q {
      display: block;
      font-style: italic;
      margin-top: 4px;
    }
    .banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      border-radius: 8px;
      background: var(--mat-sys-tertiary-container);
    }
  `,
})
export class RequestDetailPage implements OnInit {
  protected readonly dateTime = DATE_TIME;

  protected readonly dateOnly = DATE;
  /** Route parameter. */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);

  private readonly dialog = inject(MatDialog);

  private readonly notifier = inject(Notifier);

  private readonly router = inject(Router);

  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly levelLabels = LEVEL_LABELS;

  protected readonly stepLabels = STEP_STATUS_LABELS;

  protected readonly eventLabels = EVENT_LABELS;

  protected readonly roleLabels = ROLE_LABELS;

  protected readonly statusLabels = STATUS_LABELS;

  protected readonly itemColumns = ['description', 'quantity', 'unitPrice', 'lineTotal'];

  protected readonly request = signal<RequestDetail | null>(null);

  protected readonly versions = signal<VersionSummary[]>([]);

  protected readonly history = signal<RequestEvent[]>([]);

  /** A past version picked in the side list; null shows the current one. */
  private readonly pastVersion = signal<Version | null>(null);

  protected readonly shown = computed(() => this.pastVersion() ?? this.request()?.version ?? null);

  protected readonly loading = signal(false);

  protected readonly error = signal<string | null>(null);

  protected readonly notFound = signal(false);

  protected readonly acting = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.id());
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      request: this.api.getRequest(id),
      versions: this.api.versions(id),
      history: this.api.history(id),
    }).subscribe({
      next: ({ request, versions, history }) => {
        this.request.set(request);
        this.versions.set(versions);
        this.history.set(history);
        this.pastVersion.set(null);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        const problem = toProblem(error);
        this.notFound.set(problem.status === 404);
        this.error.set(problemMessage(problem));
        this.loading.set(false);
      },
    });
  }

  protected showVersion(number: number): void {
    const request = this.request();
    if (!request) {
      return;
    }
    if (number === request.currentVersion) {
      this.pastVersion.set(null);
      return;
    }
    this.api.version(request.id, number).subscribe((version) => this.pastVersion.set(version));
  }

  protected outcome(value: string): string {
    return outcomeLabel(value);
  }

  protected run(action: Action): void {
    const request = this.request();
    if (!request) {
      return;
    }
    if (action === 'EDIT') {
      void this.router.navigate(['/requests', request.id, 'edit']);
      return;
    }
    const confirmation = CONFIRMATIONS[action];
    if (!confirmation) {
      this.transition(request, action, null);
      return;
    }
    this.dialog
      .open<CommentDialog, CommentDialogData, string>(CommentDialog, {
        data: { ...confirmation, confirmLabel: ACTION_LABELS[action] },
        width: '480px',
      })
      .afterClosed()
      .subscribe((comment) => {
        if (comment !== undefined) {
          this.transition(request, action, comment);
        }
      });
  }

  /**
   * Sends the lockVersion read with the page. 409: someone acted first, so the page reloads with the current
   * state (the interceptor already told the user). 422: the API explains the rule; the page reloads too,
   * because allowedActions may have changed.
   */
  private transition(
    request: RequestDetail,
    action: TransitionAction,
    comment: string | null,
  ): void {
    this.acting.set(true);
    this.api.transition(request.id, action, request.lockVersion, comment).subscribe({
      next: (updated) => {
        this.acting.set(false);
        this.notifier.success(
          action === 'APPROVE' && updated.status === 'APPROVED'
            ? 'Solicitação aprovada: orçamento consumido.'
            : SUCCESS_MESSAGES[action],
        );
        this.load();
      },
      error: (error: unknown) => {
        this.acting.set(false);
        const status = toProblem(error).status;
        if (status === 409 || status === 422) {
          this.load();
        }
      },
    });
  }
}
