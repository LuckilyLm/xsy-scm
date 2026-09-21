import Decimal from 'decimal.js';
export function money(value: string | null | undefined): string {
  return value == null ? '—' : `¥ ${new Decimal(value).toFixed(2)}`;
}
