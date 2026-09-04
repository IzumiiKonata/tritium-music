# Third-Party Notices

Tritium Music includes vendored third-party source code under `common/src/main/java/tritium/music/repackage/`.
Those components remain governed by their respective licenses and copyright notices rather than the project's LGPL-3.0-only license.

| Component | Vendored package | License |
| --- | --- | --- |
| Processing Sound | `processing.sound` | LGPL-2.1-only |
| Jipes | `com.tagtraum.jipes` | LGPL-2.1-only |
| JLayer | `javazoom.jl` | LGPL-2.0-or-later |
| jFLAC | `org.kc7bfi.jflac` | LGPL-2.0-or-later |
| JSyn and SoftSynth support code | `com.jsyn`, `com.softsynth` | Apache-2.0 |
| Beat This! `final0` model | `assets/tritium-music/automix/beat_this.onnx` | MIT |
| beat-this-rs Mel spectrogram model | `assets/tritium-music/automix/mel_spectrogram.onnx` | MIT |
| Spotify Basic Pitch ICASSP 2022 model | `assets/tritium-music/automix/basic_pitch.onnx` | Apache-2.0 |
| SoundTouch 2.3.1 | Runtime audio processor | LGPL-2.1-or-later |
| SoundTouch JNI | Java/native binding | MIT |
| ONNX Runtime | Runtime dependency | MIT |

Copyright and license notices embedded in the vendored source files must be retained.

Beat This! original work copyright (c) 2024 Institute of Computational Perception, JKU Linz, Austria.

beat-this-rs copyright (c) 2025 danigb.

Basic Pitch copyright (c) 2022 Spotify AB. Its Apache License 2.0 is included as
`LICENSE_BASIC_PITCH_tritium-music`.

SoundTouch copyright (c) Olli Parviainen. Its LGPL 2.1 license is included as
`LICENSE_SOUNDTOUCH_tritium-music`.

SoundTouch JNI copyright (c) 2021-2022 Tianscar. Its MIT license is included as
`LICENSE_SOUNDTOUCH_JNI_tritium-music`.

ONNX Runtime copyright (c) Microsoft Corporation.

Permission is hereby granted, free of charge, to any person obtaining a copy of the Beat This! and
beat-this-rs software and associated documentation files, to deal in the Software without
restriction, including without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
