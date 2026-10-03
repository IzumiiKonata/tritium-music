# Tritium Music

Tritium Music is a NetEase Cloud Music client mod for Minecraft 26.2.

## External assets

Everything bulky lives in the repository root instead of the mod jar and is downloaded into
`<game dir>/tritium-music/assets/` on client startup, with progress shown in the music screen:

| Repository path | Installed to | Used by |
| --- | --- | --- |
| `fonts/pf_normal.ttf` | `assets/fonts/` | main UI font |
| `fonts/pf_middleblack.ttf` | `assets/fonts/` | bold UI font |
| `automix_models/mel_spectrogram.onnx` | `assets/automix_models/` | AutoMix beat tracking |
| `automix_models/beat_this.onnx` | `assets/automix_models/` | AutoMix beat tracking |
| `automix_models/basic_pitch.onnx` | `assets/automix_models/` | AutoMix harmonic analysis |
| `onnxruntime_native/win-x64/…` | `assets/onnxruntime_native/win-x64/` | ONNX Runtime natives (Windows x64) |
| `onnxruntime_native/linux-x64/…` | `assets/onnxruntime_native/linux-x64/` | ONNX Runtime natives (Linux x64) |
| `onnxruntime_native/linux-aarch64/…` | `assets/onnxruntime_native/linux-aarch64/` | ONNX Runtime natives (Linux arm64) |
| `onnxruntime_native/osx-aarch64/…` | `assets/onnxruntime_native/osx-aarch64/` | ONNX Runtime natives (macOS arm64) |

Only the natives of the running platform are downloaded. The mod jar ships the ONNX Runtime Java
classes with all natives stripped out (~0.15 MB instead of ~53 MB) and points
`onnxruntime.native.path` at the downloaded directory, so the shippable jar is ~10 MB (Fabric) /
~7 MB (Forge) instead of ~150 MB.

Downloads are verified against a SHA-256 digest, fetched from GitHub first, and automatically routed
through a `gh-proxy` mirror when GitHub is unreachable (mainland China). Progress shows up as a panel
at the top of the music screen (**M**), and a failed download can be retried by clicking that panel.

### Fail-safe

A failed download is retried automatically three times with growing delays (30 s, 2 min, 5 min).
If it still fails:

- the affected features are disabled instead of breaking: AutoMix silently falls back to rhythm-only
  transition planning, and the UI falls back to the bundled Latin font plus a system font;
- the music screen panel reports the failure and which features are disabled, and clicking it retries;
- the AutoMix settings toggles in the music screen are greyed out with the reason;
- the client tells the player through chat and a toast, and logs `[asset]` lines.

Optional system properties:

- `-Dtritium.assets.repository=<owner>/<repo>` (default `IzumiiKonata/tritium-music`)
- `-Dtritium.assets.ref=<branch or tag>` (default `main`)
- `-Dtritium.assets.mirrors=<comma separated prefixes>` (default `gh-proxy.com`, `ghfast.top`, `ghproxy.net`)
- `-Dtritium.assets.retryDelays=<comma separated seconds>` (default `0,30,120,300`)
- `-Dtritium.assets.dir=<directory>` to load the assets from a local folder instead of downloading

The assets must exist on the configured ref of the repository, otherwise every download route answers
with 404 and the music screen reports the failure. When the game runs from a source checkout the
assets are picked up straight from the repository root, so development runs never download anything.

## License

Copyright (C) 2026 IzumiiKonata.

Tritium Music is licensed under the GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`). See [LICENSE](LICENSE) and [COPYING](COPYING).

Vendored components retain their respective licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
