# Fork Client Output

## Overview

`Fork Client` (v1.2.1+26.2) is a client-side Fabric mod for Minecraft `26.2` that adds:

- A right-shift click GUI for toggling ~50 modules across 8 categories
- A draggable HUD editor with 30+ persistent widgets
- HUD widgets for coordinates, performance, time, server info, CPS, keystrokes, armor, minimap, and more
- Utility modules such as auto sprint, toggle sneak, auto tool, and inventory walk
- Visual modules such as brightness plus, zoom, motion blur, entity culling, and freelook
- Content creation tools: cinematic camera, timelapse helper, camera path recorder
- Building helpers: grid overlay, chunk borders, shape/blueprint previews, material calculator
- Multiplayer tools: friend list, ping graph, TPS estimator, chat filters, quick messages
- A performance optimization backend with async chunk meshing, occlusion culling, and frame budget management
- Toast-style notifications when module or widget visibility changes
- Server-side permission system controlling movement/rendering/utility features

## Java Components

### Core Entry Points

- `src/main/java/com/mahidx7/forkclient/ForkClient.java`
  - Common bootstrap: registers network payloads (handshake + permissions), server-side receiver, logger, identifier helper.
- `src/main/java/com/mahidx7/forkclient/ForkClientClient.java`
  - Client entrypoint: registers networking, initializes controller, motion blur module, and optimization system.

### Client Systems

- `src/main/java/com/mahidx7/forkclient/client/ForkClientController.java`
  - Central runtime state for modules, widgets, keybinds, HUD rendering, notifications, movement helpers, combat tracking, content creation tools, and config persistence. Handles all tick-based logic.
- `src/main/java/com/mahidx7/forkclient/client/ForkClickGuiScreen.java`
  - In-game click GUI for category browsing, module toggling, sliders, dropdowns, and keybind configuration.
- `src/main/java/com/mahidx7/forkclient/client/ForkHudEditorScreen.java`
  - HUD editor for dragging widgets, toggling visibility, and returning to the click GUI.

### Modules

- `src/main/java/com/mahidx7/forkclient/client/modules/AntiKnockbackModule.java`
  - Configurable horizontal/vertical knockback reduction (0–100%).
- `src/main/java/com/mahidx7/forkclient/client/modules/MotionBlurPlusModule.java`
  - Configurable motion blur with `/motionblurplus` command support and keybind.
- `src/main/java/com/mahidx7/forkclient/client/modules/MotionBlurPlusRenderer.java`
  - Renders the motion blur post-processing effect.
- `src/main/java/com/mahidx7/forkclient/client/modules/MotionBlurOptionsScreen.java`
  - Settings screen for motion blur strength and toggle.
- `src/main/java/com/mahidx7/forkclient/client/modules/EntityCullingModule.java`
  - DDA ray-step entity occlusion with async double-buffered visibility traces.
- `src/main/java/com/mahidx7/forkclient/client/modules/ArmorDurabilityConfigScreen.java`
  - Configuration screen for armor durability HUD options.
- `src/main/java/com/mahidx7/forkclient/client/modules/BlurAssetsReloader.java`
  - Resource reload listener for blur shader assets.

### Building Helpers

- `src/main/java/com/mahidx7/forkclient/client/modules/building/BlockMeasurementModule.java`
  - Measure width, height, length, volume, and distance between two positions.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/BlockPaletteModule.java`
  - Display selected building palette with color groups and favorites.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/BlueprintPreviewModule.java`
  - Load and render blueprint files as transparent ghost block wireframes.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/BuildHeightModule.java`
  - Display current Y level, build limit, and remaining height.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/ChunkBorderModule.java`
  - Customizable chunk border rendering with color and thickness options.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/GridOverlayModule.java`
  - Render an optional build grid: block, chunk, vertical, and horizontal.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/MaterialCalculatorModule.java`
  - Calculate required materials for a build with category breakdowns.
- `src/main/java/com/mahidx7/forkclient/client/modules/building/ShapePreviewModule.java`
  - Render ghost previews for circles, spheres, cylinders, and domes.

### HUD Components

- `src/main/java/com/mahidx7/forkclient/client/hud/ArmorDurabilityHudComponent.java`
  - Armor durability HUD with configurable scale, spacing, slot toggles, and color-coded durability.
- `src/main/java/com/mahidx7/forkclient/client/hud/ArrayListHudComponent.java`
  - Gradient-colored list of enabled modules.

### Permissions

