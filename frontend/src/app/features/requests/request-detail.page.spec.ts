import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, throwError } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import { RequestDetail } from '../../core/api/models';
import { Notifier } from '../../core/http/notifier.service';
import { RequestDetailPage } from './request-detail.page';

function detail(overrides: Partial<RequestDetail> = {}): RequestDetail {
  return {
    id: 7,
    status: 'PENDING_APPROVAL',
    ownerId: 1,
    ownerName: 'Ana Souza',
    costCenterId: 1,
    currentVersion: 1,
    lockVersion: 3,
    createdAt: '2026-11-10T12:00:00Z',
    updatedAt: '2026-11-10T12:00:00Z',
    versions: [1],
    allowedActions: ['SUBMIT'],
    version: {
      number: 1,
      title: 'Cadeiras',
      description: null,
      category: 'SUPPLIES',
      neededBy: null,
      total: '800.00',
      submittedAt: null,
      competenceMonth: null,
      items: [
        {
          position: 1,
          description: 'Cadeira',
          quantity: 4,
          unitPrice: '200.00',
          lineTotal: '800.00',
        },
      ],
      steps: [],
    },
    ...overrides,
  };
}

async function setup(transitionError: number) {
  const api = {
    getRequest: vi
      .fn()
      .mockReturnValueOnce(of(detail()))
      .mockReturnValue(
        of(detail({ status: 'PENDING_APPROVAL', lockVersion: 5, allowedActions: ['CANCEL'] })),
      ),
    versions: vi.fn(() => of([])),
    history: vi.fn(() => of([])),
    transition: vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: transitionError, error: { code: 'X' } })),
    ),
  };
  await render(RequestDetailPage, {
    inputs: { id: '7' },
    providers: [
      provideRouter([]),
      { provide: ApiService, useValue: api },
      { provide: Notifier, useValue: { error: vi.fn(), success: vi.fn() } },
    ],
  });
  return api;
}

describe('RequestDetailPage', () => {
  it('sends the lockVersion it read and reloads the current state after a 409', async () => {
    const api = await setup(409);

    await userEvent.click(await screen.findByRole('button', { name: /Enviar para aprovação/ }));

    expect(api.transition).toHaveBeenCalledWith(7, 'SUBMIT', 3, null);
    expect(api.getRequest).toHaveBeenCalledTimes(2);
    // the buttons now reflect the reloaded allowedActions
    expect(await screen.findByRole('button', { name: /Cancelar solicitação/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Enviar para aprovação/ })).toBeNull();
  });

  it('reloads after a 422 because the allowed actions may have changed', async () => {
    const api = await setup(422);

    await userEvent.click(await screen.findByRole('button', { name: /Enviar para aprovação/ }));

    expect(api.getRequest).toHaveBeenCalledTimes(2);
  });

  it('shows a not found state for a request outside the user visibility', async () => {
    const api = {
      getRequest: vi.fn(() =>
        throwError(() => new HttpErrorResponse({ status: 404, error: { code: 'NOT_FOUND' } })),
      ),
      versions: vi.fn(() => of([])),
      history: vi.fn(() => of([])),
    };
    await render(RequestDetailPage, {
      inputs: { id: '99' },
      providers: [provideRouter([]), { provide: ApiService, useValue: api }],
    });

    expect(await screen.findByText(/Solicitação não encontrada/)).toBeTruthy();
  });
});
