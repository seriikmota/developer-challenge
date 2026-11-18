import { AbstractControl } from '@angular/forms';

import { FieldProblem } from '../core/http/problem';

/**
 * Puts each field error of a 400 response on the matching form control, as a "server" error. API paths such
 * as "items[1].unitPrice" map to the control path items → 1 → unitPrice. Returns the messages that matched no
 * control, so the form can show them at the top.
 */
export function applyServerErrors(form: AbstractControl, errors: FieldProblem[]): string[] {
  const unmatched: string[] = [];
  for (const error of errors) {
    const path = error.field
      .replace(/\[(\d+)\]/g, '.$1')
      .split('.')
      .filter((part) => part !== '');
    const control = path.length > 0 ? form.get(path) : null;
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: error.message });
      control.markAsTouched();
    } else {
      unmatched.push(error.message);
    }
  }
  return unmatched;
}
