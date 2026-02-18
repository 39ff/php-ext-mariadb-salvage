export interface BacktraceFrame {
  call: string;
  file: string;
  line: number;
  function?: string;
  class_name?: string;
}

export interface RawQueryEntry {
  k: string;
  q: string;
  ts: number;
  tag?: string;
  s?: string;
  params?: (string | null)[];
  trace?: BacktraceFrame[];
}

export interface QueryEntry {
  jobKey: string;
  query: string;
  timestamp: number;
  tag?: string;
  status?: string;
  params?: (string | null)[];
  trace?: BacktraceFrame[];
}

export type QueryType = 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'OTHER';

export function fromRaw(raw: RawQueryEntry): QueryEntry {
  return {
    jobKey: raw.k,
    query: raw.q,
    timestamp: raw.ts,
    tag: raw.tag,
    status: raw.s,
    params: raw.params,
    trace: raw.trace,
  };
}

export function getQueryType(entry: QueryEntry): QueryType {
  const trimmed = entry.query.trimStart().toUpperCase();
  if (trimmed.startsWith('SELECT')) { return 'SELECT'; }
  if (trimmed.startsWith('INSERT')) { return 'INSERT'; }
  if (trimmed.startsWith('UPDATE')) { return 'UPDATE'; }
  if (trimmed.startsWith('DELETE')) { return 'DELETE'; }
  return 'OTHER';
}

export function getBoundQuery(entry: QueryEntry): string {
  if (!entry.params || entry.params.length === 0) {
    return entry.query;
  }

  let paramIndex = 0;
  const params = entry.params;
  let result = '';
  let i = 0;
  const q = entry.query;

  while (i < q.length) {
    // Skip string literals
    if (q[i] === '\'' || q[i] === '"') {
      const quote = q[i];
      result += q[i++];
      while (i < q.length) {
        if (q[i] === '\\') {
          result += q[i++];
          if (i < q.length) { result += q[i++]; }
        } else if (q[i] === quote) {
          result += q[i++];
          break;
        } else {
          result += q[i++];
        }
      }
      continue;
    }

    // Skip backtick-quoted identifiers
    if (q[i] === '`') {
      result += q[i++];
      while (i < q.length && q[i] !== '`') {
        result += q[i++];
      }
      if (i < q.length) { result += q[i++]; }
      continue;
    }

    // Skip line comments
    if (q[i] === '-' && i + 1 < q.length && q[i + 1] === '-') {
      while (i < q.length && q[i] !== '\n') {
        result += q[i++];
      }
      continue;
    }
    if (q[i] === '#') {
      while (i < q.length && q[i] !== '\n') {
        result += q[i++];
      }
      continue;
    }

    // Skip block comments
    if (q[i] === '/' && i + 1 < q.length && q[i + 1] === '*') {
      result += q[i++];
      result += q[i++];
      while (i < q.length && !(q[i] === '*' && i + 1 < q.length && q[i + 1] === '/')) {
        result += q[i++];
      }
      if (i < q.length) { result += q[i++]; result += q[i++]; }
      continue;
    }

    // Replace placeholder
    if (q[i] === '?' && paramIndex < params.length) {
      const param = params[paramIndex++];
      result += param === null ? 'NULL' : `'${param}'`;
      i++;
      continue;
    }

    result += q[i++];
  }

  return result;
}

const TABLE_PATTERNS = [
  /\bFROM\s+`?(\w+)`?/gi,
  /\bJOIN\s+`?(\w+)`?/gi,
  /\bUPDATE\s+`?(\w+)`?/gi,
  /\bINTO\s+`?(\w+)`?/gi,
  /\bDELETE\s+FROM\s+`?(\w+)`?/gi,
];

export function getTables(entry: QueryEntry): string[] {
  const tables = new Set<string>();
  for (const pattern of TABLE_PATTERNS) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(entry.query)) !== null) {
      tables.add(match[1].toLowerCase());
    }
  }
  return [...tables].sort();
}

export function getShortSql(entry: QueryEntry, maxLen: number = 60): string {
  const sql = entry.query.replace(/\s+/g, ' ').trim();
  if (sql.length <= maxLen) { return sql; }
  return sql.substring(0, maxLen - 3) + '...';
}

export function getSourceFile(entry: QueryEntry): string | null {
  if (!entry.trace || entry.trace.length === 0) { return null; }
  const frame = entry.trace[0];
  return `${frame.file}:${frame.line}`;
}

export function formatTimestamp(ts: number): string {
  const date = new Date(ts * 1000);
  return date.toTimeString().split(' ')[0] +
    '.' + String(date.getMilliseconds()).padStart(3, '0');
}
