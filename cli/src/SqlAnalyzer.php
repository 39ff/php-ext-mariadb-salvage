<?php

declare(strict_types=1);

namespace MariadbProfiler;

use PHPSQLParser\PHPSQLParser;

/**
 * SQL Analyzer - extracts table names and column names from SQL queries
 * using PHPSQLParser.
 */
class SqlAnalyzer
{
    private PHPSQLParser $parser;

    public function __construct()
    {
        $this->parser = new PHPSQLParser();
    }

    /**
     * Analyze a SQL query and extract tables and columns.
     *
     * @param string $sql
     * @return array{tables: string[], columns: string[]}
     */
    public function analyze(string $sql): array
    {
        $tables = [];
        $columns = [];
        $aliases = []; // alias => real_table_name

        try {
            $parsed = $this->parser->parse($sql);
        } catch (\Throwable $e) {
            // If parsing fails, return empty results
            return ['tables' => [], 'columns' => []];
        }

        if (!is_array($parsed)) {
            return ['tables' => [], 'columns' => []];
        }

        // Extract tables and aliases from FROM, JOIN, INTO, UPDATE clauses
        $this->extractTables($parsed, $tables, $aliases);

        // Extract columns from SELECT, WHERE, SET, INSERT columns, GROUP BY, ORDER BY, HAVING
        $this->extractColumns($parsed, $columns, $aliases);

        // Deduplicate
        $tables = array_values(array_unique($tables));
        $columns = array_values(array_unique($columns));

        // Sort for deterministic output
        sort($tables);
        sort($columns);

        return ['tables' => $tables, 'columns' => $columns];
    }

    /**
     * Extract table names and aliases from parsed SQL.
     */
    private function extractTables(array $parsed, array &$tables, array &$aliases): void
    {
        // FROM clause
        if (isset($parsed['FROM'])) {
            $this->extractTablesFromClause($parsed['FROM'], $tables, $aliases);
        }

        // JOIN clauses
        foreach (['JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'CROSS JOIN',
                   'LEFT OUTER JOIN', 'RIGHT OUTER JOIN', 'FULL OUTER JOIN',
                   'NATURAL JOIN', 'STRAIGHT_JOIN'] as $joinType) {
            if (isset($parsed[$joinType])) {
                $this->extractTablesFromClause($parsed[$joinType], $tables, $aliases);
            }
        }

        // UPDATE clause
        if (isset($parsed['UPDATE'])) {
            $this->extractTablesFromClause($parsed['UPDATE'], $tables, $aliases);
        }

        // INSERT INTO
        if (isset($parsed['INSERT'])) {
            $this->extractTablesFromInsert($parsed['INSERT'], $tables, $aliases);
        }

        // DELETE FROM
        if (isset($parsed['DELETE'])) {
            $this->extractTablesFromClause($parsed['DELETE'], $tables, $aliases);
        }
    }

    /**
     * Extract tables from a FROM/JOIN clause array.
     */
    private function extractTablesFromClause(array $clause, array &$tables, array &$aliases): void
    {
        foreach ($clause as $item) {
            if (!is_array($item)) {
                continue;
            }

            $tableName = null;

            if (isset($item['expr_type'])) {
                if ($item['expr_type'] === 'table') {
                    $tableName = $this->cleanIdentifier($item['table'] ?? ($item['base_expr'] ?? null));
                } elseif ($item['expr_type'] === 'subquery') {
                    // Recurse into subquery
                    if (isset($item['sub_tree'])) {
                        $subTables = [];
                        $subAliases = [];
                        $this->extractTables($item['sub_tree'], $subTables, $subAliases);
                        $tables = array_merge($tables, $subTables);
                        $aliases = array_merge($aliases, $subAliases);
                    }
                }
            } elseif (isset($item['table'])) {
                $tableName = $this->cleanIdentifier($item['table']);
            }

            if ($tableName && $tableName !== '') {
                $tables[] = $tableName;

                // Record alias
                if (isset($item['alias']) && is_array($item['alias'])) {
                    $alias = $this->cleanIdentifier($item['alias']['name'] ?? '');
                    if ($alias !== '') {
                        $aliases[$alias] = $tableName;
                    }
                } elseif (isset($item['alias']) && is_string($item['alias'])) {
                    $alias = $this->cleanIdentifier($item['alias']);
                    if ($alias !== '') {
                        $aliases[$alias] = $tableName;
                    }
                }
            }

        }
    }

    /**
     * Extract tables from INSERT clause.
     */
    private function extractTablesFromInsert(array $clause, array &$tables, array &$aliases): void
    {
        foreach ($clause as $item) {
            if (!is_array($item)) {
                continue;
            }
            if (isset($item['expr_type']) && $item['expr_type'] === 'table') {
                $tableName = $this->cleanIdentifier($item['table'] ?? ($item['base_expr'] ?? null));
                if ($tableName && $tableName !== '') {
                    $tables[] = $tableName;
                }
            }
        }
    }

