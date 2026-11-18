import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { map, startWith } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import {
  CATEGORIES,
  Category,
  CostCenter,
  RequestDetail,
  RequestPayload,
} from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { Notifier } from '../../core/http/notifier.service';
import { problemMessage, toProblem } from '../../core/http/problem';
import { CATEGORY_LABELS } from '../../shared/labels';
import { formatMoney } from '../../shared/money.pipe';
import { PageState } from '../../shared/page-state';
import { applyServerErrors } from '../../shared/server-errors';

const EDIT_CONFLICT_MESSAGE =
  'Outra pessoa alterou esta solicitação enquanto você editava. Recarregue os dados atuais e refaça suas alterações.';

const MONEY_PATTERN = /^\d{1,10}([.,]\d{1,2})?$/;

type ItemForm = FormGroup<{
  description: FormControl<string>;
  quantity: FormControl<number | null>;
  unitPrice: FormControl<string>;
}>;

/** "1.234,5" or "1234.5" → cents; null when the text is not an amount yet. */
export function toCents(text: string): number | null {
  const normalized = text
    .trim()
    .replace(/\.(?=\d{3}(\D|$))/g, '')
    .replace(',', '.');
  if (!MONEY_PATTERN.test(normalized)) {
    return null;
  }
  return Math.round(Number(normalized) * 100);
}

function positiveAmount(control: AbstractControl<string>): ValidationErrors | null {
  return (toCents(control.value ?? '') ?? 0) > 0 ? null : { money: true };
}

/**
 * Create and edit. The total shown is only a preview, computed in cents to avoid floating point errors: the
 * API recalculates it and its value is the one saved.
 */
