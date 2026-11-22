# サードパーティライセンス通知

[简体中文](NOTICE-THIRD-PARTY.zh-Hans.md) | [繁體中文](NOTICE-THIRD-PARTY.zh-Hant.md) | [English](NOTICE-THIRD-PARTY.md) | **日本語**

JukeKart のオリジナルソースコードおよび内蔵オリジナルベクターアートワークは **Apache License, Version 2.0** の下でライセンスされています（[`LICENSE`](LICENSE) を参照）。以下のネイティブコンポーネントはそれぞれのライセンスと通知を保持します。全文は APK に同梱されており、アプリ内の **設定 → オープンソースライセンス** から確認できます。ソース側のコピーは [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/) に格納されています。

| コンポーネント | バージョンまたはソース | ライセンス | 同梱テキスト |
|---|---|---|---|
| clHCA (vgmstream) | `third_party/clhca` | ISC | `vgmstream-clhca.txt` |
| libopenmpt | 0.8.9 | BSD-3-Clause および同梱コンポーネントライセンス | `libopenmpt.txt`, `libopenmpt-mpt-bsd.txt`, `libopenmpt-mpt-boost.txt` |
| minimp3 | `third_party/libopenmpt/mo3-codecs/minimp3` | CC0-1.0 | `minimp3.txt` |
| dr_flac | `dfe8377` | MIT-0 | `dr-flac.txt` |
| dr_mp3 | `dfe8377` | MIT-0 | `dr-mp3.txt` |
| stb_vorbis | `2c980bb` | MIT | `stb-vorbis.txt` |
| Opus | `third_party/opus/upstream` | BSD-3-Clause | `opus.txt` |
| Game_Music_Emu | 0.6.5 | LGPL-2.1-or-later | `game-music-emu-lgpl-2.1.txt` |
| emu2413 | Game_Music_Emu 経由 | MIT | `emu2413-mit.txt` |
| Nuked OPN2 | Game_Music_Emu 経由 | 上流の通知文 | `nuked-opn2-notice.txt` |

Game_Music_Emu は共有ライブラリ `libgme.so` としてビルドされます。固定されたソースとビルドファイルは [`third_party/game-music-emu/`](third_party/game-music-emu/) に含まれています。

## Maven コンポーネント

[Android ビルド設定](app/build.gradle.kts)では以下の直接ランタイム依存関係が宣言されています。配布条件および推移的依存関係については、各アーティファクトのメタデータとライセンス通知をご確認ください。

| コンポーネント | 宣言バージョン | ライセンス |
|---|---|---|
| Oboe | 1.10.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 |
| AndroidX Lifecycle Compose | 2.8.7 | Apache-2.0 |
| AndroidX Media | 1.7.0 | Apache-2.0 |
| Jetpack Compose UI, Foundation, Material and Material 3 | Compose BOM 2025.04.01 | Apache-2.0 |
| Kotlin standard library | 2.1.20 | Apache-2.0 |
| Kotlin Coroutines Android | 1.10.1 | Apache-2.0 |

## ゲーム参照およびアセットについて

Beta APK にはカタログ・ルートメタデータと、コースバッジ・カバー・マップ・フォールバック解放アニメーション用のプロジェクト独自アートワークが含まれています。ランチャーおよび通知アイコンは従来デザインを引き継ぎます。ゲーム音声、ゲーム動画、および抽出されたゲームテクスチャは現在のソースツリーおよび Beta パッケージから削除されており、任意のゲームメディアはユーザーが用意したファイルから読み込まれます。

JukeKart は独立したファン制作プロジェクトであり、任天堂とは一切関係なく、公式の許諾・推奨・後援を受けたものではありません。ゲーム名称、商標、およびゲーム素材はそれぞれの権利者に帰属します。
