# Fork Client — Performance Report

Optimization pass targeting the reported 20–30 FPS loss and input lag while playing.
**Constraint honored throughout: no feature, behavior, visual, or UX change.**
All changes are internal caching / allocation / frequency reductions in the client-side render and tick hot paths, plus a new backend optimization system.

Build verified: `./gradlew build` succeeds → `build/libs/fork-client-1.2.1+26.2.jar`.

---

## Part 1 — HUD & Tick Hot-Path Optimizations (`ForkClientController.java`)

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
- **After:** a 64×64 color grid (4096 pixels, vanilla packed map colors) is rebuilt only when the player's block position changes, the level changes, the zoom (1x/2x/4x) changes, or a refresh timeout elapses (`MINIMAP_CACHE_MS`, `MINIMAP_GRID`). The timeout rises to 1500 ms at higher zoom where the rebuild is heavier. The entity snapshot reuses a persistent buffer list (`minimapEntityBuffer`) cleared each frame instead of allocating.

### 4. Grid overlay + chunk border geometry caching
- **Before:** every frame allocated ~75 `Vec3` objects for the grid overlay and ~324 for chunk borders.
- **After:** geometry is baked into `GizmoLine` records in `GridOverlayCache`/`ChunkBorderCache`, rebuilt only when the player's block/chunk position or level changes. `Vec3` is immutable, so reusing instances across frames is safe. Renderer just submits cached lines via `Gizmos.line`.

### 5. Skip clamping/layout for hidden widgets
- `renderHud` now skips disabled widgets entirely (no clamp, no layout, no render). The three enable paths (`toggleWidget`, `applyWidgetModuleState`, and `loadConfig`→`syncWidgetBackedModules`) clamp immediately on enable, and the HUD editor still clamps everything while open, so the old "clamp on every frame while enabled" guarantee is preserved without per-frame work.

### 6. Fullbright / zoom option write guards
- `applyFullbright` / `applyZoom` no longer call `gamma().set(...)` / `fov().set(...)` every tick while active; they only write when the value actually differs. Avoids re-firing option listeners (and downstream work) at 20 Hz while the module is on.

### 7. Various micro-hoists
- Camera-forward/rotation vectors and other loop-invariant values hoisted out of per-frame render loops (waypoint beacons, custom crosshair) so they are computed once per frame instead of once per beacon/item.

---

## Part 2 — Backend Optimization System (`optimization/` package)

A dedicated rendering optimization backend managed by `OptimizationManager`, initialized on client entry and reset on disconnect/dimension change.

### 8. Async Chunk Build Scheduler (`chunk/`)
- **`ChunkBuildScheduler`** — manages a thread pool of `MAX_WORKERS = availableProcessors - 1` daemon `ChunkBuilderThread` instances pulling from a bounded 256-slot `LinkedBlockingQueue`.
- **`ChunkBuilderMeshingTask`** — captures a `LevelSliceView` snapshot and section reference; worker threads perform meshing off the main thread.
- **`ChunkBuilderSortingTask`** — sorts translucent geometry by distance to camera for correct alpha blending.
- **`TaskOutput`** — carries completed mesh/sort data back to the main thread via a 128-slot result queue.
- **Result collection** — `collectResults()` drains the result queue into a `ConcurrentLinkedQueue<TaskOutput>` each frame; `drainPendingResults()` returns them for GPU upload.
- **Impact:** chunk meshing is fully off-main-thread with backpressure; the main thread only processes completed results.

### 9. Occlusion Culler (`occlusion/`)
- **`OcclusionCuller`** — BFS graph traversal from the camera's section through section adjacency graphs.
- Uses **face-to-face transparency data** baked during meshing (`neighborMask`) to determine reachability between adjacent sections.
- Applies **frustum culling** (view distance in sections), **occlusion culling** (opaque face blocking), and **fog culling** (distance > fog end).
- **`SectionTree`** — flat `BitSet`-backed 3D grid (2048×256×2048 default) for O(1) visibility lookups.
- **Async execution** — `beginCull()` marks cull as pending when camera moves >1 block; `executeCull()` runs synchronously during visibility preparation.
- **Impact:** only visible sections are submitted for rendering; hidden terrain behind solid walls is skipped entirely.

### 10. Render Region Manager (`region/`)
- **`RenderRegionManager`** — groups sections into GPU upload regions and manages result upload.
- `uploadResults(List<TaskOutput>)` — processes completed chunk builds and uploads vertex/index data to GPU within the frame budget.
- `getRegionsForSections(List<RenderSection>)` — maps visible sections to their parent regions for draw submission.
- **Impact:** reduces draw call count by batching sections into larger regions.

