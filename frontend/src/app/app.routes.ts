import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'requests',
        title: 'Solicitações',
        loadComponent: () =>
          import('./features/requests/request-list.page').then((m) => m.RequestListPage),
      },
      {
        path: 'requests/new',
        title: 'Nova solicitação',
        loadComponent: () =>
          import('./features/requests/request-form.page').then((m) => m.RequestFormPage),
      },
      {
        path: 'requests/:id',
        title: 'Solicitação',
        loadComponent: () =>
          import('./features/requests/request-detail.page').then((m) => m.RequestDetailPage),
      },
      {
        path: 'requests/:id/edit',
        title: 'Editar solicitação',
        loadComponent: () =>
          import('./features/requests/request-form.page').then((m) => m.RequestFormPage),
      },
      {
        path: 'approvals',
        title: 'Caixa de aprovação',
        canActivate: [roleGuard('APROVADOR', 'ADMIN')],
        loadComponent: () => import('./features/approvals/inbox.page').then((m) => m.InboxPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
