import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RequestStatus } from '../core/api/models';
import { STATUS_LABELS } from './labels';

@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'status-chip status-' + status()">{{ label() }}</span>`,
})
export class StatusChip {
  readonly status = input.required<RequestStatus>();

  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}
