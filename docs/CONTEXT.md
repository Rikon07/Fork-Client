## 🔧 Mod Info

- **Mod Name:** `Fork Client`
- **Mod ID:** `com.mahidx7.forkclient`
- **Author:** `Mahidx7`
- **Package Name:** `com.mahidx7.forkclient`
- **Minecraft Version:** `26.2`
- **Fabric Loader Version:** `0.19.5`
- **Fabric API Version:** `0.160.0+26.2`
- **Java Version (JDK):** `25`
- **Mod Type:** `client`

---

## 🧠 Mod Functionality

### 📋 Mod Details

**Fork Client** (v1.2.1+26.2) is a client-side Fabric mod for Minecraft 26.2 providing a click GUI, customizable HUD, utility modules, rendering tweaks, building helpers, multiplayer tools, and a performance optimization backend. All features are permission-gated via a server handshake system.

Press **Right Shift** to open the Click GUI; press again to close. Press **H** to open the HUD Editor for dragging widgets.

---

### Core Architecture

| Component | File(s) | Purpose |
|---|---|---|
| Common bootstrap | `ForkClient.java` | Registers network payloads (handshake + permissions), server-side receiver |
| Client bootstrap | `ForkClientClient.java` | Registers networking, initializes controller, motion blur, optimization system |
| Central controller | `ForkClientController.java` | Module/widget registry, keybinds, config persistence, all tick-based logic, HUD rendering |
| Permissions | `FeaturePermissions.java` | Server-granted feature gates: `MOVEMENT`, `RENDERING`, `UTILITY`. Single-player = all allowed |
| Network | `ForkClientHandshakePayload.java`, `MovementPermissionsPayload.java` | Client→Server handshake with mod version; Server→Client permission grants |

---

### Module System (~50 modules across 8 categories)

#### VISUAL (13 modules)
- **Brightness Plus** — Gamma override from -1.0 to 12.0, hotkey `B`, adjust with `=`/`-`
- **Zoom** — FOV lock to 30 while `C` held
- **Time Changer** — Pins world clock to noon (tick 6000)
- **Block Outline** — Forces block outline always
- **No Hurt Camera** — Disables hurt tilt
- **Custom Crosshair** — Stylized crosshair overlay (suppresses vanilla)
- **Freelook** — Free-look camera while `V` held, player body stays fixed
- **Clean View** — Hides own name tag in third person
- **No Weather** — Client-side clear weather (saves/restores rain/thunder)
- **Low Shield** — Lowers shield model for better visibility
- **Low Fire** — Shrinks fire overlay
- **Motion Blur Plus** — Configurable motion blur via `/motionblurplus` command, keybind `M`
- **BossBar Overhaul** — Scales vanilla boss health bars

#### MOVEMENT (4 modules)
- **Auto Sprint** — Continuous sprint (via `ClientInputMixin`)
- **Toggle Sneak** — Sneak becomes toggle
- **Inventory Walk** — WASD + jump while any screen is open (reads raw GLFW keys)
- **Anti-Knockback** — Configurable horizontal/vertical knockback reduction (0–100%)

#### HUD (19 widgets)
`coords`, `fps`, `ping`, `memory`, `direction`, `biome`, `saturation`, `day_counter`, `clock`, `server`, `armor_durability`, `keystrokes`, `cps`, `performance`, `array_list`, `potion_status`, `combo`, `reach`, `item_counter`, `pack_display`, `minimap`, `timers`, `scoreboard`, `timelapse_info`, `build_height`, `measurements`, `ping_graph`, `tps_display`, `palette`, `materials`, `shape_preview`, `blueprint_preview`

#### COMBAT (2 modules)
- **Combo Counter** — Tracks consecutive PvP hits with timeout reset
- **Reach Display** — Shows hit distance to 2 decimal places