@Component({
  selector: 'app-request-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageState,
  ],
  template: `
    <header class="page-header">
      <h1>{{ id() ? 'Editar solicitação #' + id() : 'Nova solicitação' }}</h1>
    </header>

    <app-page-state [loading]="loading()" [error]="loadError()" (retry)="load()">
      @if (!loading()) {
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          @if (formErrors().length > 0) {
            <div class="form-error" role="alert">
              @for (message of formErrors(); track $index) {
                <p>{{ message }}</p>
              }
              @if (conflict()) {
                <button matButton="outlined" type="button" (click)="load()">
                  Recarregar dados atuais
                </button>
              }
            </div>
          }

          <mat-card appearance="outlined">
            <mat-card-content class="grid">
              <mat-form-field class="span-2">
                <mat-label>Título</mat-label>
                <input matInput formControlName="title" maxlength="150" />
                @if (form.controls.title.hasError('required')) {
                  <mat-error>Informe o título.</mat-error>
                }
                @if (form.controls.title.getError('server'); as message) {
                  <mat-error>{{ message }}</mat-error>
                }
              </mat-form-field>
              <mat-form-field>
                <mat-label>Categoria</mat-label>
                <mat-select formControlName="category">
                  @for (category of categories; track category) {
                    <mat-option [value]="category">{{ categoryLabels[category] }}</mat-option>
                  }
                </mat-select>
                @if (form.controls.category.hasError('required')) {
                  <mat-error>Escolha a categoria.</mat-error>
                }
                @if (form.controls.category.getError('server'); as message) {
                  <mat-error>{{ message }}</mat-error>
                }
              </mat-form-field>
              <mat-form-field>
                <mat-label>Necessário até</mat-label>
                <input matInput type="date" formControlName="neededBy" />
                @if (form.controls.neededBy.getError('server'); as message) {
                  <mat-error>{{ message }}</mat-error>
                }
              </mat-form-field>
              @if (chooseCostCenter()) {
                <mat-form-field>
                  <mat-label>Centro de custo</mat-label>
                  <mat-select formControlName="costCenterId">
                    @for (costCenter of costCenters(); track costCenter.id) {
                      <mat-option [value]="costCenter.id">
                        {{ costCenter.code }} — {{ costCenter.name }}
                      </mat-option>
                    }
                  </mat-select>
                  @if (form.controls.costCenterId.getError('server'); as message) {
                    <mat-error>{{ message }}</mat-error>
                  }
                </mat-form-field>
              }
              <mat-form-field class="span-all">
                <mat-label>Descrição</mat-label>
                <textarea
                  matInput
                  formControlName="description"
                  rows="3"
                  maxlength="2000"
                ></textarea>
                @if (form.controls.description.getError('server'); as message) {
                  <mat-error>{{ message }}</mat-error>
                }
              </mat-form-field>
            </mat-card-content>
          </mat-card>

          <h2>Itens</h2>
          <div formArrayName="items" class="items">
            @for (item of items.controls; track item; let index = $index) {
              <div class="item-row" [formGroupName]="index" [attr.data-testid]="'item-' + index">
                <mat-form-field class="item-description">
                  <mat-label>Descrição do item {{ index + 1 }}</mat-label>
                  <input matInput formControlName="description" maxlength="300" />
                  @if (item.controls.description.hasError('required')) {
                    <mat-error>Informe a descrição.</mat-error>
                  }
                  @if (item.controls.description.getError('server'); as message) {
                    <mat-error>{{ message }}</mat-error>
                  }
                </mat-form-field>
                <mat-form-field class="item-quantity">
                  <mat-label>Quantidade</mat-label>
                  <input matInput type="number" min="1" step="1" formControlName="quantity" />
                  @if (
                    item.controls.quantity.invalid && !item.controls.quantity.hasError('server')
                  ) {
                    <mat-error>Inteiro maior que zero.</mat-error>
                  }
                  @if (item.controls.quantity.getError('server'); as message) {
                    <mat-error>{{ message }}</mat-error>
                  }
                </mat-form-field>
                <mat-form-field class="item-price">
                  <mat-label>Preço unitário (R$)</mat-label>
                  <input matInput inputmode="decimal" formControlName="unitPrice" />
                  @if (
                    item.controls.unitPrice.invalid && !item.controls.unitPrice.hasError('server')
                  ) {
                    <mat-error>Valor como 1234,56.</mat-error>
                  }
                  @if (item.controls.unitPrice.getError('server'); as message) {
                    <mat-error>{{ message }}</mat-error>
                  }
                </mat-form-field>
                <output class="item-total" [attr.aria-label]="'Total do item ' + (index + 1)">
                  {{ lineTotals()[index] }}
                </output>
                <button
                  matIconButton
                  type="button"
                  [attr.aria-label]="'Remover item ' + (index + 1)"
                  [disabled]="items.length === 1"
                  (click)="removeItem(index)"
                >
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            }
          </div>
          @if (form.controls.items.getError('server'); as message) {
            <p class="form-error">{{ message }}</p>
          }
          <button matButton="outlined" type="button" (click)="addItem()">
            <mat-icon aria-hidden="true">add</mat-icon> Adicionar item
          </button>

          <div class="footer">
            <p class="total">
              Total estimado: <strong data-testid="total">{{ total() }}</strong>
              <small>O valor final é calculado pelo servidor.</small>
            </p>
            <div class="footer-actions">
              <a matButton [routerLink]="id() ? ['/requests', id()] : ['/requests']">Voltar</a>
              <button matButton="filled" type="submit" [disabled]="saving()">
                {{ id() ? 'Salvar alterações' : 'Criar rascunho' }}
              </button>
            </div>
          </div>
        </form>
      }
    </app-page-state>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 0 12px;
      padding-top: 16px;
    }
    .span-2 {
      grid-column: span 2;
    }
    .span-all {
      grid-column: 1 / -1;
    }
    .items {
      display: flex;
      flex-direction: column;
    }
    .item-row {
      display: grid;
      grid-template-columns: minmax(200px, 3fr) minmax(90px, 1fr) minmax(130px, 1.3fr) 130px 48px;
      gap: 0 12px;
      align-items: start;
    }
    .item-total {
      padding-top: 16px;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .footer {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
    .total {
      display: flex;
      flex-direction: column;
      margin: 0;
      font-size: 18px;
    }
    .total small {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .footer-actions {
      display: flex;
      gap: 8px;
    }
    @media (max-width: 720px) {
      .item-row {
        grid-template-columns: 1fr 1fr;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        margin-bottom: 8px;
      }
      .item-description {
        grid-column: 1 / -1;
      }
      .span-2 {
        grid-column: auto;
      }
    }
  `,
})
export class RequestFormPage implements OnInit {
  /** Route parameter: present when editing. */
  readonly id = input<string>();

  private readonly api = inject(ApiService);

  private readonly auth = inject(AuthService);

  private readonly router = inject(Router);

  private readonly notifier = inject(Notifier);

  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly categories = CATEGORIES;

  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly loading = signal(false);

  protected readonly loadError = signal<string | null>(null);

  protected readonly saving = signal(false);

  protected readonly formErrors = signal<string[]>([]);

  protected readonly conflict = signal(false);

  protected readonly costCenters = signal<CostCenter[]>([]);

  /** A global ADMIN has no cost center of their own, so they pick one; everyone else uses theirs. */
  protected readonly chooseCostCenter = computed(() => this.auth.hasRole('ADMIN'));

