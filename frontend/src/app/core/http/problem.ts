import { HttpErrorResponse } from '@angular/common/http';

export interface FieldProblem {
  field: string;
  message: string;
}

/** Error body of the API (RFC 9457 problem details plus code, requestId and errors[]). */
export interface Problem {
  status: number;
  code: string | null;
  title: string | null;
  detail: string | null;
  requestId: string | null;
  errors: FieldProblem[];
}

export const CONFLICT_MESSAGE =
  'Outra pessoa agiu nesta solicitação antes de você. Os dados foram recarregados: confira e tente de novo.';

export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: -1,
      code: null,
      title: null,
      detail: String(error),
      requestId: null,
      errors: [],
    };
  }
  const body = typeof error.error === 'object' && error.error !== null ? error.error : {};
  return {
    status: error.status,
    code: body.code ?? null,
    title: body.title ?? null,
    detail: body.detail ?? null,
    requestId: body.requestId ?? error.headers?.get('X-Request-Id') ?? null,
    errors: Array.isArray(body.errors) ? body.errors : [],
  };
}

/** Message shown to the user for an API error. */
export function problemMessage(problem: Problem): string {
  switch (problem.status) {
    case 0:
      return 'Não foi possível falar com o servidor. Verifique sua conexão e tente de novo.';
    case 403:
      return 'Você não tem permissão para esta ação.';
    case 404:
      return 'Solicitação não encontrada.';
    case 409:
      return problem.code === 'CONCURRENT_MODIFICATION'
        ? CONFLICT_MESSAGE
        : (problem.detail ?? CONFLICT_MESSAGE);
    case 400:
    case 422:
      return problem.detail ?? 'A operação não pôde ser concluída.';
    default:
      return problem.status >= 500
        ? `Erro inesperado no servidor.${problem.requestId ? ` Código para o suporte: ${problem.requestId}.` : ''}`
        : (problem.detail ?? 'A operação não pôde ser concluída.');
  }
}