- `src/main/java/com/mahidx7/forkclient/client/permissions/FeaturePermissions.java`
  - Server-granted feature gates: MOVEMENT, RENDERING, UTILITY. Single-player = all allowed.

### Network

- `src/main/java/com/mahidx7/forkclient/network/ForkClientHandshakePayload.java`
  - Client→Server handshake with mod version string.
- `src/main/java/com/mahidx7/forkclient/network/MovementPermissionsPayload.java`
  - Server→Client permission flags for movement, rendering, and utility.

### Optimization System

- `src/main/java/com/mahidx7/forkclient/optimization/OptimizationManager.java`
  - Central lifecycle coordinator for all optimization subsystems.
- `src/main/java/com/mahidx7/forkclient/optimization/FrameBudgetManager.java`
  - Adaptive frame timing with exponential moving average; computes upload budget (30% of frame time, 10–50ms).
- `src/main/java/com/mahidx7/forkclient/optimization/chunk/ChunkBuildScheduler.java`
  - Thread pool manager for async chunk meshing with bounded task/result queues.
- `src/main/java/com/mahidx7/forkclient/optimization/chunk/ChunkBuilderThread.java`
  - Worker thread that pulls meshing/sorting tasks from the queue.
- `src/main/java/com/mahidx7/forkclient/optimization/chunk/ChunkBuilderMeshingTask.java`
  - Captures a LevelSliceView snapshot for off-thread meshing.
- `src/main/java/com/mahidx7/forkclient/optimization/chunk/ChunkBuilderSortingTask.java`
  - Sorts translucent geometry by distance to camera for correct alpha blending.
- `src/main/java/com/mahidx7/forkclient/optimization/chunk/TaskOutput.java`
  - Carries completed mesh/sort data back to the main thread.
- `src/main/java/com/mahidx7/forkclient/optimization/occlusion/OcclusionCuller.java`
  - BFS visibility traversal using face-to-face transparency data and section adjacency graphs.
- `src/main/java/com/mahidx7/forkclient/optimization/occlusion/SectionTree.java`
  - Flat BitSet-backed 3D grid (2048×256×2048) for O(1) section visibility lookups.
- `src/main/java/com/mahidx7/forkclient/optimization/region/RenderRegionManager.java`
  - Groups sections into GPU upload regions; processes completed chunk build results.
- `src/main/java/com/mahidx7/forkclient/optimization/region/RenderRegion.java`
  - Represents a batched GPU upload region containing multiple sections.
- `src/main/java/com/mahidx7/forkclient/optimization/storage/SectionStorage.java`
  - 3D grid of RenderSection objects with dirty flags and neighbor opacity masks.
- `src/main/java/com/mahidx7/forkclient/optimization/world/ClonedChunkSectionCache.java`
  - LRU cache of cloned chunk sections for thread-safe off-thread meshing.
- `src/main/java/com/mahidx7/forkclient/optimization/world/LevelSliceView.java`
  - Thread-safe cross-section view providing block state access to chunk builders.

### Mixins

- `src/main/java/com/mahidx7/forkclient/mixin/VelocityMixin.java`
  - Anti-knockback velocity override on LivingEntity.
- `src/main/java/com/mahidx7/forkclient/mixin/ClientInputMixin.java`
  - Auto-sprint injection on LocalPlayer.
- `src/main/java/com/mahidx7/forkclient/mixin/FogRendererMixin.java`
  - Brightness Plus gamma override.
- `src/main/java/com/mahidx7/forkclient/mixin/GameRendererMixin.java`
  - Freelook camera angle override and motion blur post-processing hook.
- `src/main/java/com/mahidx7/forkclient/mixin/GameRendererPoolAccessor.java`
  - Accessor for GameRenderer internals.
- `src/main/java/com/mahidx7/forkclient/mixin/LevelRendererMixin.java`
  - Entity culling injection and optimization hooks.
- `src/main/java/com/mahidx7/forkclient/mixin/LivingEntityRendererMixin.java`
  - Clean View (own nametag hide in third person).
- `src/main/java/com/mahidx7/forkclient/mixin/CameraMixin.java`
  - Freelook camera isolation.
- `src/main/java/com/mahidx7/forkclient/mixin/ChatFilterMixin.java`
  - Chat filter and timestamp injection.
- `src/main/java/com/mahidx7/forkclient/mixin/ChatComponentMixin.java`
  - Chat component modification for timestamps and filters.
- `src/main/java/com/mahidx7/forkclient/mixin/ChatScreenMixin.java`
  - Chat screen hooks for filter integration.