  /** One key per form: a double click or a retried submit returns the same request instead of a copy. */
  private idempotencyKey = crypto.randomUUID();

  private lockVersion = 0;

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    category: this.fb.control<Category | ''>('', Validators.required),
    neededBy: [''],
    costCenterId: this.fb.control<number | null>(null),
    items: this.fb.array<ItemForm>([]),
  });

  protected get items(): FormArray<ItemForm> {
    return this.form.controls.items;
  }

  private readonly itemValues = toSignal(
    this.items.valueChanges.pipe(
      startWith(null),
      map(() => this.items.getRawValue()),
    ),
    { initialValue: [] },
  );

  private readonly lineCents = computed(() =>
    this.itemValues().map((item) => {
      const cents = toCents(item.unitPrice ?? '');
      const quantity = item.quantity;
      return cents !== null && quantity !== null && Number.isInteger(quantity) && quantity > 0
        ? cents * quantity
        : null;
    }),
  );

  protected readonly lineTotals = computed(() =>
    this.lineCents().map((cents) => (cents === null ? '—' : formatMoney(cents / 100))),
  );

  protected readonly total = computed(() =>
    formatMoney(this.lineCents().reduce<number>((sum, cents) => sum + (cents ?? 0), 0) / 100),
  );

  ngOnInit(): void {
    if (this.chooseCostCenter()) {
      this.form.controls.costCenterId.addValidators(Validators.required);
      this.api.costCenters().subscribe((costCenters) => this.costCenters.set(costCenters));
    }
    if (this.id()) {
      this.load();
    } else {
      this.addItem();
    }
  }

  load(): void {
    const id = Number(this.id());
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getRequest(id).subscribe({
      next: (request) => {
        this.fill(request);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(problemMessage(toProblem(error)));
        this.loading.set(false);
      },
    });
  }

  protected addItem(item?: { description: string; quantity: number; unitPrice: string }): void {
    this.items.push(
      this.fb.group({
        description: [item?.description ?? '', [Validators.required, Validators.maxLength(300)]],
        quantity: this.fb.control<number | null>(item?.quantity ?? 1, [
          Validators.required,
          Validators.min(1),
          Validators.pattern(/^\d+$/),
        ]),
        unitPrice: [
          item ? item.unitPrice.replace('.', ',') : '',
          [Validators.required, positiveAmount],
        ],
      }),
    );
  }

  protected removeItem(index: number): void {
    this.items.removeAt(index);
  }

  protected save(): void {
    this.formErrors.set([]);
    this.conflict.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formErrors.set(['Corrija os campos destacados.']);
      return;
    }
    this.saving.set(true);
    const id = this.id();
    const request$ = id
      ? this.api.updateRequest(Number(id), { ...this.payload(), lockVersion: this.lockVersion })
      : this.api.createRequest(this.payload(), this.idempotencyKey);
    request$.subscribe({
      next: (request) => {
        this.notifier.success(id ? 'Alterações salvas.' : 'Rascunho criado.');
        void this.router.navigate(['/requests', request.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const problem = toProblem(error);
        if (problem.status === 400) {
          const unmatched = applyServerErrors(this.form, problem.errors);
          this.formErrors.set([problem.detail ?? 'Corrija os campos destacados.', ...unmatched]);
        } else if (problem.status === 409) {
          this.conflict.set(true);
          this.formErrors.set([EDIT_CONFLICT_MESSAGE]);
        } else {
          this.formErrors.set([problemMessage(problem)]);
        }
      },
    });
  }

  private fill(request: RequestDetail): void {
    this.lockVersion = request.lockVersion;
    if (!request.allowedActions.includes('EDIT')) {
      this.loadError.set('Esta solicitação não pode ser editada no estado atual.');
      return;
    }
    const version = request.version;
    this.form.patchValue({
      title: version.title,
      description: version.description ?? '',
      category: version.category,
      neededBy: version.neededBy ?? '',
      costCenterId: request.costCenterId,
    });
    this.items.clear();
    for (const item of version.items) {
      this.addItem(item);
    }
    this.form.markAsPristine();
  }

  private payload(): RequestPayload {
    const value = this.form.getRawValue();
    return {
      title: value.title.trim(),
      description: value.description.trim() || null,
      category: value.category as Category,
      neededBy: value.neededBy || null,
      costCenterId: this.chooseCostCenter() ? value.costCenterId : null,
      items: value.items.map((item) => ({
        description: item.description.trim(),
        quantity: Number(item.quantity),
        unitPrice: ((toCents(item.unitPrice) ?? 0) / 100).toFixed(2),
      })),
    };
  }
}
