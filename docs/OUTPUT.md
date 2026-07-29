# Fork Client Output

## Overview

`Fork Client` is a client-side Fabric mod for Minecraft `26.2` that adds:

- A right-shift click GUI for toggling modules
- A draggable HUD editor with persistent widget positions
- HUD widgets for coordinates, performance, time, server info, CPS, keystrokes, and enabled modules
- Utility modules such as auto sprint and toggle sneak
- Visual modules such as fullbright, zoom, block outline forcing, time changer, and no hurt camera
- Toast-style notifications when module or widget visibility changes

## Java Components

### Core Entry Points

- `src/main/java/com/mahidx7/forkclient/ForkClient.java`
  - Common bootstrap with mod id, logger, and identifier helper.
- `src/main/java/com/mahidx7/forkclient/ForkClientClient.java`
  - Client entrypoint that initializes the controller and all client-only systems.

### Client Systems

- `src/main/java/com/mahidx7/forkclient/client/ForkClientController.java`
  - Central runtime state for modules, widgets, keybinds, HUD rendering, notifications, movement helpers, and config persistence.
- `src/main/java/com/mahidx7/forkclient/client/ForkClickGuiScreen.java`
  - In-game click GUI for category browsing and module toggling.
- `src/main/java/com/mahidx7/forkclient/client/ForkHudEditorScreen.java`
  - HUD editor for dragging widgets, toggling visibility, and returning to the click GUI.

### Mixins

- `src/main/java/com/mahidx7/forkclient/mixin/GameRendererMixin.java`
  - Cancels the hurt camera bob when the `No Hurt Camera` module is enabled.

## Registered Systems

### Entrypoints

- `main`
  - `com.mahidx7.forkclient.ForkClient`
- `client`
  - `com.mahidx7.forkclient.ForkClientClient`

### Keybinds

- `Right Shift`
  - Opens or closes the click GUI.
- `H`
  - Opens or closes the HUD editor.
- `C`
  - Activates zoom while held if the `Zoom` module is enabled.

### Fabric Events and APIs

- `ClientTickEvents.END_CLIENT_TICK`
  - Processes keybind presses, movement modules, zoom/fullbright/time logic, block outline state, CPS tracking, and movement speed updates.
- `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ...)`
  - Inserts the custom Fork Client HUD overlay into the vanilla HUD pipeline.

## Modules

### HUD

- `Clock Widget`
- `Coordinates Widget`
- `Performance Widget`
- `Server Widget`
- `Keystrokes Widget`
- `CPS Widget`
- `Array List Widget`

Each HUD module is backed by a draggable widget and stored in the config file.

### Visual

- `Fullbright`
- `Zoom`
- `Time Changer`
- `Block Outline`
- `No Hurt Camera`

### Movement

- `Auto Sprint`
- `Toggle Sneak`

### Utility

- `Notifications`

## Assets and Resources

- `src/main/resources/fabric.mod.json`
  - Fabric metadata, client/common entrypoints, dependency declarations, and icon path.
- `src/main/resources/fork-client.mixins.json`
  - Registers the client mixin used by the hurt-camera module.
- `src/main/resources/assets/fork-client/lang/en_us.json`
  - Translation keys for the custom key category and keybind names.
- `src/main/resources/assets/fork-client/icon.png`
  - Mod icon used by Fabric.

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

## Structure Notes

- The controller owns all runtime state so the screens stay lightweight.
- The click GUI edits module state directly through the controller.
- The HUD editor manipulates widget objects in memory and immediately persists changes when closed or released.
- The mixin is intentionally small and only guards a single visual effect.
