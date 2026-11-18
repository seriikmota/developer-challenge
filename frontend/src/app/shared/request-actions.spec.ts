import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';

import { Action } from '../core/api/models';
import { RequestActions } from './request-actions';

describe('RequestActions', () => {
  async function renderWith(allowed: Action[]) {
    const selected = vi.fn();
    await render(RequestActions, { inputs: { allowed }, on: { selected } });
    return selected;
  }

  it('renders only the actions the API allows', async () => {
    await renderWith(['APPROVE', 'REJECT', 'REQUEST_CHANGES']);

    const labels = screen.getAllByRole('button').map((button) => button.textContent?.trim());
    expect(labels).toEqual([
      expect.stringContaining('Aprovar'),
      expect.stringContaining('Devolver para ajuste'),
      expect.stringContaining('Rejeitar'),
    ]);
    expect(screen.queryByRole('button', { name: /Editar/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Cancelar/ })).toBeNull();
  });

  it('renders the owner actions of a draft', async () => {
    await renderWith(['EDIT', 'SUBMIT', 'CANCEL']);

    expect(screen.getByRole('button', { name: /Enviar para aprovação/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Editar/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Cancelar solicitação/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Aprovar/ })).toBeNull();
  });

  it('renders nothing when no action is allowed', async () => {
    await renderWith([]);

    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('emits the chosen action', async () => {
    const selected = await renderWith(['APPROVE']);

    await userEvent.click(screen.getByRole('button', { name: /Aprovar/ }));

    expect(selected).toHaveBeenCalledWith('APPROVE');
  });
});
