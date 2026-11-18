import {
  Action,
  Category,
  EventAction,
  RequestStatus,
  Role,
  StepLevel,
  StepStatus,
} from '../core/api/models';

export const STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovação',
  CHANGES_REQUESTED: 'Devolvida para ajuste',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
};

export const CATEGORY_LABELS: Record<Category, string> = {
  EQUIPMENT: 'Equipamentos',
  SERVICES: 'Serviços',
  SUPPLIES: 'Suprimentos',
  TRAVEL: 'Viagens',
  SOFTWARE: 'Software',
  OTHER: 'Outros',
};

export const ACTION_LABELS: Record<Action, string> = {
  EDIT: 'Editar',
  SUBMIT: 'Enviar para aprovação',
  APPROVE: 'Aprovar',
  REJECT: 'Rejeitar',
  REQUEST_CHANGES: 'Devolver para ajuste',
  CANCEL: 'Cancelar solicitação',
};

export const EVENT_LABELS: Record<EventAction, string> = {
  CREATE: 'Criou',
  EDIT: 'Editou',
  SUBMIT: 'Enviou para aprovação',
  APPROVE: 'Aprovou',
  REJECT: 'Rejeitou',
  REQUEST_CHANGES: 'Devolveu para ajuste',
  CANCEL: 'Cancelou',
};

export const LEVEL_LABELS: Record<StepLevel, string> = {
  NIVEL_1: 'Nível 1',
  NIVEL_2: 'Nível 2',
  ADMIN: 'Administração',
};

export const STEP_STATUS_LABELS: Record<StepStatus, string> = {
  PENDING: 'Pendente',
  APPROVED: 'Aprovada',
  REJECTED: 'Rejeitada',
  CHANGES_REQUESTED: 'Devolvida',
};

export const ROLE_LABELS: Record<Role, string> = {
  SOLICITANTE: 'Solicitante',
  APROVADOR: 'Aprovador',
  ADMIN: 'Administrador',
};

/** Outcome of a version in GET /versions: a step status or PENDING while the chain is running. */
export function outcomeLabel(outcome: string): string {
  return (
    STEP_STATUS_LABELS[outcome as StepStatus] ?? STATUS_LABELS[outcome as RequestStatus] ?? outcome
  );
}
