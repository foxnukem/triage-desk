export type TicketStatus = 'NEW' | 'TRIAGED' | 'IN_REVIEW' | 'RESOLVED' | 'ESCALATED';
export type Category = 'BILLING' | 'SHIPPING' | 'RETURN_REFUND' | 'TECHNICAL' | 'ACCOUNT' | 'OTHER';
export type Priority = 'P1' | 'P2' | 'P3' | 'P4';
export type DecisionAction = 'ACCEPTED' | 'EDITED' | 'REJECTED' | 'ESCALATED';
export type StepStatus = 'OK' | 'INVALID' | 'ERROR' | 'FAILED' | 'OVERRIDE' | 'INFO' | 'SKIPPED';

export const CATEGORIES: Category[] = ['BILLING', 'SHIPPING', 'RETURN_REFUND', 'TECHNICAL', 'ACCOUNT', 'OTHER'];
export const PRIORITIES: Priority[] = ['P1', 'P2', 'P3', 'P4'];
export const STATUSES: TicketStatus[] = ['NEW', 'TRIAGED', 'IN_REVIEW', 'RESOLVED', 'ESCALATED'];

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface TicketRow {
  id: number;
  status: TicketStatus;
  source: string;
  createdAt: string;
  updatedAt: string;
  preview: string;
  customerEmail?: string;
  category?: Category;
  priority?: Priority;
  queue?: string;
  needsHuman?: boolean;
  ruleOverride: boolean;
  model?: string;
  triageError?: string;
}

export interface Classification {
  category: Category;
  priority: Priority;
  priorityReason: string;
  sentiment: 'NEGATIVE' | 'NEUTRAL' | 'POSITIVE';
  language: 'UK' | 'EN' | 'OTHER';
  confidence: number;
  multiIntent: boolean;
}

export interface Extraction {
  orderId?: string;
  email?: string;
  product?: string;
  amount?: number;
  summary: string;
}

export interface ReplyDraft {
  text: string;
  citedArticles: string[];
  promisesAction: boolean;
}

export interface ToolCall {
  name: string;
  arguments: string;
  result: string;
  durationMs: number;
}

export interface StepTrace {
  name: string;
  attempt: number;
  durationMs: number;
  status: StepStatus;
  errors?: string[];
  output?: unknown;
  toolCalls?: ToolCall[];
  note?: string;
}

export interface PipelineTrace {
  steps: StepTrace[];
  totalMs: number;
  llmCalls: number;
  retries: number;
}

export interface RuleOverride {
  ruleId: string;
  reason: string;
  matched: string;
  modelCategory: Category;
  modelPriority: Priority;
  categoryChanged: boolean;
  priorityRaised: boolean;
}

export interface PipelineOptions {
  model: string;
  ruleRouter: boolean;
  validation: boolean;
  critic: boolean;
  megaPrompt: boolean;
  reply: boolean;
  classifier?: Classifier;
}

export type Classifier = 'llm' | 'laya';

export interface ArmStats {
  tickets: number;
  needsHuman: number;
  ACCEPTED: number;
  EDITED: number;
  REJECTED: number;
  ESCALATED: number;
  acceptedWithoutEdits?: number | null;
}

export interface TriageResult {
  classification: Classification;
  extraction: Extraction;
  reply?: ReplyDraft;
  queue: string;
  needsHuman: boolean;
  escalationReason?: string;
  ruleOverride?: RuleOverride;
  model: string;
  options: PipelineOptions;
  diagnostics?: { draftBeforeCritic?: ReplyDraft; policyLintFinal?: string[]; retrievedArticles?: string[] };
  trace: PipelineTrace;
}

export interface HistoryItem {
  id: number;
  model: string;
  category?: Category;
  priority?: Priority;
  needsHuman: boolean;
  createdAt: string;
}

export interface Decision {
  id: number;
  resultId?: number;
  action: DecisionAction;
  finalText?: string;
  note?: string;
  decidedAt: string;
}

export interface TicketDetail {
  id: number;
  status: TicketStatus;
  source: string;
  text: string;
  customerEmail?: string;
  createdAt: string;
  updatedAt: string;
  triageError?: string;
  currentResultId?: number;
  result?: TriageResult;
  history: HistoryItem[];
  decisions: Decision[];
}

export interface Stats {
  byStatus: Record<TicketStatus, number>;
  decisions: Record<DecisionAction, number>;
  acceptedWithoutEdits?: number | null;
  arms?: Record<string, ArmStats>;
}

export interface Sample {
  id: string;
  slice: string;
  lang: string;
  tags: string[];
  demo?: number;
  text: string;
  expected: Record<string, unknown>;
}

export interface KbArticle {
  id: string;
  title: string;
  body: string;
}

export interface KbHit {
  articleId: string;
  title: string;
  score: number;
  text: string;
}

export interface EvalConfig {
  label: string;
  model: string;
  ruleRouter: boolean;
  validation: boolean;
  critic: boolean;
  megaPrompt: boolean;
  reply: boolean;
  limit?: number | null;
  slice?: string | null;
  caseIds?: string[] | null;
  classifier?: Classifier;
}

export type Metrics = Record<string, any>;

export interface RunSummary {
  id: number;
  label: string;
  config: EvalConfig;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  totalCases: number;
  doneCases: number;
  error?: string;
  startedAt: string;
  finishedAt?: string;
  metrics?: Metrics;
}

export interface CaseDetail {
  caseId: string;
  slice: string;
  lang: string;
  tags: string[];
  text: string;
  expected: Record<string, any>;
  actual?: Record<string, any>;
  scores?: Record<string, any>;
  latencyMs: number;
  error?: string;
  result?: TriageResult;
}

export interface CaseRow {
  caseId: string;
  slice: string;
  correct: boolean;
  latencyMs: number;
  detail: CaseDetail;
}

export interface RunDetail {
  run: RunSummary;
  cases: CaseRow[];
}