    /**
     * Extract column names from various clauses.
     */
    private function extractColumns(array $parsed, array &$columns, array &$aliases): void
    {
        // SELECT columns
        if (isset($parsed['SELECT'])) {
            $this->extractColumnsFromExpression($parsed['SELECT'], $columns, $aliases);
        }

        // WHERE clause
        if (isset($parsed['WHERE'])) {
            $this->extractColumnsFromExpression($parsed['WHERE'], $columns, $aliases);
        }

        // SET clause (UPDATE)
        if (isset($parsed['SET'])) {
            $this->extractColumnsFromExpression($parsed['SET'], $columns, $aliases);
        }

        // GROUP BY
        if (isset($parsed['GROUP'])) {
            $this->extractColumnsFromExpression($parsed['GROUP'], $columns, $aliases);
        }

        // ORDER BY
        if (isset($parsed['ORDER'])) {
            $this->extractColumnsFromExpression($parsed['ORDER'], $columns, $aliases);
        }

        // HAVING
        if (isset($parsed['HAVING'])) {
            $this->extractColumnsFromExpression($parsed['HAVING'], $columns, $aliases);
        }

        // INSERT columns
        if (isset($parsed['INSERT']) && is_array($parsed['INSERT'])) {
            foreach ($parsed['INSERT'] as $item) {
                if (is_array($item) && isset($item['expr_type']) && $item['expr_type'] === 'column-list') {
                    if (isset($item['sub_tree'])) {
                        $this->extractColumnsFromExpression($item['sub_tree'], $columns, $aliases);
                    }
                }
            }
        }

        // JOIN ON conditions
        foreach (['FROM', 'JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'CROSS JOIN',
                   'LEFT OUTER JOIN', 'RIGHT OUTER JOIN', 'FULL OUTER JOIN'] as $clause) {
            if (isset($parsed[$clause]) && is_array($parsed[$clause])) {
                foreach ($parsed[$clause] as $item) {
                    if (is_array($item) && isset($item['ref_clause']) && is_array($item['ref_clause'])) {
                        $this->extractColumnsFromExpression($item['ref_clause'], $columns, $aliases);
                    }
                }
            }
        }
    }

    /**
     * Recursively extract column references from an expression tree.
     */
    private function extractColumnsFromExpression(array $items, array &$columns, array &$aliases): void
    {
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }

            $exprType = $item['expr_type'] ?? '';

            if ($exprType === 'colref') {
                $col = $this->resolveColumnRef($item, $aliases);
                if ($col !== null) {
                    $columns[] = $col;
                }
            } elseif ($exprType === 'subquery') {
                // Recurse into subquery
                if (isset($item['sub_tree']) && is_array($item['sub_tree'])) {
                    $subColumns = [];
                    $subAliases = $aliases;
                    $subTables = [];
                    $this->extractTables($item['sub_tree'], $subTables, $subAliases);
                    $this->extractColumns($item['sub_tree'], $subColumns, $subAliases);
                    $columns = array_merge($columns, $subColumns);
                }
            }

            // Recurse into sub_tree for functions, expressions, etc.
            if (isset($item['sub_tree']) && is_array($item['sub_tree']) && $exprType !== 'subquery') {
                $this->extractColumnsFromExpression($item['sub_tree'], $columns, $aliases);
            }
        }
    }

    /**
     * Resolve a column reference to "table.column" format.
     */
    private function resolveColumnRef(array $item, array &$aliases): ?string
    {
        $baseExpr = $item['base_expr'] ?? '';
        $noQuotes = $item['no_quotes']['parts'] ?? null;

        if ($baseExpr === '*') {
            return null; // Skip bare wildcards
        }

        if ($noQuotes && is_array($noQuotes)) {
            if (count($noQuotes) === 2) {
                // table.column or alias.column
                $tableOrAlias = $noQuotes[0];
                $column = $noQuotes[1];

                if ($column === '*') {
                    return null; // Skip table.* wildcards
                }

                // Resolve alias to real table name
                $realTable = $aliases[$tableOrAlias] ?? $tableOrAlias;
                return $realTable . '.' . $column;
            } elseif (count($noQuotes) === 3) {
                // schema.table.column
                $table = $noQuotes[1];
                $column = $noQuotes[2];
                if ($column === '*') {
                    return null;
                }
                $realTable = $aliases[$table] ?? $table;
                return $realTable . '.' . $column;
            } elseif (count($noQuotes) === 1) {
                // Just column name without table prefix
                $column = $noQuotes[0];
                if ($column === '*') {
                    return null;
                }
                return $column;
            }
        }

        // Fallback: use base_expr
        $cleaned = $this->cleanIdentifier($baseExpr);
        if ($cleaned !== '' && $cleaned !== '*') {
            return $cleaned;
        }

        return null;
    }

    /**
     * Clean a SQL identifier (remove backticks, quotes).
     */
    private function cleanIdentifier(?string $identifier): string
    {
        if ($identifier === null || $identifier === '') {
            return '';
        }

        // Remove backticks
        $identifier = trim($identifier, '`');
        // Remove double quotes
        $identifier = trim($identifier, '"');
        // Remove brackets (SQL Server style)
        $identifier = trim($identifier, '[]');

        return $identifier;
    }
}
