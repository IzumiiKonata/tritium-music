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

Copyright (C) 2026 IzumiiKonata.

Tritium Music is licensed under the GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`). See [LICENSE](LICENSE) and [COPYING](COPYING).

Vendored components retain their respective licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