#### UTILITY (6 modules)
- **Auto GG** — Sends "gg" once on death in multiplayer
- **Auto Chat Messages** — Rotating friendly messages on a 3-minute timer
- **Auto Tool** — Swaps to best tool when mining (evaluates `getDestroySpeed`)
- **Chat Timestamps** — Prepends timestamps to chat messages
- **Notifications** — Toggle notifications
- **Module Toggle Alerts** — Richer toggle alerts

#### CONTENT CREATION (6 modules)
- **Cinematic Camera** — Smooth camera with adjustable mouse smoothing
- **Cinematic Presets** — Configurable presets (slow pan, orbit, dolly, zoom, fly-through)
- **Screenshot Mode** — Hides HUD for clean screenshots
- **Hide HUD Hotkey** — Toggle all HUD with `F1`
- **Timelapse Helper** — Position lock, compass lock, elapsed timer
- **Camera Path Recorder** — Record/playback camera paths with interpolation, keybind `J`

#### BUILDING (8 modules)
- **Block Palette Viewer** — Building palette display with color groups
- **Material Calculator** — Calculate materials with category breakdowns
- **Grid Overlay** — Render build grid (block/chunk/vertical/horizontal)
- **Chunk Border Viewer** — Customizable chunk borders
- **Build Height Indicator** — Current Y, build limit, remaining height
- **Shape Preview** — Ghost previews: circle, sphere, cylinder, dome (1–32 radius)
- **Blueprint Preview** — 3D wireframe box preview (configurable W/H/D 1–64)
- **Block Measurement** — Measure distance, volume between two points

#### MULTIPLAYER (8 modules)
- **Friend List** — Local friends with nicknames, colors, online indicators
- **Party HUD** — Party member display
- **Ping Graph** — Live ping with avg/min/max history (200 samples)
- **Server TPS Estimator** — Client-side TPS estimation via game time advancement
- **Chat Filters** — Keyword, regex, player, spam filtering
- **Quick Messages** — Numpad 1–9 hotkeys for chat messages
- **Player Notes** — Local notes about players
- **Server Bookmarks** — Saved servers with IP/port/ping history

---

### HUD System

- **30 draggable widgets** registered via `ForkHudEditorScreen`
- Widgets are rendered by the `fork_overlay` HUD element attached after `MISC_OVERLAYS`
- **Armor Durability HUD** — Dedicated component with configurable scale, spacing, slot toggles, horizontal/vertical layout, color-coded durability (red → yellow → green)
- **Array List** — Gradient-colored list of enabled modules
- Vanilla elements (boss bar, scoreboard, crosshair) are conditionally replaced

---

### Rendering Tweaks (Mixins — 18 files)

| Mixin | Target | Purpose |
|---|---|---|
| `VelocityMixin` | `LivingEntity` | Anti-knockback velocity override |
| `ClientInputMixin` | `LocalPlayer` | Auto-sprint injection |
| `FogRendererMixin` | `FogRenderer` | Brightness Plus gamma override |
| `GameRendererMixin` | `GameRenderer` | Freelook camera angle override, motion blur post-processing |
| `LevelRendererMixin` | `LevelRenderer` | Entity culling injection, optimization hooks |
| `LivingEntityRendererMixin` | `LivingEntityRenderer` | Clean View (own nametag hide) |
| `ChatFilterMixin` / `ChatComponentMixin` / `ChatScreenMixin` | Chat system | Chat timestamps, filters |
| `CameraMixin` | `Camera` | Freelook camera isolation |
| `ItemInHandRendererMixin` | `ItemInHandRenderer` | Low Shield/Low Fire model adjustments |
| `ScreenEffectRendererMixin` | `ScreenEffectRenderer` | Fire overlay shrink |
| `LevelExtractorMixin` | `Level` | World data extraction for TPS/weather |
| `LightmapRenderStateExtractorMixin` | Lightmap | Brightness Plus lightmap injection |

---

### Performance Optimization System

