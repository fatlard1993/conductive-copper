# Conductive Copper - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.
