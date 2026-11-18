import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { problemMessage, toProblem } from '../../core/http/problem';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  template: `
    <main class="login">
      <mat-card appearance="outlined">
        @if (loading()) {
          <mat-progress-bar mode="indeterminate" aria-label="Entrando" />
        }
        <mat-card-header>
          <mat-card-title>Solicitações de compra</mat-card-title>
          <mat-card-subtitle>Entre com seu e-mail e senha</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field class="full-width">
              <mat-label>E-mail</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
              @if (form.controls.email.invalid) {
                <mat-error>Informe um e-mail válido.</mat-error>
              }
            </mat-form-field>
            <mat-form-field class="full-width">
              <mat-label>Senha</mat-label>
              <input
                matInput
                type="password"
                formControlName="password"
                autocomplete="current-password"
              />
              @if (form.controls.password.invalid) {
                <mat-error>Informe a senha.</mat-error>
              }
            </mat-form-field>
            @if (error(); as message) {
              <p class="form-error" role="alert">{{ message }}</p>
            }
            <button matButton="filled" type="submit" class="full-width" [disabled]="loading()">
              Entrar
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .login {
      min-height: 100vh;
      box-sizing: border-box;
      display: grid;
      place-items: center;
      padding: 16px;
    }
    mat-card {
      width: min(400px, 100%);
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-top: 16px;
    }
  `,
})
export class LoginPage {
  /** Route the user tried to open before logging in (query parameter). */
  readonly returnUrl = input<string>();

  private readonly auth = inject(AuthService);

  private readonly router = inject(Router);

  protected readonly loading = signal(false);

  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => void this.router.navigateByUrl(this.safeReturnUrl()),
      error: (error: unknown) => {
        const problem = toProblem(error);
        this.error.set(
          problem.status === 401 ? 'E-mail ou senha inválidos.' : problemMessage(problem),
        );
        this.loading.set(false);
      },
    });
  }

  /** Only internal paths: an external returnUrl would turn the login into an open redirect. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login')
      ? url
      : '/dashboard';
  }
}
