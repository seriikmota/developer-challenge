/**
 * Contracts of the back-end API. Money travels as a string with two decimals ("1234.50") and is never
 * recalculated here: the server owns every total.
 */

export type Role = 'SOLICITANTE' | 'APROVADOR' | 'ADMIN';
export type ApproverLevel = 'NIVEL_1' | 'NIVEL_2';
export type RequestStatus =
  'DRAFT' | 'PENDING_APPROVAL' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type Category = 'EQUIPMENT' | 'SERVICES' | 'SUPPLIES' | 'TRAVEL' | 'SOFTWARE' | 'OTHER';
export type Action = 'EDIT' | 'SUBMIT' | 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'CANCEL';
export type StepLevel = 'NIVEL_1' | 'NIVEL_2' | 'ADMIN';
export type StepStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
export type EventAction = 'CREATE' | Action;

export const STATUSES: RequestStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'CHANGES_REQUESTED',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
];
export const CATEGORIES: Category[] = [
  'EQUIPMENT',
  'SERVICES',
  'SUPPLIES',
  'TRAVEL',
  'SOFTWARE',
  'OTHER',
];

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  approverLevel: ApproverLevel | null;
  costCenterId: number | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RequestSummary {
  id: number;
  title: string;
  category: Category;
  status: RequestStatus;
  total: string;
  costCenterId: number;
  ownerId: number;
  ownerName: string;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface Item {
  position: number;
  description: string;
  quantity: number;
  unitPrice: string;
  lineTotal: string;
}

export interface Step {
  order: number;
  level: StepLevel;
  status: StepStatus;
  current: boolean;
  decidedBy: number | null;
  decidedByName: string | null;
  decidedAt: string | null;
  comment: string | null;
}

export interface Version {
  number: number;
  title: string;
  description: string | null;
  category: Category;
  neededBy: string | null;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  items: Item[];
  steps: Step[];
}

export interface VersionSummary {
  number: number;
  title: string;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  outcome: string;
}

export interface RequestDetail {
  id: number;
  status: RequestStatus;
  ownerId: number;
  ownerName: string;
  costCenterId: number;
  currentVersion: number;
  lockVersion: number;
  createdAt: string;
  updatedAt: string;
  version: Version;
  versions: number[];
  allowedActions: Action[];
}

export interface RequestEvent {
  id: number;
  versionNumber: number;
  stepOrder: number | null;
  actorId: number;
  actorName: string;
  actorRole: Role;
  action: EventAction;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus;
  comment: string | null;
  requestId: string | null;
  occurredAt: string;
}

export interface ItemPayload {
  description: string;
  quantity: number;
  unitPrice: string;
}

export interface RequestPayload {
  title: string;
  description: string | null;
  category: Category;
  costCenterId: number | null;
  neededBy: string | null;
  items: ItemPayload[];
  lockVersion?: number;
}

export interface CostCenter {
  id: number;
  code: string;
  name: string;
}

export interface Budget {
  costCenterId: number;
  costCenterCode: string;
  month: string;
  amount: string;
  consumed: string;
  available: string;
}

export interface DashboardSummary {
  totalsByStatus: Partial<Record<RequestStatus, number>>;
  month: string;
  budgets: Budget[];
}

/** Filters of the request list; every field mirrors a query parameter of GET /requests. */
export interface RequestQuery {
  status?: RequestStatus | null;
  category?: Category | null;
  costCenterId?: number | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  minAmount?: string | null;
  maxAmount?: string | null;
  q?: string | null;
  page?: number;
  size?: number;
  sort?: string | null;
}
