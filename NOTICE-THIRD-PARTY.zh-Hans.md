# 第三方开源许可声明

**简体中文** | [繁體中文](NOTICE-THIRD-PARTY.zh-Hant.md) | [English](NOTICE-THIRD-PARTY.md) | [日本語](NOTICE-THIRD-PARTY.ja.md)

JukeKart 的原创源代码与内置原创矢量图形采用 **Apache License, Version 2.0** 许可（详见 [`LICENSE`](LICENSE)）。以下原生组件保留其各自原有的许可证与声明，完整文本已随 APK 打包并在应用内 **设置 → 第三方开源许可** 中展示，源码副本位于 [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)。

| 组件 | 版本或来源 | 许可证 | 打包文本 |
|---|---|---|---|
| clHCA (vgmstream) | `third_party/clhca` | ISC | `vgmstream-clhca.txt` |
| libopenmpt | 0.8.9 | BSD-3-Clause 及附带组件许可 | `libopenmpt.txt`, `libopenmpt-mpt-bsd.txt`, `libopenmpt-mpt-boost.txt` |
| minimp3 | `third_party/libopenmpt/mo3-codecs/minimp3` | CC0-1.0 | `minimp3.txt` |
| dr_flac | `dfe8377` | MIT-0 | `dr-flac.txt` |
| dr_mp3 | `dfe8377` | MIT-0 | `dr-mp3.txt` |
| stb_vorbis | `2c980bb` | MIT | `stb-vorbis.txt` |
| Opus | `third_party/opus/upstream` | BSD-3-Clause | `opus.txt` |
| Game_Music_Emu | 0.6.5 | LGPL-2.1-or-later | `game-music-emu-lgpl-2.1.txt` |
| emu2413 | 经由 Game_Music_Emu | MIT | `emu2413-mit.txt` |
| Nuked OPN2 | 经由 Game_Music_Emu | 上游声明 | `nuked-opn2-notice.txt` |

Game_Music_Emu 以动态共享库 `libgme.so` 形式构建。其固定版本的源代码与构建文件位于 [`third_party/game-music-emu/`](third_party/game-music-emu/)。

## Maven 组件

[Android 构建配置](app/build.gradle.kts)声明了以下直接运行时依赖。有关其分发条款及传递依赖，请查阅各构件自身的元数据与许可声明。

| 组件 | 声明版本 | 许可证 |
|---|---|---|
| Oboe | 1.10.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 |
| AndroidX Lifecycle Compose | 2.8.7 | Apache-2.0 |
| AndroidX Media | 1.7.0 | Apache-2.0 |
| Jetpack Compose UI, Foundation, Material and Material 3 | Compose BOM 2025.04.01 | Apache-2.0 |
| Kotlin standard library | 2.1.20 | Apache-2.0 |
| Kotlin Coroutines Android | 1.10.1 | Apache-2.0 |

## 游戏名称与素材声明

Beta APK 仅包含曲目目录与路线元数据，以及项目原创的赛道徽章、封面、地图与回退解锁动画；应用图标与通知图标沿用现有样式。当前源码树与 Beta 安装包中均已移除游戏音频、游戏视频与提取的游戏贴图；可选的游戏媒体资源均来自用户自行提供的文件。

JukeKart 是独立的爱好者项目，与 Nintendo 无关联，亦未经 Nintendo 认可或赞助。相关游戏名称、商标及游戏素材均归各自权利人所有。
