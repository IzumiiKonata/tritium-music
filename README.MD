# Tritium Music

Tritium Music is a NetEase Cloud Music client mod for Minecraft 26.2.

## External assets

The PingFang fonts and the AutoMix ONNX models are too large to ship inside the mod jar, so they live
in the repository root and are downloaded to the game directory on first launch, with progress shown
in the UI:

| Repository path | Installed to | Used by |
| --- | --- | --- |
| `fonts/pf_normal.ttf` | `<game dir>/tritium-music/assets/fonts/` | main UI font |
| `fonts/pf_middleblack.ttf` | `<game dir>/tritium-music/assets/fonts/` | bold UI font |
| `automix_models/mel_spectrogram.onnx` | `<game dir>/tritium-music/assets/automix_models/` | AutoMix beat tracking |
| `automix_models/beat_this.onnx` | `<game dir>/tritium-music/assets/automix_models/` | AutoMix beat tracking |
| `automix_models/basic_pitch.onnx` | `<game dir>/tritium-music/assets/automix_models/` | AutoMix harmonic analysis |

Downloads are verified against a SHA-256 digest, fetched from GitHub first, and automatically routed
through a `gh-proxy` mirror when GitHub is unreachable (mainland China). The download starts
silently in the background on client startup and its progress shows up as a panel at the top of the
music screen (**M**); a failed download can be retried by clicking that panel. Until the fonts
arrive the UI falls back to a bundled Latin font and a system font, then reloads once the real fonts
land.

The assets must exist on the configured ref of the repository, otherwise every download route
answers with 404 and the music screen reports the failure.

Optional system properties:

- `-Dtritium.assets.repository=<owner>/<repo>` (default `IzumiiKonata/tritium-music`)
- `-Dtritium.assets.ref=<branch or tag>` (default `main`)
- `-Dtritium.assets.mirrors=<comma separated prefixes>` (default `gh-proxy.com`, `ghfast.top`, `ghproxy.net`)
- `-Dtritium.assets.dir=<directory>` to load the assets from a local folder instead of downloading

When the game runs from a source checkout, the assets are picked up straight from the repository root,
so development runs never download anything.

## License

Copyright (C) 2026 IzumiiKonata.

Tritium Music is licensed under the GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`). See [LICENSE](LICENSE) and [COPYING](COPYING).

Vendored components retain their respective licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
