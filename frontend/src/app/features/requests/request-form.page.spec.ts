import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { render, screen, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, throwError } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { RequestFormPage, toCents } from './request-form.page';

function setup(api: Partial<ApiService> = {}) {
  const apiMock = { costCenters: vi.fn(() => of([])), createRequest: vi.fn(), ...api };
  return render(RequestFormPage, {
    providers: [
      provideRouter([]),
      provideHttpClient(),
      { provide: ApiService, useValue: apiMock },
      { provide: AuthService, useValue: { hasRole: () => false } },
    ],
  }).then((result) => ({ ...result, api: apiMock }));
}

describe('RequestFormPage', () => {
  it('parses Brazilian and plain amounts into cents', () => {
    expect(toCents('1.234,56')).toBe(123456);
    expect(toCents('1234.5')).toBe(123450);
    expect(toCents('0,1')).toBe(10);
    expect(toCents('abc')).toBeNull();
  });

  it('adds and removes items and updates the total as the user types', async () => {
    const user = userEvent.setup();
    await setup();
    const total = screen.getByTestId('total');
    expect(total.textContent).toContain('0,00');

    await user.type(screen.getByLabelText('Preço unitário (R$)'), '10,50');
    const quantity = screen.getByLabelText('Quantidade');
    await user.clear(quantity);
    await user.type(quantity, '3');
    expect(total.textContent).toContain('31,50');

    await user.click(screen.getByRole('button', { name: /Adicionar item/ }));
    const second = screen.getByTestId('item-1');
    await user.type(within(second).getByLabelText('Preço unitário (R$)'), '0,10');
    expect(total.textContent).toContain('31,60');

    await user.click(screen.getByRole('button', { name: 'Remover item 1' }));
    expect(screen.queryByTestId('item-1')).toBeNull();
    expect(total.textContent).toContain('0,10');
  });

  it('shows each server validation error next to its field', async () => {
    const user = userEvent.setup();
    const createRequest = vi.fn(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_ERROR',
              detail: 'A requisição tem 2 campo(s) inválido(s).',
              errors: [
                { field: 'title', message: 'o título já foi usado hoje' },
                { field: 'items[0].unitPrice', message: 'preço acima do permitido' },
              ],
            },
          }),
      ),
    );
    await setup({ createRequest } as Partial<ApiService>);

    await user.type(screen.getByLabelText('Título'), 'Cadeiras');
    await user.click(screen.getByLabelText('Categoria'));
    await user.click(await screen.findByRole('option', { name: 'Suprimentos' }));
    await user.type(screen.getByLabelText('Descrição do item 1'), 'Cadeira');
    await user.type(screen.getByLabelText('Preço unitário (R$)'), '999999');
    await user.click(screen.getByRole('button', { name: 'Criar rascunho' }));

    expect(createRequest).toHaveBeenCalledTimes(1);
    const [payload, key] = createRequest.mock.calls[0] as unknown as [
      { items: { unitPrice: string }[] },
      string,
    ];
    expect(payload.items[0].unitPrice).toBe('999999.00');
    expect(key).toMatch(/[0-9a-f-]{36}/);
    expect(await screen.findByText('o título já foi usado hoje')).toBeTruthy();
    expect(screen.getByText('preço acima do permitido')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('2 campo(s) inválido(s)');
  });
});
