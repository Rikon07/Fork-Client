# Fork Client

Fork Client is a client-side Fabric mod for Minecraft. It provides a configurable
click GUI, draggable HUD widgets, utility modules, building tools, visual
enhancements, and client-side performance optimizations.

## Supported versions

- Minecraft `26.3`
- Fabric Loader `0.19.5` or newer
- Fabric API `0.161.0+26.3`
- Java 21 or newer

The mod is client-only and requires Fabric API.

## Features

- Right-shift click GUI for managing modules and categories
- Draggable HUD editor with persistent widget positions and visibility
- HUD widgets for coordinates, FPS, memory, server, ping, CPS, keystrokes,
  armor, potions, direction, time, and more
- Utility modules including auto sprint, auto tool, inventory walk, zoom,
  toggle sneak, and no-hurt-camera
- Building helpers such as grid overlays, chunk borders, shape previews,
  blueprint previews, and material calculation
- Visual features including motion blur, entity culling, custom crosshair,
  brightness, freelook, and block outlines
- Persistent module, widget, and profile configuration
- Internal rendering and world-performance optimizations

See [`docs/OUTPUT.md`](docs/OUTPUT.md) for the detailed feature and architecture
overview, and [`docs/PERFORMANCE_REPORT.md`](docs/PERFORMANCE_REPORT.md) for
performance implementation details.

## Building

### Windows

```powershell
.\gradlew.bat build
```

### Linux and macOS

```bash
./gradlew build
```

The compiled mod and sources JARs are written to `build/libs/`.

## Running the client

Use the Fabric Loom development client with:

```powershell
.\gradlew.bat runClient
```

On Linux or macOS:

```bash
./gradlew runClient
```

The development runtime uses the `run/` directory. Its Fork Client settings
are stored in `run/config/fork-client.properties`.

## Project layout

```text
src/main/java/com/mahidx7/forkclient/
├── client/          Client controller, screens, HUD, modules, and permissions
├── mixin/           Minecraft mixins
└── optimization/   Client performance systems

src/main/resources/
├── assets/fork-client/  Text, language, shader, font, and icon resources
├── fabric.mod.json      Fabric mod metadata
└── fork-client.mixins.json
```

The main and client entrypoints are
`com.mahidx7.forkclient.ForkClient` and
`com.mahidx7.forkclient.ForkClientClient`.

## Useful Gradle tasks

| Task | Purpose |
| --- | --- |
| `build` | Compile, test, remap, and package the mod |
| `runClient` | Launch the Fabric development client |
| `printClasspath` | Print the compile classpath |
| `build --refresh-dependencies` | Re-resolve Gradle and Minecraft dependencies |

## Links

- [Project website](https://forkclient.vercel.app/)
- [Source code](https://github.com/Rikon07/Fork-Client)
- [Issue tracker](https://github.com/Rikon07/Fork-Client/issues)
- [Modrinth](https://modrinth.com/mod/fork-client)

## License

Fork Client is released under the [CC0 1.0 Universal](LICENSE) license.
