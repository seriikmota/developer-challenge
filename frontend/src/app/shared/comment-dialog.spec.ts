import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';

import { CommentDialog, CommentDialogData } from './comment-dialog';

async function open(data: Partial<CommentDialogData> = {}) {
  const close = vi.fn();
  await render(CommentDialog, {
    providers: [
      {
        provide: MAT_DIALOG_DATA,
        useValue: {
          title: 'Aprovar etapa',
          message: 'Confirma?',
          confirmLabel: 'Aprovar',
          commentRequired: false,
          ...data,
        },
      },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  return close;
}

describe('CommentDialog', () => {
  it('Voltar dismisses without a result, so nothing is sent', async () => {
    const close = await open();

    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }));

    expect(close).toHaveBeenCalledWith(undefined);
  });

  it('confirms an approval with an empty optional comment', async () => {
    const close = await open();

    await userEvent.click(screen.getByRole('button', { name: 'Aprovar' }));

    expect(close).toHaveBeenCalledWith('');
  });

  it('requires a reason of at least 10 characters, ignoring surrounding blanks', async () => {
    const close = await open({ confirmLabel: 'Rejeitar', commentRequired: true });
    const reason = screen.getByLabelText(/Motivo/);

    await userEvent.type(reason, '   curto   ');
    await userEvent.click(screen.getByRole('button', { name: 'Rejeitar' }));
    expect(close).not.toHaveBeenCalled();
    expect(screen.getByText(/pelo menos 10 caracteres/)).toBeTruthy();

    await userEvent.type(reason, 'fora do orçamento');
    await userEvent.click(screen.getByRole('button', { name: 'Rejeitar' }));
    expect(close).toHaveBeenCalledWith('curto   fora do orçamento');
  });
});
