# Tritium Music

Tritium Music is a NetEase Cloud Music client mod for Minecraft 26.2.

## Loaders

- Fabric 26.2
- NeoForge 26.2

The Minecraft-independent backend, audio engine, UI implementation, Mixins, and assets are shared. Loader modules contain the loader-specific entrypoints, lifecycle wiring, key registration, HUD registration, metadata, and access configuration.

## Build

Java 25 is required.

```bash
./gradlew :fabric:build
./gradlew :neoforge:build
```

Artifacts are written to:

```text
fabric/build/libs/
neoforge/build/libs/
```

Development clients:

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

## License

This project is available under the CC0 license.
