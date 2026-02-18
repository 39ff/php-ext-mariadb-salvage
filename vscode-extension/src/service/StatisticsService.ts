import { QueryEntry, getQueryType, getTables } from '../model/QueryEntry';

export interface QueryStats {
  totalQueries: number;
  byType: Record<string, number>;
  byTable: Record<string, number>;
  byTag: Record<string, number>;
}

export class StatisticsService {
  computeStats(entries: QueryEntry[]): QueryStats {
    const byType: Record<string, number> = {};
    const byTable: Record<string, number> = {};
    const byTag: Record<string, number> = {};

    for (const entry of entries) {
      // By type
      const qtype = getQueryType(entry);
      byType[qtype] = (byType[qtype] || 0) + 1;

      // By table
      for (const table of getTables(entry)) {
        byTable[table] = (byTable[table] || 0) + 1;
      }

      // By tag
      if (entry.tag) {
        byTag[entry.tag] = (byTag[entry.tag] || 0) + 1;
      }
    }

    return {
      totalQueries: entries.length,
      byType: sortByValueDesc(byType),
      byTable: sortByValueDesc(byTable),
      byTag: sortByValueDesc(byTag),
    };
  }
}

function sortByValueDesc(record: Record<string, number>): Record<string, number> {
  const sorted: Record<string, number> = {};
  const entries = Object.entries(record).sort((a, b) => b[1] - a[1]);
  for (const [key, value] of entries) {
    sorted[key] = value;
  }
  return sorted;
}
