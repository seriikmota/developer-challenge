import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Page, RequestSummary } from '../../core/api/models';
import { problemMessage, toProblem } from '../../core/http/problem';
import { CATEGORY_LABELS } from '../../shared/labels';
import { DATE_TIME } from '../../shared/date-formats';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageState } from '../../shared/page-state';

/** Requests whose current step the logged user can approve now (the API applies every rule). */
@Component({
  selector: 'app-inbox-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, MatTableModule, MatPaginatorModule, MoneyPipe, PageState],
  template: `
    <h1>Caixa de aprovação</h1>
    <p class="subtitle">Solicitações aguardando a sua decisão na etapa atual.</p>

    <app-page-state
      [loading]="loading()"
      [error]="error()"
      [empty]="page()?.totalElements === 0"
      emptyMessage="Nenhuma solicitação aguardando você. Tudo em dia!"
      (retry)="load()"
    >
      @if (page(); as page) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="page.data">
            <ng-container matColumnDef="id">
              <th mat-header-cell *matHeaderCellDef>#</th>
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
            <ng-container matColumnDef="owner">
              <th mat-header-cell *matHeaderCellDef>Solicitante</th>
              <td mat-cell *matCellDef="let row">{{ row.ownerName }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef class="numeric">Total</th>
              <td mat-cell *matCellDef="let row" class="numeric">{{ row.total | money }}</td>
            </ng-container>
            <ng-container matColumnDef="updatedAt">
              <th mat-header-cell *matHeaderCellDef>Atualizada em</th>
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
          [pageSizeOptions]="[10, 20, 50]"
          (page)="changePage($event)"
        />
      }
    </app-page-state>
  `,
})
export class InboxPage {
  protected readonly dateTime = DATE_TIME;
  private readonly api = inject(ApiService);

  protected readonly categoryLabels: Record<string, string> = CATEGORY_LABELS;

  protected readonly columns = ['id', 'title', 'category', 'owner', 'total', 'updatedAt'];

  protected readonly page = signal<Page<RequestSummary> | null>(null);

  protected readonly loading = signal(false);

  protected readonly error = signal<string | null>(null);

  private pageIndex = 0;

  private pageSize = 20;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.inbox(this.pageIndex, this.pageSize).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(problemMessage(toProblem(error)));
        this.loading.set(false);
      },
    });
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }
}
