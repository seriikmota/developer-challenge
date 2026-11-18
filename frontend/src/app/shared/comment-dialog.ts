import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export const MIN_COMMENT_LENGTH = 10;

export interface CommentDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  /** Rejecting and requesting changes require a reason; approving and cancelling accept an optional one. */
  commentRequired: boolean;
}

/** Same rule as the API: surrounding blanks do not count. */
export function trimmedMinLength(min: number) {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = (control.value ?? '').trim().length;
    return length >= min ? null : { minlength: { requiredLength: min, actualLength: length } };
  };
}

/**
 * Confirmation of a decision with a comment. Closes with the comment (possibly empty) when confirmed and with
 * undefined when dismissed (Voltar, Esc or a click outside), so the caller never mistakes a dismissal for a
 * confirmation without comment.
 */
@Component({
  selector: 'app-comment-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ data.commentRequired ? 'Motivo' : 'Comentário (opcional)' }}</mat-label>
        <textarea matInput [formControl]="comment" rows="4" maxlength="2000"></textarea>
        @if (data.commentRequired) {
          <mat-hint>Mínimo de {{ min }} caracteres</mat-hint>
        }
        @if (comment.invalid) {
          <mat-error>Explique o motivo com pelo menos {{ min }} caracteres.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="cancel()">Voltar</button>
      <button matButton="filled" type="button" (click)="confirm()">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
})
export class CommentDialog {
  protected readonly data = inject<CommentDialogData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject<MatDialogRef<CommentDialog, string>>(MatDialogRef);

  protected readonly min = MIN_COMMENT_LENGTH;

  protected readonly comment = new FormControl('', {
    nonNullable: true,
    validators: this.data.commentRequired
      ? [Validators.required, trimmedMinLength(MIN_COMMENT_LENGTH)]
      : [],
  });

  protected cancel(): void {
    this.dialogRef.close(undefined);
  }

  protected confirm(): void {
    if (this.comment.invalid) {
      this.comment.markAsTouched();
      return;
    }
    this.dialogRef.close(this.comment.value.trim());
  }
}