| Subsystem | Files | Function |
|---|---|---|
| **ChunkBuildScheduler** | `chunk/ChunkBuildScheduler.java`, `ChunkBuilderThread.java`, etc. | Thread-pooled async chunk meshing with dirty-section scheduling |
| **OcclusionCuller** | `occlusion/OcclusionCuller.java`, `SectionTree.java` | Spatial visibility tree for section-level frustum/occlusion culling |
| **RenderRegionManager** | `region/RenderRegionManager.java`, `RenderRegion.java` | Groups sections into GPU upload regions |
| **FrameBudgetManager** | `FrameBudgetManager.java` | Per-frame upload time budget to prevent stalls |
| **SectionStorage** | `storage/SectionStorage.java` | Section state storage (dirty flags, rebuild tracking) |
| **ClonedChunkSectionCache** | `world/ClonedChunkSectionCache.java` | Cached section snapshots for meshing |
| **LevelSliceView** | `world/LevelSliceView.java` | Thread-safe cross-section view for chunk builders |

---

### Entity Culling

- **DDA ray-step path-tracing** from camera to each entity
- **Double-buffered** visibility results (reads previous frame, writes current)
- **Async trace pool** (`availableProcessors - 1` threads)
- Uses `isSolidRender()` (same predicate as vanilla face-culling) to detect occluding blocks
- Distance range: 1.5–128 blocks

---

### Keybinds

| Key | Action |
|---|---|
| `Right Shift` | Open Click GUI |
| `H` | Open HUD Editor |
| `K` | Armor Durability Config |
| `C` | Zoom |
| `V` | Freelook |
| `F1` | Hide HUD |
| `J` | Camera Path Record/Stop |
| `B` | Brightness Plus Toggle |
| `=` / `-` | Brightness Plus Adjust |
| `M` | Motion Blur Plus Settings |
| Numpad `1–9` | Quick Messages |

---

### Config

- Persisted to disk via `Properties` files
- Per-module float/string settings, boolean states, widget positions
- 4 built-in profiles: `Default`, `UHC`, `Hypixel`, `Arena PvP`

---

### Assets

- Custom fonts, shaders (`shaders/`), post effects (`post_effect/`), language files (`lang/`), mod icon

---

## 📦 Output Instructions

The AI must:

- ✅ List all **Java source files**  
  - Show full relative paths (e.g., `src/main/java/com/...`)  
  - Give a one-line description of each file's purpose

- ✅ Write **full, working Java code** for every file  
  - No placeholders, stubs, or missing logic  
  - Fully import-ready and compilable

- ✅ Provide a valid, working `fabric.mod.json`

- ✅ Register all components correctly:
  - Items, blocks, screens, overlays, particles, keybinds, etc.  
  - Client or server init logic as appropriate

- ✅ Include all required **import statements** and **annotations**

---

## 🎨 Assets (If Applicable)

If assets are needed, AI must:

- 📂 **List all required assets**, including:
  - Textures (`.png`)
  - Lang files (`lang/en_us.json`)
  - Models and blockstates (`.json`)
  - Sounds or GUI elements

- 📄 **Provide full content** and **exact paths** for each file:
  - Example: `src/main/.../exampleMixin.java`
  - Example: `src/main/resources/assets/[modid]/textures/item/example_item.png`
  - Example: `src/main/resources/assets/[modid]/lang/en_us.json`

---

## 📘 Documentation Output

Also generate a brief documentation file and save it as:

> 📁 `docs/OUTPUT.md`

This file must:

- ✅ Explain what the mod does  
- ✅ Summarize all the components (classes, assets, events)  
- ✅ Describe where everything is registered and how it works  
- ✅ Be written for the user to understand how the mod is structured  
- ✅ Use Markdown formatting (headings, bullets, code blocks)

---

## ⚙️ Optional Features

If applicable, include:

- Mixin setup  
- Config file support (Cloth Config or JSON)  
- Command registration  
- Client/server networking  
- Runtime environment checks  
- Shader or render layer support

---

## ❌ Rules

- ❌ Do not skip any code  
- ❌ Do not use vague notes like “implement this later”  
- ❌ Do not generate placeholder files or comments  
- ✅ Output must be **complete and build-ready** from the start