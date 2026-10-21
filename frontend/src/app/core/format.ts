import { Category } from './models';

export const CATEGORY_LABEL: Record<Category, string> = {
  BILLING: 'Billing',
  SHIPPING: 'Shipping',
  RETURN_REFUND: 'Return / refund',
  TECHNICAL: 'Technical',
  ACCOUNT: 'Account',
  OTHER: 'Other',
};

export function age(iso: string | undefined, now = Date.now()): string {
  if (!iso) {
    return '';
  }
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  if (s < 86400) return `${Math.floor(s / 3600)}h`;
  return `${Math.floor(s / 86400)}d`;
}

export function seconds(ms: number | undefined | null): string {
  return ms == null ? '–' : `${(ms / 1000).toFixed(1)} s`;
}

export function pct(v: unknown): string {
  return typeof v === 'number' ? `${Math.round(v * 100)}%` : '–';
}

export function json(v: unknown): string {
  return JSON.stringify(v, null, 2);
}
