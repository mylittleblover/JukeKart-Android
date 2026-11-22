# 第三方授權條款

[简体中文](NOTICE-THIRD-PARTY.zh-Hans.md) | **繁體中文** | [English](NOTICE-THIRD-PARTY.md) | [日本語](NOTICE-THIRD-PARTY.ja.md)

JukeKart 的原創原始碼與內建原創向量圖形採用 **Apache License, Version 2.0** 授權（詳見 [`LICENSE`](LICENSE)）。以下原生元件保留各自原有的授權與聲明，完整條款文字已隨 APK 封裝並可於應用程式內 **設定 → 第三方開源許可** 查閱，副本位於 [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)。

| 元件 | 版本或來源 | 授權條款 | 隨附文本 |
|---|---|---|---|
| clHCA (vgmstream) | `third_party/clhca` | ISC | `vgmstream-clhca.txt` |
| libopenmpt | 0.8.9 | BSD-3-Clause 及隨附元件授權 | `libopenmpt.txt`, `libopenmpt-mpt-bsd.txt`, `libopenmpt-mpt-boost.txt` |
| minimp3 | `third_party/libopenmpt/mo3-codecs/minimp3` | CC0-1.0 | `minimp3.txt` |
| dr_flac | `dfe8377` | MIT-0 | `dr-flac.txt` |
| dr_mp3 | `dfe8377` | MIT-0 | `dr-mp3.txt` |
| stb_vorbis | `2c980bb` | MIT | `stb-vorbis.txt` |
| Opus | `third_party/opus/upstream` | BSD-3-Clause | `opus.txt` |
| Game_Music_Emu | 0.6.5 | LGPL-2.1-or-later | `game-music-emu-lgpl-2.1.txt` |
| emu2413 | 經由 Game_Music_Emu | MIT | `emu2413-mit.txt` |
| Nuked OPN2 | 經由 Game_Music_Emu | 上游聲明 | `nuked-opn2-notice.txt` |

Game_Music_Emu 以動態共享函式庫 `libgme.so` 形式建置。其固定版本的原始碼與建置檔案位於 [`third_party/game-music-emu/`](third_party/game-music-emu/)。

## Maven 元件

[Android 建置設定](app/build.gradle.kts)宣告了下列直接執行期相依套件。關於散佈條款與遞移相依性，請參閱各構件的詮釋資料與授權聲明。

| 元件 | 宣告版本 | 授權條款 |
|---|---|---|
| Oboe | 1.10.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 |
| AndroidX Lifecycle Compose | 2.8.7 | Apache-2.0 |
| AndroidX Media | 1.7.0 | Apache-2.0 |
| Jetpack Compose UI, Foundation, Material and Material 3 | Compose BOM 2025.04.01 | Apache-2.0 |
| Kotlin standard library | 2.1.20 | Apache-2.0 |
| Kotlin Coroutines Android | 1.10.1 | Apache-2.0 |

## 遊戲參照與素材聲明

Beta APK 僅包含曲目目錄與路線詮釋資料，以及專案原創的賽道徽章、封面、地圖與備援解鎖動畫；啟動圖示與通知圖示沿用現有設計。目前原始碼樹與 Beta 安裝包中皆已移除遊戲音訊、遊戲影片與擷取的遊戲貼圖；選用的遊戲媒體均來自使用者自行提供的檔案。

JukeKart 為獨立的愛好者專案，與 Nintendo 無關，亦未獲得任天堂官方認可或贊助。遊戲名稱、商標與遊戲素材均歸各自權利人所有。
