export function generateBar(value: number, max: number, barWidth: number = 20): string {
  if (max === 0) { return '\u2591'.repeat(barWidth); }
  const filled = Math.max(0, Math.min(barWidth, Math.round((value / max) * barWidth)));
  return '\u2588'.repeat(filled) + '\u2591'.repeat(barWidth - filled);
}

export function formatPercent(value: number, total: number): string {
  if (total === 0) { return '0%'; }
  return `${Math.round((value / total) * 100)}%`;
}

export function shortKey(key: string, maxLen: number = 12): string {
  if (key.length <= maxLen) { return key; }
  return key.substring(0, maxLen);
}
