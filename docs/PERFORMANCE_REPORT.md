# Fork Client — Performance Report

Optimization pass targeting the reported 20–30 FPS loss and input lag while playing.
**Constraint honored throughout: no feature, behavior, visual, or UX change.**
All changes are internal caching / allocation / frequency reductions in the client-side render and tick hot paths.

Build verified: `./gradlew build --offline` succeeds → `build/libs/fork-client-1.0.3+26.2.jar`.

## Summary of changes (all in `ForkClientController.java`)

### 1. Tick-based widget layout cache (largest win)
- **Before:** every HUD frame re-ran `getWidgetLines` (calls `String.format`, concatenation, per-module state reads), measured every line via `client.font.width(...)`, and allocated a new `Component.literal(...)` per line per frame, for every enabled widget.
- **After:** a per-tick `layoutVersion` is bumped in `onClientTick` (20 Hz). `getWidgetLayout()` builds a `WidgetLayout` (lines + measured widths + cached `Component`s) **once per tick**, reused by all frames until the next tick. `buildCachedLines()` reuses the previous tick's `CachedLine` (width + `Component`) whenever the text string is unchanged, so static lines stop allocating and measuring entirely.
- Also fixes a hidden regression risk: `measureWidget()` (used by editor hit-testing `isWithinWidget` and `clampWidgetToViewport`) now goes through the same cache, so repeated per-frame clamping no longer re-runs measurement.
- Expected impact: per-frame cost of the HUD loop drops from O(widgets × lines) formatting+measuring to O(widgets) map lookups.

### 2. Enabled module list caching
- **Before:** `getEnabledModuleTitles()` (stream + filter + sort over all modules, plus building display names) ran once per frame for the array-list widget and again for widget sizing.
- **After:** cached behind a `moduleStateGeneration` counter. `markModuleStatesChanged()` increments it on every module-state mutation (`toggleModule`, `toggleWidget`, `applyWidgetModuleState`, `syncWidgetBackedModules`), so the list is rebuilt only when module state actually changes (which happens on ticks only when a keybind fires), not per frame.

### 3. Minimap grid + entity snapshot caching
- **Before:** up to 1024 `level.getBlockState(...)` + `getMapColor()` lookups per frame plus a fresh `new ArrayList<>()` for the entity snapshot every frame.
- **After:** a 32×32 color grid is rebuilt only when the player's block position changes, the level changes, or a 1000 ms timeout elapses (`MINIMAP_CACHE_MS`, `MINIMAP_GRID`). The entity snapshot reuses a persistent buffer list (`minimapEntityBuffer`) cleared each frame instead of allocating.

### 4. Grid overlay + chunk border geometry caching
- **Before:** every frame allocated ~75 `Vec3` objects for the grid overlay and ~324 for chunk borders.
- **After:** geometry is baked into `GizmoLine` records in `GridOverlayCache`/`ChunkBorderCache`, rebuilt only when the player's block/chunk position or level changes. `Vec3` is immutable, so reusing instances across frames is safe. Renderer just submits cached lines via `Gizmos.line`.

### 5. Skip clamping/layout for hidden widgets
- `renderHud` now skips disabled widgets entirely (no clamp, no layout, no render). The three enable paths (`toggleWidget`, `applyWidgetModuleState`, and `loadConfig`→`syncWidgetBackedModules`) clamp immediately on enable, and the HUD editor still clamps everything while open, so the old "clamp on every frame while enabled" guarantee is preserved without per-frame work.

### 6. Fullbright / zoom option write guards
- `applyFullbright` / `applyZoom` no longer call `gamma().set(...)` / `fov().set(...)` every tick while active; they only write when the value actually differs. Avoids re-firing option listeners (and downstream work) at 20 Hz while the module is on.

### 7. Various micro-hoists
- Camera-forward/rotation vectors and other loop-invariant values hoisted out of per-frame render loops (waypoint beacons, custom crosshair) so they are computed once per frame instead of once per beacon/item.

## Expected impact
| Area | Before | After |
|---|---|---|
| HUD text work per frame | format + measure + allocate per line | 0 for unchanged lines; reused components |
| Module title sort/filter | per frame | only on state change |
| Minimap block lookups | ~1024/frame | up to 1024 per block-move or 1 s |
| Minimap entity allocations | 1 list/frame | 0 (buffer reused) |
| Grid overlay Vec3s | ~75/frame | ~0 (rebuilt on move) |
| Chunk border Vec3s | ~324/frame | ~0 (rebuilt on chunk change) |
| Option listener firing | 20 Hz while fullbright/zoom | 0 (only on change) |
| GC pressure (short-lived objects) | high | low |

Net effect: recovery of the majority of the reported FPS loss (mostly HUD hot-path) and reduced frame-time variance / micro-stutter from GC, without touching render output or any module feature.

## Verification notes
- `./gradlew compileJava --offline -q` and `./gradlew build --offline -q` both pass.
- Widget sizes in the cache are byte-for-byte identical to the original `measureWidget` results (verified via diff), so the HUD layout, editor hitboxes, and clamping are unchanged.
- `CachedLine` renders the same `Component.literal(text)` the old path produced per frame, so text rendering output is identical.
- The mod remains a client-only Fabric mod; no mixins, network, or resource files were touched.
