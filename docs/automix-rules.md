# 硬性 AutoMix 规则

启用 AutoMix 后，游戏会从 `.minecraft/tritium-music/automix-rules.json` 读取规则；开发环境对应 `run/tritium-music/automix-rules.json`。首次播放时会自动创建空文件。保存文件后，下一次选曲会自动重载，不需要重启游戏。

规则按网易云歌曲 ID 精确、有方向地匹配。`111 -> 222` 不会匹配 `222 -> 111`。同一歌曲对出现多次时，使用文件中第一条启用且有效的规则。

```json
{
  "rules": [
    {
      "enabled": true,
      "outgoingSongId": 111,
      "incomingSongId": 222,
      "outgoingStartMillis": 173500,
      "incomingStartMillis": 32000,
      "durationMillis": 8500,
      "style": "MUSICAL_BLEND",
      "playbackRate": 1.02,
      "pitchShiftSemitones": -1,
      "tempoRampMillis": 2500,
      "eqStrength": 0.8
    }
  ]
}
```

字段含义：

- `outgoingSongId`：上一首歌的网易云歌曲 ID。
- `incomingSongId`：下一首歌的网易云歌曲 ID。
- `outgoingStartMillis`：上一首播放到此时间点时开始过渡。
- `incomingStartMillis`：下一首从此时间点进入。
- `durationMillis`：过渡持续时间，至少 900 毫秒。
- `style`：`GAPLESS`、`CROSSFADE`、`NATURAL_FADE`、`SILENCE_SKIP` 或 `MUSICAL_BLEND`。`GAPLESS` 会忽略过渡时长、EQ、变速和移调，直接从指定时间点切换到下一首。
- `playbackRate`：过渡前将上一首变速到此倍率，范围 0.5 到 2，默认 1。
- `pitchShiftSemitones`：上一首移调半音数，范围 -12 到 12，默认 0。
- `tempoRampMillis`：变速和移调在过渡前渐变的时间，默认 0。
- `eqStrength`：过渡 EQ 强度，范围 0 到 1，默认 0.55。

命中规则时不会采用自动分析得到的过渡点。没有命中、规则被禁用或规则无效时，仍采用原来的 AutoMix 行为。