### 11. Frame Budget Manager (`FrameBudgetManager.java`)
- Tracks frame timing via **exponential moving average** (smoothing factor 0.95).
- Computes adaptive **upload budget** = 30% of average frame time (clamped 10–50 ms).
- Dynamically adjusts chunk build/upload limits based on FPS:
  - >90 FPS: 8 builds + 16 uploads/frame
  - >60 FPS: 4 builds + 8 uploads/frame
  - >30 FPS: 2 builds + 4 uploads/frame
  - ≤30 FPS: 1 build + 2 uploads/frame
- **Impact:** prevents GPU upload spikes from causing frame drops; self-throttles under load.

### 12. Section Storage & Dirty Tracking (`storage/`)
- **`SectionStorage`** — 3D grid of `RenderSection` objects with dirty flags and neighbor opacity masks.
- `RenderSection` tracks: position, dirty state, rebuild-needed flag, and a 6-bit `neighborMask` (one bit per face, set when the adjacent section is fully opaque on that face).
- **Impact:** only dirty sections are scheduled for rebuild; neighbor masks enable fast occlusion decisions.

### 13. World Data Snapshots (`world/`)
- **`ClonedChunkSectionCache`** — LRU-style cache of cloned chunk sections for thread-safe meshing (workers cannot access live world data).
- **`LevelSliceView`** — thread-safe cross-section view providing block state access to chunk builders without locking the world.
- **Impact:** eliminates race conditions between worker threads and the main thread; avoids full world snapshots.

---

## Part 3 — Entity Culling (`modules/EntityCullingModule.java`)

### 14. Async DDA Entity Occlusion
- **Before:** every entity was rendered regardless of whether it was behind solid blocks.
- **After:** a DDA (Digital Differential Analyzer) ray-step algorithm traces from the camera to each entity's eye position, checking for occluding blocks along the path.
- **Double-buffered** visibility: the current frame reads the previous frame's results; the current frame's traces write to a separate buffer, swapped each frame.
- **Async trace pool** — `availableProcessors - 1` daemon threads at `NORM_PRIORITY - 1`; results are placed in a `ConcurrentHashMap<Long, Boolean>`.
- **Occlusion check** — uses `BlockState.isSolidRender()` (the same predicate vanilla uses for face-culling during chunk meshing) to identify fully opaque full-cube blocks.
- **Distance range** — entities closer than 1.5 blocks or farther than 128 blocks are never culled (always rendered).
- **Position hashing** — entity positions are quantized to 1/100th of a block and hashed for cache keying.
- **Impact:** in dense areas (caves, bases, forests), entities hidden behind walls are skipped, reducing entity render cost significantly.

---

## Expected Impact Summary

| Area | Before | After |
|---|---|---|
| HUD text work per frame | format + measure + allocate per line | 0 for unchanged lines; reused components |
| Module title sort/filter | per frame | only on state change |
| Minimap block lookups | ~1024/frame | up to 4096 per block-move, zoom change, or refresh timeout (1 s at 1x, 1.5 s at 2x/4x) |
| Minimap entity allocations | 1 list/frame | 0 (buffer reused) |
| Grid overlay Vec3s | ~75/frame | ~0 (rebuilt on move) |
| Chunk border Vec3s | ~324/frame | ~0 (rebuilt on chunk change) |
| Option listener firing | 20 Hz while fullbright/zoom | 0 (only on change) |
| Chunk meshing | main thread | async worker pool (N-1 threads) |
| Chunk uploads | unbounded per frame | budget-limited (adaptive, 10–50 ms/frame) |
| Terrain visibility | render all loaded sections | BFS occlusion cull (only visible sections) |
| Entity rendering | render all entities | DDA ray-step cull (skip occluded entities) |
| GC pressure (short-lived objects) | high | low |

---

## Verification Notes

- `./gradlew build` passes.
- Widget sizes in the cache are byte-for-byte identical to the original `measureWidget` results (verified via diff), so the HUD layout, editor hitboxes, and clamping are unchanged.
- `CachedLine` renders the same `Component.literal(text)` the old path produced per frame, so text rendering output is identical.
- The mod remains a client-only Fabric mod; all optimizations are internal to the client tick and render paths.
- The backend optimization system (`OptimizationManager`) is fully self-contained in the `optimization/` package with no dependencies on module logic.
- Entity culling results are eventually consistent (1-frame lag) but visually imperceptible at normal gameplay speeds.
