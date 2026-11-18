import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class Notifier {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000 });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Fechar', { duration: 8000, panelClass: 'snack-error' });
  }
}
