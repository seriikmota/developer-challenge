import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/**
 * Loading, error and empty states shared by every screen. The content is projected only when none of them
 * applies.
 */
@Component({
  selector: 'app-page-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressBarModule, MatButtonModule, MatIconModule],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Carregando" />
    }
    @if (error(); as message) {
      <div class="state state-error" role="alert">
        <mat-icon aria-hidden="true">error_outline</mat-icon>
        <p>{{ message }}</p>
        <button matButton="outlined" type="button" (click)="retry.emit()">Tentar de novo</button>
      </div>
    } @else if (empty() && !loading()) {
      <div class="state state-empty">
        <mat-icon aria-hidden="true">inbox</mat-icon>
        <p>{{ emptyMessage() }}</p>
      </div>
    } @else {
      <ng-content />
    }
  `,
})
export class PageState {
  readonly loading = input(false);

  readonly error = input<string | null>(null);

  readonly empty = input(false);

  readonly emptyMessage = input('Nada para mostrar.');

  readonly retry = output();
}