- `src/main/java/com/mahidx7/forkclient/mixin/ItemInHandRendererMixin.java`
  - Low Shield/Low Fire model adjustments.
- `src/main/java/com/mahidx7/forkclient/mixin/ScreenEffectRendererMixin.java`
  - Fire overlay shrink.
- `src/main/java/com/mahidx7/forkclient/mixin/LevelExtractorMixin.java`
  - World data extraction for TPS estimation and weather control.
- `src/main/java/com/mahidx7/forkclient/mixin/LightmapRenderStateExtractorMixin.java`
  - Brightness Plus lightmap injection.
- `src/main/java/com/mahidx7/forkclient/mixin/PostEffectPassBufferAccessor.java`
  - Accessor for motion blur post-effect buffers.
- `src/main/java/com/mahidx7/forkclient/mixin/PostEffectProcessorPassesAccessor.java`
  - Accessor for post-effect processor pass list.
- `src/main/java/com/mahidx7/forkclient/mixin/ShaderManagerAccessor.java`
  - Accessor for shader manager internals.

## Registered Systems

### Entrypoints

- `main`
  - `com.mahidx7.forkclient.ForkClient` — common bootstrap, network payload registration, server-side handshake receiver.
- `client`
  - `com.mahidx7.forkclient.ForkClientClient` — client bootstrap, networking, controller init, optimization system init.

### Keybinds

| Key | Action |
|---|---|
| `Right Shift` | Open/close click GUI |
| `H` | Open/close HUD editor |
| `K` | Open armor durability config |
| `C` | Zoom (hold) |
| `V` | Freelook (hold) |
| `F1` | Hide HUD toggle |
| `J` | Camera path record/stop |
| `B` | Brightness Plus toggle |
| `=` / `-` | Brightness Plus adjust |
| `M` | Motion Blur Plus settings |
| Numpad `1–9` | Quick messages |

### Fabric Events and APIs

- `ClientTickEvents.START_CLIENT_TICK`
  - Pre-tick for toggle sneak latching.
- `ClientTickEvents.END_CLIENT_TICK`
  - Processes keybind presses, all module logic (visual, movement, utility, content creation, building, multiplayer), CPS tracking, movement speed, combat tracking, timers, and notifications.
- `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ...)`
  - Inserts the custom Fork Client HUD overlay into the vanilla HUD pipeline.
- `HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, ...)`
  - Conditional boss bar overhaul scaling.
- `HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, ...)`
  - Conditional scoreboard repositioning.
- `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, ...)`
  - Conditional custom crosshair suppression.
- `LevelRenderEvents.BEFORE_GIZMOS.register(...)`
  - Renders grid overlay, chunk borders, shape previews, and blueprint previews.
- `ClientPlayNetworking.registerGlobalReceiver(MovementPermissionsPayload.TYPE, ...)`
  - Receives server permission grants.
- `ClientPlayConnectionEvents.JOIN.register(...)`
  - Sends handshake payload on server join.
- `ClientPlayConnectionEvents.DISCONNECT.register(...)`
  - Resets permissions and reloads optimization system on disconnect.
- `ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(...)`
  - Registers blur shader asset reload listener.

## Modules

### HUD (30+ widgets)

- `Clock Widget` — displays current world time
- `Coordinates Widget` — displays current X/Y/Z
- `Performance Widget` — displays FPS, ping, and speed
- `FPS Counter` — displays current frames per second
- `Ping Display` — displays server latency
- `Memory Usage` — displays Java memory usage
- `Direction HUD` — displays facing direction
- `Biome Display` — displays current biome
- `Armor Durability` — displays armor items with durability percentages
- `Saturation Display` — displays hunger and saturation
- `Day Counter` — displays current world day
- `Server Widget` — displays server or singleplayer status
- `Keystrokes Widget` — displays movement and mouse input
- `CPS Widget` — displays left and right clicks per second
- `Array List Widget` — displays enabled modules (gradient-colored)
- `Potion Status` — lists current buffs and debuffs with timers
- `Item Counter` — tracks specific items in inventory
- `Pack Display` — shows active resource pack name
- `MiniMap` — top-down radar minimap of nearby terrain
- `Timers & Stopwatches` — on-screen stopwatch and countdown timer
- `Scoreboard Customization` — reposition and clean up vanilla scoreboards
- `BossBar Overhaul` — scale and customize vanilla boss health bars

### Visual (13 modules)

