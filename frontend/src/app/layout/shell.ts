import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { ROLE_LABELS } from '../shared/labels';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
  ],
  template: `
    <mat-toolbar class="toolbar">
      <a class="brand" routerLink="/dashboard">
        <mat-icon aria-hidden="true">shopping_cart</mat-icon>
        <span class="brand-label">Compras</span>
      </a>
      <nav aria-label="Principal">
        <a matButton routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
        <a matButton routerLink="/requests" routerLinkActive="active">Solicitações</a>
        @if (auth.hasRole('APROVADOR', 'ADMIN')) {
          <a matButton routerLink="/approvals" routerLinkActive="active">Caixa de aprovação</a>
        }
      </nav>
      <span class="spacer"></span>
      @if (auth.user(); as user) {
        <span class="user">
          <strong>{{ user.name }}</strong>
          <small>
            {{ roleLabels[user.role] }}
            @if (user.approverLevel) {
              · {{ user.approverLevel === 'NIVEL_1' ? 'Nível 1' : 'Nível 2' }}
            }
          </small>
        </span>
      }
      <button matIconButton type="button" aria-label="Sair" title="Sair" (click)="logout()">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: `
    .toolbar {
      gap: 8px;
      position: sticky;
      top: 0;
      z-index: 10;
      background: var(--mat-sys-surface-container);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-right: 16px;
      color: inherit;
      text-decoration: none;
      font-weight: 500;
    }
    nav a.active {
      background: var(--mat-sys-secondary-container);
    }
    .spacer {
      flex: 1;
    }
    .user {
      display: flex;
      flex-direction: column;
      line-height: 1.2;
      font-size: 14px;
      text-align: right;
    }
    .user small {
      color: var(--mat-sys-on-surface-variant);
    }
    .content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 16px 48px;
    }
    nav {
      display: flex;
      min-width: 0;
      overflow-x: auto;
      overflow-y: hidden;
      scrollbar-width: none;
    }
    /* phones: the menu keeps the room; the user name and role are hidden, logout stays */
    @media (max-width: 720px) {
      .toolbar {
        gap: 0;
        padding: 0 4px;
      }
      .brand {
        margin-right: 4px;
      }
      .brand-label,
      .user {
        display: none;
      }
      nav a {
        padding: 0 8px;
        white-space: nowrap;
      }
      .content {
        padding: 16px 12px 48px;
      }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);

  private readonly router = inject(Router);

  protected readonly roleLabels = ROLE_LABELS;

  protected logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
