import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { Action } from '../core/api/models';
import { ACTION_LABELS } from './labels';

const ORDER: Action[] = ['APPROVE', 'REQUEST_CHANGES', 'REJECT', 'SUBMIT', 'EDIT', 'CANCEL'];

const ICONS: Record<Action, string> = {
  APPROVE: 'check_circle',
  REQUEST_CHANGES: 'undo',
  REJECT: 'block',
  SUBMIT: 'send',
  EDIT: 'edit',
  CANCEL: 'close',
};

/**
 * Buttons of the actions the API allows for the logged user (allowedActions). No permission rule lives here:
 * an action missing from the list is simply not rendered.
 */
@Component({
  selector: 'app-request-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule],
  template: `
    @for (action of actions(); track action) {
      <button
        type="button"
        [attr.data-action]="action"
        [disabled]="busy()"
        [class.danger]="action === 'REJECT' || action === 'CANCEL'"
        [matButton]="action === 'APPROVE' || action === 'SUBMIT' ? 'filled' : 'outlined'"
        (click)="selected.emit(action)"
      >
        <mat-icon aria-hidden="true">{{ icons[action] }}</mat-icon>
        {{ labels[action] }}
      </button>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class RequestActions {
  readonly allowed = input.required<Action[]>();

  readonly busy = input(false);

  readonly selected = output<Action>();

  protected readonly labels = ACTION_LABELS;

  protected readonly icons = ICONS;

  protected readonly actions = computed(() =>
    ORDER.filter((action) => this.allowed().includes(action)),
  );
}
