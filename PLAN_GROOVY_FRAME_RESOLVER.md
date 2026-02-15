# Plan: Groovy Script for Query Log Frame Resolution

## Overview
Replace the static `tagDepthMapping` with a user-editable Groovy script that determines
which backtrace frame to display in the Query Log's Function/File columns.

## Changes

### 1. build.gradle.kts - Add Groovy dependency
- Add `org.apache.groovy:groovy:4.0.18` (compatible with JVM 17)

### 2. FrameResolverService (NEW)
Location: `service/FrameResolverService.kt`
- Project-level service
- Compiles Groovy script once via `GroovyShell` (cached, recompiles on script text change)
- `resolve(entry: QueryEntry): Int` — returns frame index to display
- Script bindings:
  - `trace` — List of Maps `[file: String, line: int, call: String]`
  - `tag` — String? (query tag)
  - `query` — String (SQL)
- Returns `0` as fallback on error
- Thread-safe (synchronized compilation)

### 3. ProfilerState
- Replace `tagDepthMapping: String` → `frameResolverScript: String`
- Remove `getTagDepthMap()`, `getDepthForTag()`
- Default script implements the same tag-depth logic as a Groovy map:
```groovy
// Tag-to-depth mapping
def depthMap = ['default': 0]
def depth = depthMap[tag ?: 'default'] ?: depthMap['default'] ?: 0
if (depth < trace.size()) return depth
return 0
```

### 4. SettingsPanel
- Replace single-line `tagDepthField` → multi-line `JTextArea` (8 rows)
- Section title: "Frame Resolver Script (Groovy)"
- Add "Reset to Default" button
- Show compilation error inline if script is invalid

### 5. QueryTableModel
- Remove `getFrameAtDepth()` (was using `ProfilerState.getDepthForTag`)
- Instead: call `FrameResolverService.resolve(entry)` → get frame index
- Use that index for Function and File columns

### 6. QueryDetailPanel
- Replace `state.getDepthForTag(entry.tag)` → `FrameResolverService.resolve(entry)`
- Use resolved index for backtrace highlight

### 7. plugin.xml
- Register `FrameResolverService` as `<projectService>`

## User Experience

**Default** — Works exactly like current tag-depth mapping (backward compatible).

**Customized** — User edits the Groovy script in Settings. Examples:

Pattern match (find first app code frame):
```groovy
for (int i = 0; i < trace.size(); i++) {
    if (trace[i].file.contains('/app/')) return i
}
return 0
```

Complex logic (tag-aware + pattern fallback):
```groovy
if (tag == 'queue') {
    return trace.findIndexOf { it.file.contains('/Jobs/') }
}
def idx = trace.findIndexOf { it.file =~ /\/(app|src)\// }
return idx >= 0 ? idx : 0
```
