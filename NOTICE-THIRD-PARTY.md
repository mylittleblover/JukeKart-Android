# Third-Party Notices

JukeKart's original source code and built-in original vector artwork are licensed under the **Apache License, Version 2.0** (see [`LICENSE`](LICENSE)). The native components below retain their own licenses and notices. Their texts are included in the APK and shown under **Settings → Third-party licenses**. The source copies are in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/).

| Component | Version or source | License | Bundled text |
|---|---|---|---|
| clHCA (vgmstream) | `third_party/clhca` | ISC | `vgmstream-clhca.txt` |
| libopenmpt | 0.8.9 | BSD-3-Clause and bundled component licenses | `libopenmpt.txt`, `libopenmpt-mpt-bsd.txt`, `libopenmpt-mpt-boost.txt` |
| minimp3 | `third_party/libopenmpt/mo3-codecs/minimp3` | CC0-1.0 | `minimp3.txt` |
| dr_flac | `dfe8377` | MIT-0 | `dr-flac.txt` |
| dr_mp3 | `dfe8377` | MIT-0 | `dr-mp3.txt` |
| stb_vorbis | `2c980bb` | MIT | `stb-vorbis.txt` |
| Opus | `third_party/opus/upstream` | BSD-3-Clause | `opus.txt` |
| Game_Music_Emu | 0.6.5 | LGPL-2.1-or-later | `game-music-emu-lgpl-2.1.txt` |
| emu2413 | via Game_Music_Emu | MIT | `emu2413-mit.txt` |
| Nuked OPN2 | via Game_Music_Emu | Upstream notice | `nuked-opn2-notice.txt` |

Game_Music_Emu builds as the shared library `libgme.so`. The pinned source and build files are supplied under [`third_party/game-music-emu/`](third_party/game-music-emu/).

## Maven components

The following direct runtime dependencies are declared by the [Android build](app/build.gradle.kts). Consult each artifact's metadata and license notices for its distribution terms and transitive dependencies.

| Component | Declared version | License |
|---|---|---|
| Oboe | 1.10.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 |
| AndroidX Lifecycle Compose | 2.8.7 | Apache-2.0 |
| AndroidX Media | 1.7.0 | Apache-2.0 |
| Jetpack Compose UI, Foundation, Material and Material 3 | Compose BOM 2025.04.01 | Apache-2.0 |
| Kotlin standard library | 2.1.20 | Apache-2.0 |
| Kotlin Coroutines Android | 1.10.1 | Apache-2.0 |

## Game references and assets

The Beta APK contains catalog and route metadata and project artwork for course badges, covers, the map and the fallback unlock animation. The launcher and notification icons retain their existing designs. Game audio, game video and extracted game textures have been removed from the current source tree and the Beta package. Optional game media comes from user-supplied files.

JukeKart is an independent fan project. Nintendo has no affiliation with it and does not endorse or sponsor it. Game names, trademarks and game assets remain the property of their respective owners.
