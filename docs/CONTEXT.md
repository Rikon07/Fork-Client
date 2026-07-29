## 🔧 Mod Info

- **Mod Name:** `Fork Client`
- **Mod ID:** `com.mahidx7.forkclient`
- **Author:** `Mahidx7`
- **Package Name:** `com.mahidx7.forkclient`
- **Minecraft Version:** `26.2`
- **Fabric Loader Version:** `0.19.3`
- **Fabric API Version:** `0.156.0+26.2`
- **Java Version (JDK):** `25`
- **Mod Type:** `client`

---

## 🧠 Mod Functionality

📋 Mod Details

This is a Client-Side HUD & Utility Mod designed to improve Minecraft's UI, HUD, PvP experience, visualization, and quality-of-life features. This mod is for Minecraft 26.1.2 and Fabric. After pressing right shift key the GUI will open. Pressing right shift key again will close the GUI.

The mod features a Click GUI, HUD Editor, Draggable Widgets, Custom Rendering System, Notifications, and various PvP/QoL modules.

🎨 GUI System

Modern Click GUI
Module Categories
Toggle Buttons
Sliders
Dropdown Menus
Color Picker
Keybind Manager
Module Configuration Screen
HUD Editor
Draggable HUD Elements
Persistent Settings

🖥️ HUD Features

Performance
FPS Counter
Memory Usage Display
Ping Display
Server Information Display
Player Information
Coordinates Display
Direction / Compass HUD
Speed Display
Biome Display
Day Counter
Clock HUD
Saturation Display
Combat HUD
CPS Counter
Combo Counter
Armor HUD
Potion Effects HUD
Misc HUD
Resource Pack Display
Chat Timestamps
Array List Module Display
Scoreboard Customization

⚔️ PvP Features

Keystrokes Overlay
Custom Crosshair
Damage Indicator
Hitbox Viewer
Block Overlay
Nametag Icons
Tool Warning System

🎥 Visual Features

Fullbright
Motion Blur
Zoom
Time Changer
Particle Customization
No Hurt Camera Effect
No Boss Bar
Ping Overlay

🎮 Movement Features

Toggle Sneak
Auto Sprint

💬 Utility Features

Auto GG
Auto Text Messages
Notification System
HUD Notifications
Module Toggle Notifications

🏗️ Architecture

Fabric Client Mod
Modular Design
Mixins-Based Rendering
Custom HUD Rendering Engine
Draggable Components
Configurable Modules
Category-Based Module Management

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