- `Brightness Plus` — gamma override -1.0 to 12.0, hotkey B
- `Zoom` — FOV lock to 30 while C held
- `Time Changer` — pins world clock to noon
- `Block Outline` — forces block outline rendering
- `No Hurt Camera` — disables hurt camera tilt
- `Custom Crosshair` — stylized crosshair overlay
- `Freelook` — free-look camera while V held
- `Clean View` — hides own name tag in third person
- `No Weather` — client-side clear weather
- `Low Shield` — lowers shield model for visibility
- `Low Fire` — shrinks fire overlay
- `Motion Blur Plus` — configurable motion blur via /motionblurplus
- `Entity Culling` — DDA ray-step entity occlusion

### Movement (4 modules)

- `Auto Sprint` — continuous sprint
- `Toggle Sneak` — sneak becomes toggle
- `Inventory Walk` — WASD + jump while any screen open
- `Anti-Knockback` — configurable knockback reduction

### Combat (2 modules)

- `Combo Counter` — tracks consecutive PvP hits
- `Reach Display` — shows hit distance to 2 decimals

### Utility (6 modules)

- `Auto GG` — sends "gg" once on death in multiplayer
- `Auto Chat Messages` — rotating friendly messages on timer
- `Auto Tool` — swaps to best tool when mining
- `Chat Timestamps` — prepends timestamps to chat
- `Notifications` — toggle notifications
- `Module Toggle Alerts` — richer toggle alerts

### Content Creation (6 modules)

- `Cinematic Camera` — smooth camera with mouse smoothing
- `Cinematic Presets` — configurable camera presets
- `Screenshot Mode` — hides HUD for clean screenshots
- `Hide HUD Hotkey` — toggle all HUD with F1
- `Timelapse Helper` — position lock, compass lock, elapsed timer
- `Camera Path Recorder` — record/playback camera paths with interpolation

### Building (8 modules)

- `Block Palette Viewer` — building palette display with color groups
- `Material Calculator` — calculate materials with category breakdowns
- `Grid Overlay` — render build grid (block/chunk/vertical/horizontal)
- `Chunk Border Viewer` — customizable chunk borders
- `Build Height Indicator` — current Y, build limit, remaining height
- `Shape Preview` — ghost previews: circle, sphere, cylinder, dome
- `Blueprint Preview` — 3D wireframe box preview
- `Block Measurement` — measure distance and volume between points

### Multiplayer (8 modules)

- `Friend List` — local friends with nicknames and colors
- `Party HUD` — party member display
- `Ping Graph` — live ping with avg/min/max history
- `Server TPS Estimator` — client-side TPS estimation
- `Chat Filters` — keyword, regex, player, spam filtering
- `Quick Messages` — numpad 1–9 hotkeys for chat
- `Player Notes` — local notes about players
- `Server Bookmarks` — saved servers with IP/port/ping history

## Assets and Resources

- `src/main/resources/fabric.mod.json`
  - Fabric metadata, client/common entrypoints, dependency declarations (fabricloader ≥0.19.5, minecraft ~26.2, java ≥21), and icon path.
- `src/main/resources/fork-client.mixins.json`
  - Registers all 18 client mixins.
- `src/main/resources/assets/fork-client/lang/en_us.json`
  - Translation keys for the custom key category and keybind names.
- `src/main/resources/assets/fork-client/icon.png`
  - Mod icon used by Fabric and Mod Menu.
- `src/main/resources/assets/fork-client/font/`
  - Custom font assets.
- `src/main/resources/assets/fork-client/shaders/`
  - Shader files for motion blur and other post-processing effects.
- `src/main/resources/assets/fork-client/post_effect/`
  - Post-effect definition files for render pipeline integration.

## Config and Persistence

The controller saves a properties file to:

```text
config/fork-client.properties
```

This file stores:

- Selected click GUI category
- Enabled or disabled state for every module
- X/Y coordinates for every HUD widget
- Widget visibility state
- Per-module float and string settings (e.g., brightness value, motion blur strength, knockback reduction, minimap zoom)
- 4 built-in profiles: Default, UHC, Hypixel, Arena PvP

## Architecture Notes

- The controller owns all runtime state so the screens stay lightweight.
- The click GUI edits module state directly through the controller.
- The HUD editor manipulates widget objects in memory and immediately persists changes when closed or released.
- All mixins are in the `mixin/` package and are registered via `fork-client.mixins.json`.
- The optimization system (`optimization/` package) is fully self-contained with no dependencies on module logic.
- Entity culling results are eventually consistent (1-frame lag) but visually imperceptible.
- The server handshake system allows servers to control which feature categories are permitted.
