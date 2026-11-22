# JukeKart (`v0.1.0-beta01`) 首次公开发布全项目审计报告

## 当前验收 · 2026-11-22

> **最终验收结论：`READY`（`v0.1.0-beta01` 公开发布就绪）**
>
> 全部 P0 阻断项已完成整改、License 定稿、文档同步与 Android 15 模拟器（`mkw_test`，API 35，x86_64）全量 R8 Release APK 回归验证（6/6 PASS，0 崩溃）。下文第 1–10 节保留整改前的原始审计基线以供追溯。

验收对象为 `e719bd6ca7dd0d12d50c04b41545d3dbc520ab60` 上的整改工作树与最终签名 Release 产物。

| 检查项 | 当前结果 |
|---|---|
| 主工作区 | `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleRelease` 和 `:app:compileDebugAndroidTestKotlin` 均通过。JVM 共 1,378 项，0 失败、0 错误、17 跳过。Debug Lint 为 0 错误、156 警告。 |
| 干净克隆 | 将整改后源文件复制到本地干净克隆，逐字节比对了 1,294 个文件。克隆无 `keystore.properties` 及 `app/src` 下的 `.bwav`。`:app:testDebugUnitTest`、`:app:lintDebug` 和 `:app:assembleDebug` 均通过，JVM 与 Lint 计数和主工作区一致。 |
| 签名 Release APK | `app/build/outputs/apk/release/app-release.apk`，22,392,343 字节，版本 `0.1.0-beta01`，versionCode 26，含四个 ABI。`apksigner verify --verbose --print-certs` 验证 v2 签名及预期 Release 证书通过。 |
| 16 KB 文件对齐 | `zipalign -v -c -P 16 4` 通过。Release 合并目录的 `arm64-v8a` 与 `x86_64` 中，`libmkw_native.so`、`libgme.so` 及其余三个共享库的全部 ELF `LOAD` 段均为 `0x4000`。 |
| 产物内容与路径脱敏 | APK 内核对了 12 份第三方许可文本，未发现游戏音视频或曲包扩展名。工作树已移除对应游戏贴图与内部证据目录。公开文档和研究文档已更新，仓库全量扫描未发现个人本机 Windows 路径。 |
| 系统备份 | Manifest 已设 `allowBackup="false"`，并配置了旧版备份和 Android 12+ 数据提取规则，排除曲包、导入断点和可选游戏媒体目录。 |
| 许可证定稿与文档 | 根 [`LICENSE`](../../LICENSE) 已定稿为标准 **Apache License, Version 2.0**（`Copyright 2026 mylittleblover`），明确适用范围（原创代码与内置原创矢量图形）、第三方组件保留各自许可（`NOTICE-THIRD-PARTY.md` 与 `app/src/main/assets/licenses/`）及无任天堂素材/非隶属声明。`README.md`、`NOTICE-THIRD-PARTY.md`、`CHANGELOG.md` 已同步更新为 Apache-2.0 口径。 |
| 运行时回归（模拟器） | 已在 **Android 15 模拟器（`mkw_test`，API 35，x86_64）** 上对签名 R8 Release APK 完成 **6/6 PASS** 端到端回归（导入实测 `.mkwpack` 含 DSP-ADPCM 与 Opus codec 2）：<br>1. **R8 冷启动与核心播放**：SAF 导入 `beta01-qa.mkwpack`（39 曲目 / 62 资产）、交互赛道阶段切换、Jukebox Opus 播放与 `PlaybackService` 媒体通知、设置页 `Open-source licenses` 展示均正常；<br>2. **横屏解锁演出**：`CeremonyVideoLayer` 在 `ROTATION_90`（`2340x1080`）下 `CeremonyPrismHorizonCanvas` 淡入淡出及退回竖屏正常无崩溃；<br>3. **SFC / 对战组曲与 10s 淡出**：`Battle Course (Super Mario Kart)` 与 `Mario Circuit (Mission)` 完整组曲及尾部 10s 淡出自然结束正常；<br>4. **慢慢龟海滩 B 组曲**：`Pre_Short` 起始与后段跳转播放正常；<br>5. **快捷设置磁贴与小组件**：`PlayerTileService` 在播放态切换暂停/恢复、在强停未播放态于 API 35 调用 `startActivityAndCollapse(PendingIntent)` 拉起 `MainActivity` 均正常（`0` 次 `UnsupportedOperationException`），`PlayerWidgetProvider` 注册正常；<br>6. **世界地图缩放与路线交互**：29 赛道程序化 Circuit Atlas 渲染、缩放/拖动/重置、官方路线高亮与赛道详情卡均正常。<br>全量 `adb logcat -d`（155,197 行）检出 `0` 条崩溃/异常日志。（注：运行时回归在 Android 15 模拟器完成，物理真机可由用户后续补充体验。） |

- APK SHA-256：`7E80732D2879A220D06D17C857A886DA52AFC7AD709FDE12006CBD36E8C23EFE`
- Release 证书 SHA-256：`D0:7D:8D:94:51:62:C0:81:8E:9A:29:AC:65:CE:81:80:B3:61:F9:2C:29:9C:3C:35:59:4E:22:99:0A:10:BC:FD`
- 已修复地图色值和路线目标类型、横屏动画所用音频时长、误触单测断言的非隶属文案、`PlayerTileService` API 34+ 磁贴跳转、`PlaybackService` API 33+ 广播注册、16 KB ELF 对齐与 R8 Release 配置。本机 `keystore.properties` 被 `.gitignore` 忽略且未入库。

- **目标仓库**: `mylittleblover/MarioKartWorldPlayer-Android`（GitHub：`PUBLIC`，默认分支 `main`）
- **应用正式名称 / 包名**: `JukeKart` / `com.mylittleblover.mkwplayer`
- **审计基准提交 (`HEAD`)**: `e719bd6ca7dd0d12d50c04b41545d3dbc520ab60`（下文第 1–10 节为此提交整改前的基线记录）
- **当前版本配置**: `versionName = "0.1.0-beta01"`、`versionCode = 26`
- **当前发布状态**: **`READY`**（已完成整改、Apache-2.0 许可定稿及 Android 15 模拟器全量 R8 回归，准予提交、打 `v0.1.0-beta01` 标签并发布 GitHub Pre-release）

> ## 原始基线审计结论（已全部整改）：~~`BLOCKED`~~ → 现已 **`READY`**
>
> 代码与核心互动音频架构已具备 Beta 功能基础（本地 JVM 单元测试 1,378 项全部通过、Native JNI 符号 0 缺失），但在 **版权素材再分发风险**、**干净克隆无法构建/缺损音频（`BGM_Seq_FinalLapFF.bwav` 被 `.gitignore` 忽略）**、**16 KB ELF 页对齐缺失**、**Android 14–16 快捷设置磁贴崩溃（Lint Error）**、**缺少 Release 构建与正式签名配置** 以及 **文档与仓库实际内容严重矛盾** 等关键项目上存在明确阻断，当前 **不可** 直接打 `v0.1.0-beta01` Tag 并公开发布。

---

## 分类图例

- **`PASS`**: 已实际运行命令或完整核验并确认符合 Beta 发布要求。
- **`FAIL`**: 已发现明确缺陷、构建/合规错误或发布阻断问题。
- **`NOT_TESTED`**: 当前环境或本轮审计未实际执行验证（如缺少真机/带曲包模拟器、未安装 `pytest`、未在宿主机编译执行 C++ 原生测试）。
- **`NEEDS_REVIEW`**: 无明确崩溃/阻断错误，但在公开发布前需开发者审视行为、兼容性边界或产品决策。

---

## 1. 当前仓库真实状态

| 检查项 | 状态 | 实测事实与证据 |
| --- | --- | --- |
| 本地与远端 `HEAD` 同步状态 | `PASS` | `main` 与 `origin/main` 均为 `e719bd6ca7dd0d12d50c04b41545d3dbc520ab60`（本地另存历史分支 `backup/my-main-before-codex`，远端存在 `origin/codex/interactive-minf-playback` 与 `origin/codex-interactive-minf-playback`）。 |
| 工作区未提交修改 | `PASS` | 审计开始前工作区干净，无未提交改动（本报告 `docs/release/BETA_RELEASE_AUDIT.md` 为本次新增交付物）。 |
| 版本号与 SDK 配置 | `NEEDS_REVIEW` | `app/build.gradle.kts`：`applicationId = "com.mylittleblover.mkwplayer"`、`minSdk = 26`、`compileSdk = 35`、`targetSdk = 35`、`ndkVersion = "27.0.12077973"`、`versionCode = 26`、`versionName = "0.1.0-beta01"`。尚无 `release` `buildType`。 |
| Git Tag 与 GitHub Release | `PASS` | `git tag -l` 为空，`gh release list --repo mylittleblover/MarioKartWorldPlayer-Android` 为空；`v0.1.0-beta01` 尚未发布。 |
| `.gitignore` 资产隐藏缺陷 | `FAIL` | `.gitignore` 包含 `*.bwav`、`*.apk`、`config/signing/*.jks`、`config/signing/*.keystore`、`config/signing/release*`、`reports/generated/`。由于 `*.bwav` 规则生效，本地打包进 APK 的 [`BGM_Seq_FinalLapFF.bwav`](../../app/src/main/assets/audio/BGM_Seq_FinalLapFF.bwav)（`307,376` B）**并未提交到 GitHub 仓库**，导致干净 `git clone` 缺少该资产。 |
| 密钥/凭据泄露检查 | `NEEDS_REVIEW` | 未在代码或配置中检出 API Key、OAuth Token 或 PEM 私钥；但 [`config/signing/mkwplayer-debug.p12`](../../config/signing/mkwplayer-debug.p12)（`2,718` B）及明文密码 `"android"` 已提交在 [`app/build.gradle.kts`](../../app/build.gradle.kts#L24-L30) 中（见第 5 节）。 |
| 个人本机路径残留 | `FAIL` | 已提交文件中共有 28 个文件存在硬编码 Windows 本机路径（个人工作区及用户目录），覆盖 [`gradle.properties`](../../gradle.properties#L5)（1 个）、[`tools/`](../../tools/) 下 16 个 Python 脚本、[`MKW-PLAYER-AI-HANDOFF.md`](../../MKW-PLAYER-AI-HANDOFF.md)（1 个）、[`docs/research/`](../../docs/research/) 3 份文档与 [`evidence/bootstrap/`](../../evidence/bootstrap/) 7 份记录；`app/src/` 主代码与测试源文件中无本机 Windows 路径。 |

---

## 2. 已验证的测试与构建结果

| 验证项目 | 状态 | 实测结果与精确说明 |
| --- | --- | --- |
| JVM 单元测试（`:app:testDebugUnitTest`，138 个文件） | `PASS`（限本机） / `FAIL`（干净克隆） | 在 `e719bd6` 本地工作区实际运行通过：**1,378 项测试、0 失败、0 错误、16 项跳过**（`DirectBridgePlanTest`、`DirectBridgePreviewExportTest`、`DonkeyRegionPlanTest`、`DonkeyRegionPreviewExportTest`、`FinalLapFanfarePlanTest`、`FullVersionPlanTest`、`FullVersionPreviewExportTest`、`InteractiveAudioIdentitiesTest`、`InteractivePackAcceptanceTest`、`InteractivePlaybackPlanTest`、`NativePreviewPacketTest` 3 项、`OutroNextPrePreviewExportTest`、`PreviewJobExportTest`、`RainbowPreviewJobExportTest`，均通过 `Assume.assumeTrue` 依赖本机 `/workspace/mkw-audio` 或外部导出器）。**干净克隆隐患**：[`CourseMedleyCompilerTest.kt`](../../app/src/test/kotlin/com/mylittleblover/mkwplayer/interactive/CourseMedleyCompilerTest.kt#L65-L72) 直接断言 `src/main/assets/audio/BGM_Seq_FinalLapFF.bwav` 存在，而该文件被 `.gitignore` 排除，干净克隆会直接失败。 |
| Debug APK 构建产物 | `PASS` | [`app-debug.apk`](../../app/build/outputs/apk/debug/app-debug.apk) 存在，大小 **`156,951,575` B（约 149.7 MiB）**，381 个条目，包含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四架构，但仅以 `fixedDebug` 测试密钥签名且内含来自未追踪本地目录的 `assets/audio/BGM_Seq_FinalLapFF.bwav`。 |
| Release APK / AAB 构建与 R8/ProGuard | `FAIL` | [`app/build.gradle.kts`](../../app/build.gradle.kts) 未定义 `release` `buildType`，且仓库中不存在 `app/proguard-rules.pro`；未验证 R8 混淆/压缩下 JNI 反射、Native 入口及 JSON 字段安全性。 |
| Android Test 编译（`:app:compileDebugAndroidTestKotlin`，38 个文件） | `PASS` | `e719bd6` 上实际编译成功（33s）。 |
| Android Instrumentation 测试（`:app:connectedDebugAndroidTest`） | `NOT_TESTED` | 本轮针对 `0.1.0-beta01`（`e719bd6`）**未运行真机或完整带曲包模拟器测试**（原因：`emulator-5556` 无曲包，且开发者在 beta 收束阶段明确决定跳过模拟器改由本人真机测试）。此前视觉/手动验证记录停留于早期版本（如 `alpha19`/`alpha25`），不包含 `beta01` 后期改动的 `SpecialCupUnlockCeremonyStage` 横屏淡入淡出、慢慢龟海滩组曲 B、`PlayerTileService` 与通知小图标。 |
| Android Lint（`:app:lintDebug`） | `FAIL` | 在 `e719bd6` 上实际执行 `./gradlew.bat :app:lintDebug` **失败**，共检出 **3 个 Error 与 154 个 Warning**：<br>1. **Error [`StartActivityAndCollapseDeprecated`]**: [`PlayerTileService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/widget/PlayerTileService.kt#L51) 调用已废弃的 `startActivityAndCollapse(Intent)`，在 `targetSdk = 35`（Android 14/15/16）上会抛出 `UnsupportedOperationException`。<br>2. **Error [`MissingTranslation`]**: [`strings.xml`](../../app/src/main/res/values/strings.xml#L3) 中 `app_name`（`JukeKart`）缺少 `translatable="false"` 或 `zh`/`ja` 翻译，会阻断 `release` 打包时的 `lintVitalAnalyzeRelease`。<br>3. **Error [`LocalContextConfigurationRead`]**: [`StageLocalizationDeviceTest.kt`](../../app/src/androidTest/kotlin/com/mylittleblover/mkwplayer/StageLocalizationDeviceTest.kt#L22)。<br>主要 Warning 汇总：`UnusedResources`（70 条，全部位于 `res/values/` 字符串/资源）、`PluralsCandidate`（27）、`UseKtx`（12）、`GradleDependency`（10）、`ObsoleteSdkInt`（8）、`ModifierParameter`（5）、`ConfigurationScreenWidthHeight`（4）、`InlinedApi`（3：`PlaybackService.kt:175`、`MainActivity.kt:116`、`MkwApp.kt:266`）、`UnusedAttribute`（3）、`MonochromeLauncherIcon`（2）、`AppBundleLocaleChanges`（1）、`DataExtractionRules`（1）。 |
| JNI 符号一致性检查 | `PASS` / `NEEDS_REVIEW` | Kotlin 端共声明 **60** 个 `external fun`（`NativeAudioLibrary` 47 + `NativeSourceCalls` 13）；C++ [`NativeAudioBridge.cpp`](../../app/src/main/cpp/platform/audio/native/jni/NativeAudioBridge.cpp) 及桥接层共导出 **83** 个 `JNIEXPORT` 函数，**0 个 Kotlin 方法缺失 C++ 实现（`PASS`）**。另有 23 个历史/未绑定的 C++ 导出（如 `Mo3Calls`/`ModuleCalls` 等），标记为 `NEEDS_REVIEW`。 |
| 16 KB ELF 页大小对齐检查（`llvm-readelf -l`） | `FAIL` | 使用 NDK 27 `llvm-readelf` 检查 `merged_native_libs`：`liboboe.so`、`libc++_shared.so`、`libandroidx.graphics.path.so` 在 `arm64-v8a` 与 `x86_64` 的 `LOAD` 段对齐均为 `0x4000`（16 KB）；**但自研原生库 `libmkw_native.so` 与 `libgme.so` 的 `LOAD` 段对齐仅为 `0x1000`（4 KB）**，违反 Android 15/16 16 KB 页设备要求。 |
| Manifest 与 Android 15/16 兼容性 | `FAIL` / `NEEDS_REVIEW` | **FAIL**：[`PlaybackService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/player/PlaybackService.kt#L167-L176) 在 `onCreate` 中无条件调用三参数 `registerReceiver(..., Context.RECEIVER_EXPORTED)` 且未判断 `Build.VERSION.SDK_INT >= 33`（而 `DiscoveryViewModel.kt:77` 做了 `SDK_INT >= 33` 判断），在 `minSdk = 26`–32 设备上存在行为与 Lint `InlinedApi` 风险；[`PlayerTileService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/widget/PlayerTileService.kt#L51) 在 Android 14/15/16 崩溃。<br>**NEEDS_REVIEW**：`MainActivity`（[`MainActivity.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/MainActivity.kt#L39)）使用 `android:Theme.Material.Light.NoActionBar` 且未显式调用 `enableEdgeToEdge()`（Android 15/16 强制边到边后状态栏/手势栏视觉需实测）；`CeremonyVideoLayer` 固定 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` 在 Android 16 大屏设备上需验证系统窗口行为；`AndroidManifest.xml` 仅设 `allowBackup="false"` 未配 `dataExtractionRules`。 |
| Python 测试套件（[`tests/test_interactive_pack.py`](../../tests/test_interactive_pack.py)） | `NOT_TESTED` | 本机 Python `3.12.14` 未安装 `pytest`，且工作区无虚拟环境，未执行 Python 自动化测试。 |
| C++ 原生宿主测试（[`tools/interactive-preview/CMakeLists.txt`](../../tools/interactive-preview/CMakeLists.txt)） | `NOT_TESTED` | 定义有 7 个原生宿主目标（含 `mkw_bwav_padding_test`、`mkw_rainbow_fade_boundary_test`、`mkw_transition_cancel_test`、`mkw_global_fade_stop_test`、`mkw_pending_transition_bench`、`mkw_bwav_decode_probe`、`mkw_interactive_preview`），本次未在宿主机编译运行。 |

---

## 3. 功能模块问题清单

| # | 功能模块 | 状态 | 核心实现文件与检查发现 |
| --- | --- | --- | --- |
| 1 | 赛道动态音乐（MINF / Bridge / Fast / Outro / Loop / Pattern / Native sync） | `NEEDS_REVIEW` | `CourseFlowRecipeBuilder`、`CourseMedleyCompiler`、`NativeInteractiveDriver`、C++ `engine/native` 逻辑完整且本地 1,378 项单测通过；但依赖 `BGM_Seq_FinalLapFF.bwav` 与用户导入 `.mkwpack`，且 `beta01` 阶段在真机长时切换尚未完成回归（`NOT_TESTED`）。 |
| 2 | Jukebox（点唱机） | `PASS` | `JukeboxCatalog`、`LinearPlaybackCoordinator` 与播放队列架构完整；实际 Beta 设备回放体验因未做真机测试仍标记 `NOT_TESTED`（见第 2 节）。 |
| 3 | 音乐组曲（Medley / FinalLapFF / SFC / Battle 10s 淡出 / MainMenu） | `FAIL` | [`FinalLapFanfareCompiler.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/interactive/FinalLapFanfareCompiler.kt#L9)、[`CourseMedleyCompiler.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/interactive/CourseMedleyCompiler.kt#L29) 与 `PlayerViewModel.kt` 硬依赖 `assets/audio/BGM_Seq_FinalLapFF.bwav`，但该文件未入库（被 `.gitignore` `*.bwav` 屏蔽），导致源码仓库克隆者无法完整编译单测或使用终圈开场风扇音。此外 `52_KoopaTroopaBeach_b`、`SFC/Battle` 淡出及 `MainMenu` 组曲未经 `beta01` 真机实测。 |
| 4 | 播放列表（用户歌单 / 收藏） | `PASS` | [`PlaylistStore.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/library/PlaylistStore.kt)、`FavoriteStore` 与 `MetadataFile` 均使用临时文件 + `fd.sync()` + `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 原子落盘，无数据覆写竞态缺陷。 |
| 5 | 路线与地图（官方路线 / 自建路线 / 道路点唱机） | `PASS` | [`CustomRouteStore.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/routes/CustomRouteStore.kt) 强制执行拓扑连接校验与尾部编辑限制，持久化走 `MetadataFile` 原子写入。内嵌地图图片版权风险见第 4 节。 |
| 6 | 音乐流程编辑（Flow Editor / Preset / 波形吸附） | `PASS` | [`FlowEditorScreen.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/ui/interactive/FlowEditorScreen.kt) 与 [`CoursePresetStore.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/library/CoursePresetStore.kt) 严格遵循图规则与 `Bridge_ToFull` 交接约束，存储原子安全。 |
| 7 | 媒体库和资源导入（`.mkwpack` 流式导入 / 断点续传） | `NEEDS_REVIEW` | `PackImportCoordinator`、`PackImportService`、`StreamingPackInstaller` 支持校验、断点恢复与取消回滚。需复核两处：<br>1. `InternalPackLibrary.kt` 目录切换与回滚使用了 `File.renameTo(...)`（局部目录替换在跨挂载或部分失败时仅依赖 `backup?.renameTo` 回退）。<br>2. `PackImportService` 使用 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`，在 Android 15+ 存在单日累计运行时限（正常数分钟导入可正常 `stopSelf()`，极端大包长时间挂起需注意）。 |
| 8 | 播放控制及后台服务（`PlaybackService` / `PlaybackSession` / `MediaSession`） | `FAIL` | 1. [`PlaybackService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/player/PlaybackService.kt#L167-L176) 无 `SDK_INT >= 33` 判断直接传递 `Context.RECEIVER_EXPORTED`，触发 Lint `InlinedApi` 并威胁 Android 8.0–12L（API 26–32）兼容。<br>2. [`PlaybackService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/player/PlaybackService.kt#L163) 与 `:289` 调用旧版两参数 `startForeground(...)`，建议统一使用 `ServiceCompat.startForeground` 显式传 `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`。 |
| 9 | 小组件和快捷设置（AppWidget / Quick Settings Tile） | `FAIL` | [`PlayerTileService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/widget/PlayerTileService.kt#L51) 在无播放状态点击快捷设置磁贴时调用已移除支持的 `startActivityAndCollapse(Intent)`，在 Android 14 / 15 / 16（`targetSdk = 35`）上**必定崩溃**（Lint Error `StartActivityAndCollapseDeprecated`）。`PlayerWidgetProvider.kt` 对 `PendingIntent.getForegroundService` 与 `uninterruptible` 状态保护处理正确。 |
| 10 | 特别杯解锁及演出（`SpecialCupUnlock` / 视频演出 / 开发者测试模式） | `NEEDS_REVIEW` | 1. 解锁演出视频 `res/raw/rainbow_appears.webm` 存在重大版权与体积问题（见第 4 节）。<br>2. [`CeremonyVideoLayer.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/ui/unlock/CeremonyVideoLayer.kt#L103) 在 `beta01` 新改写的横屏全屏 + 1s 黑场淡入淡出尚未在真机测试（`NOT_TESTED`）。<br>3. `DevOptionsTapTracker` 默认隐藏，需连点版本号解锁且 `UnlockTestStore` 默认为 `NORMAL`，但需注意在公开包中说明或限制测试模式不会意外持久化误导普通用户。<br>4. [`UnlockStore.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/unlock/UnlockStore.kt#L180) 正常写入已使用 `Files.move(..., REPLACE_EXISTING)`，仅 `.corrupt` 备份路径使用 `renameTo`。 |
| 11 | 用户设置与持久化（Settings / i18n / About / 废弃 UI 代码） | `NEEDS_REVIEW` | 1. **开源许可前端缺失**: APK 已将 12 份第三方许可正文打包进 `app/src/main/assets/licenses/`，但搜索全工程 Kotlin 代码发现 **没有任何 UI 读取或展示这些许可文件**；[`AppSettingsScreen.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/ui/settings/AppSettingsScreen.kt#L734-L770) 的「关于」卡片仅显示应用名、版本号与 `map_art_notice`，缺少开源许可入口、与 Nintendo 非隶属声明、项目仓库/反馈入口。<br>2. **Dead Code**: [`MainActivity.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/MainActivity.kt#L89-L514) 仍残留 425 行未被任何调用的 `private fun LegacyPlayerScreen`。<br>3. **未使用资源**: `res/values/` 存在 70 条 `UnusedResources` Lint 警告。 |
| 12 | 本地数据备份及恢复（JSON 备份 / M3U 导出） | `NEEDS_REVIEW` | [`UserBackupManager.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/library/UserBackupManager.kt) 完整覆盖歌单、收藏、Preset、自建路线、最近播放、播放统计及音频效果设置；但有以下风险：<br>1. **未包含特别杯解锁进度（`UnlockStore`）与应用语言偏好**，用户换机或重装恢复完整 JSON 后解锁状态会归零。<br>2. **`applyBackupToContext` 缺乏跨文件事务回滚**：6 个 Store 顺序调用 `.getOrThrow()`，若中途写入失败会留下半恢复状态。<br>3. **`parseBackupJson` 解析过严**：第 201 行要求 `version == SCHEMA_VERSION`（无向后兼容预留），且多个数组循环使用 `getJSONObject(i)` 而非 `optJSONObject(i)`。 |

---

## 4. 素材授权审查表

当前仓库及已构建 APK 中存在大量未经授权再分发的第三方游戏受版权保护素材，且多份素材在代码/文档自身的声明中（如 `CupArt.kt`、`map_art_notice`、`LICENSE`）已被标注为受任天堂版权保护或不可再分发。

| 资源路径 / 模式 | 文件数与总大小 | 已知来源（仓库内依据） | 是否第三方版权内容 | 是否存在再分发授权 | 是否适合公开发布 / 分类 | 推荐处理与不破坏功能的替代方案 |
| --- | --- | --- | --- | --- | --- | --- |
| `app/src/main/res/raw/rainbow_appears.webm` | 1 个<br>`101,831,396` B<br>（`97.11 MiB`） | 《马力欧卡丁车 世界》游戏实机解锁过场演出视频（`CeremonyVideoLayer.kt` 引用） | **是**（Nintendo 版权音视频作品） | **否**（无任何书面或公开许可） | **绝对不适合**<br>`FAIL`（P0 阻断） | **非破坏替代方案**：<br>1. 从 APK 与 Git 历史/工作区移除内置 `.webm`，改由玩家自备 `.mkwpack`（或用户在游戏文件导入时附带演出视频路径）按文件名按需读取；<br>2. 若未提供外部视频文件，在 `CeremonyVideoLayer` 中回退至纯 Compose/Canvas 原创粒子与彩虹光线动画 + 曲包音频完成解锁演出，功能零损坏且 APK 体积立减 ~97 MiB。 |
| `app/src/main/assets/audio/BGM_Seq_FinalLapFF.bwav` | 1 个<br>`307,376` B<br>（本地未入库） | 游戏 RomFS 音频流（`tools/` 与 `docs/research/` 明确来自游戏 `romfs/Sound/Resource/Stream`） | **是**（Nintendo 版权音频） | **否** | **绝对不适合**<br>`FAIL`（P0 阻断 + 构建撕裂） | **非破坏替代方案**：将 `BGM_Seq_FinalLapFF.bwav` 作为曲包（`.mkwpack`）内建成员或用户可导入资产，修改 `CourseMedleyCompiler` / `FinalLapFanfareCompiler` 从已安装曲包读取，缺省无曲包时不报错崩溃、单测使用合成测试夹具或显式 `assumeTrue`。 |
| `app/src/main/res/drawable-nodpi/world_map.png` | 1 个<br>`6,066,830` B | 游戏世界地图 UI 底图（`map_art_notice` 声明 `© Nintendo`） | **是**（Nintendo 版权美术） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：从仓库移除，改为支持从用户导出曲包 / 外部地图资产包运行时加载；缺少外部底图时基于 [`map_anchors.json`](../../app/src/main/assets/map_anchors.json) 与 [`stage_connections.json`](../../app/src/main/assets/stage_connections.json) 用 Compose Canvas 绘制简洁矢量拓扑线网与节点地图，保留完整路线选择、连线高亮和播放功能。 |
| `app/src/main/res/drawable-nodpi/cover_*.webp` | 30 个<br>`3,507,412` B | `CourseCoverArt.kt` 记录来源于游戏 `Layout` 的 `C_StageL_*` 赛道封面 | **是**（Nintendo 版权美术） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：`PackArtwork.kt` 已具备从 `.mkwpack` 读取封面的基础设施；移除内置 `cover_*.webp` 后优先使用曲包内附带的封面，未提供时回退至已有 `CoursePlaceholder.kt`（基于赛道配色和徽章的原创建筑色块封面），功能完全保留。 |
| `app/src/main/res/drawable-nodpi/stage_*.png` | 30 个<br>`1,352,619` B | `StageArt.kt` 记录为游戏 3D 场景/微缩地形插图 | **是**（Nintendo 版权美术） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：移入 `.mkwpack` 按需加载或回退至原创渐变地形色卡 / 矢量图案占位符。 |
| `app/src/main/res/drawable-nodpi/cup_*.png` | 8 个<br>`550,746` B | [`CupArt.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/ui/art/CupArt.kt) 注释明确标记为 **`PRIVATE_RESEARCH` / `Not redistributable`** | **是**（Nintendo 版权图标） | **明确无授权** | **绝对不适合**<br>`FAIL`（P0 阻断） | **替代方案**：移除原生游戏图标文件，默认渲染原创极简几何奖杯矢量徽章 + 奖杯名称，同时兼容读取 `.mkwpack` 自定义杯赛图标。 |
| `app/src/main/res/drawable-nodpi/sv_*.png` | 12 个<br>`774,287` B | `CupArt.kt` 确认属 Knockout Tour 赛事徽章 | **是**（Nintendo 版权图标） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：同 `cup_*.png`，默认使用原创编号徽标，或从 `.mkwpack` 加载。 |
| `app/src/main/res/drawable-nodpi/map_light_*.png` & `map_route_*.png` | 24 个<br>`558,590` B | `MapArtworkCache.kt` 注释确认源自游戏 `MapLine_00` / `VoteLine_00` | **是**（Nintendo 游戏 UI 贴图） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：移除静态游戏高亮贴图，改用 Compose `Path` + Glow 渐变动态绘制地图路径高亮，视觉反而随分辨率矢量化更清晰且 0 版权包袱。 |
| `app/src/main/res/drawable-nodpi/mode_*.png`<br>`media_*.png`<br>`button_*.png` | 15 个（7 + 4 + 4）<br>`423,720` B | `ButtonArt.kt` 记录 `button_*.png` 源自游戏 `DialogBtnS_*` Layout 精灵图；`mode_*`/`media_*` 属游戏模式与媒体框贴图 | **是**（Nintendo 游戏 UI 美术） | **否** | **不适合**<br>`FAIL`（P0 阻断） | **替代方案**：改用项目已有的 `MkwKitTokens` 原创 Material/Vector 按钮样式与图标（`Icons.Filled.*` / 原创 SVG Vector），完全去除游戏提取 UI 贴图。 |
| `app/src/main/res/mipmap-*/ic_launcher*.png` | 20 个<br>`488,184` B | 基于 Nintendo 角色「朱盖木 / Lakitu」形象绘制的应用启动图标 | **是**（受角色商标与著作权保护） | **否** | **不适合公开发布**<br>`FAIL`（P0 阻断 / `NEEDS_REVIEW`） | **替代方案**：设计 **JukeKart 原创启动图标**（例如赛车格纹旗 + 音乐音符 / 唱片矢量自适应图标，同时顺带补齐 Lint `MonochromeLauncherIcon` 警告）。 |
| `app/src/main/res/drawable-*/ic_stat_jukekart.png` | 5 个<br>`6,517` B | 基于朱盖木轮廓简化处理的纯白单色通知栏小图标 | **存在角色形象衍生争议** | **否** | **建议替换**<br>`NEEDS_REVIEW` | **替代方案**：与启动图标同步替换为原创 VectorDrawable（音符 + 赛道网格单色通知图标）。 |
| `evidence/bootstrap/*.png` | 8 个<br>`1,032,380` B | 早期模拟器调试截图（含赛道封面与杯赛画面） | **间接包含游戏画面** | **否** | **不建议带图公开**<br>`FAIL` / `NEEDS_REVIEW` | 将内部调试过程截图目录从公开 Release 分支剥离或替换成脱敏占位图后的界面截图。 |
| `third_party/` 与 `app/src/main/assets/licenses/` | 2 份源码包 + 内联源码 + 12 份许可文本 | `libopenmpt-0.8.9`（BSD-3）、`game-music-emu-0.6.5`（LGPL-2.1，按 `SHARED` 动态库编译链接至 `libgme.so`）、`opus`（BSD-3）、`vgmstream-clhca`（ISC）、`dr_flac`/`dr_mp3`/`minimp3`/`stb-vorbis`/`emu2413`/`nuked-opn2` | **是（合规开源组件）** | **是** | **基本合规**<br>`NEEDS_REVIEW` | `libgme.so` 采用 `SHARED` 编译满足 LGPL-2.1 要求，但需在应用「关于 -> 开源许可」页面实际展示这 12 份文本，并在 `NOTICE-THIRD-PARTY.md` 补充 Maven 依赖（`Oboe`、`Jetpack Compose`、`AndroidX Media`、`libandroidx.graphics.path.so` 等 Apache-2.0 组件）。 |

---

## 5. 签名与安全风险

1. **`FAIL`（P0）— 公开发布不可使用仓库内历史调试密钥库**:
   - [`config/signing/mkwplayer-debug.p12`](../../config/signing/mkwplayer-debug.p12) 及 SHA-256 指纹已提交进公开 GitHub 仓库，[`app/build.gradle.kts`](../../app/build.gradle.kts#L24-L30) 明文硬编码 `storePassword = "android"`、`keyPassword = "android"`、`keyAlias = "mkwplayerdebugkey"`。
   - **安全风险**: 公开仓库中任何人都拥有该私钥并能伪造同签名 APK 覆盖安装已装用户设备，篡改本地歌曲库/备份/隐私数据；且一旦公开发布首个 Beta 使用该签名，后续切换正式签名会导致 **用户无法直接覆盖升级（必须卸载重装）**。
2. **`FAIL`（P0）— 缺少正式 `release` 构建配置与 ProGuard/R8 规则**:
   - 目前只有 `debug` 变体，`applicationId` 直接打包为未混淆 Debug 包；公开 Release APK 需要独立的 `release` `buildType`、离线保管的新正式发布签名（不入库、仅存本地环境变量或 GitHub Actions Secrets）以及针对 `NativeAudioLibrary` / `NativeSourceCalls` 的 `-keep` 规则。
3. **`NEEDS_REVIEW` — `android:allowBackup="false"` 未配 `dataExtractionRules`**:
   - [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml#L14) 关闭系统备份，设置 `allowBackup="false"` 但无 `android:fullBackupContent` / `android:dataExtractionRules`。由于应用私有存储中的曲包目录（`files/packs`）体积通常在数百 MB 到 1+ GB，应明确排除大体积音频目录，特别是还需覆盖设备间迁移规则的情形。
4. **`NEEDS_REVIEW` — 仓库内个人环境与本机目录信息暴露**:
   - [`gradle.properties`](../../gradle.properties#L5) 硬编码 `org.gradle.java.installations.paths=<本机 JDK 目录>`，导致其他贡献者克隆后可能因路径不存在出现工具链探测告警；[`docs/research/2026-11-15-handoff.md`](../../docs/research/2026-11-15-handoff.md)、`MKW-PLAYER-AI-HANDOFF.md` 与 16 个 `tools/*.py` 暴露本机盘符、用户名及本地目录结构。

---

## 6. 文档整改清单

| 文件 | 状态 | 与真实代码/项目的实质矛盾 | 整改要求 |
| --- | --- | --- | --- |
| [`README.md`](../../README.md) | `FAIL` | 1. 标题仍是旧名称 `# MKW Player`，版本号停在 `0.1.0-alpha01`；<br>2. 第 7 行宣称 *"This repository does **not** include any Nintendo game audio, character CG, or game UI icons"*，**与当前仓库实际包含 `rainbow_appears.webm`、`drawable-nodpi/` 120 张美术/UI图、朱盖木图标及本机 `BGM_Seq_FinalLapFF.bwav` 直接冲突**；<br>3. 功能描述仍写 *"Only `.bwav` audio playback / Single-track play, pause, resume, stop, seek"*，与当前完整动态组曲、Jukebox、地图路线、Flow 编辑器、小组件、特别杯解锁、备份系统严重不符；<br>4. 缺少安装要求（需自备 `.mkwpack`）、GitHub Issues 反馈渠道与与 Nintendo 非隶属声明。 | 全面重写为 **JukeKart `0.1.0-beta01`** 介绍，真实列明核心功能、曲包导入前置说明、环境构建要求（移除个人路径依赖）、与 Nintendo 非隶属声明、反馈渠道（GitHub Issues），且确保文案与仓库真实资产完全一致。 |
| [`CHANGELOG.md`](../../CHANGELOG.md) | `FAIL` | 内容仅记录 `0.1.0-alpha01 — 2026-11-08`，完全缺失 `alpha02` 至 `alpha25` 及 `0.1.0-beta01` 的完整演进与变更说明。 | 补充整理自 `alpha01` 至 `0.1.0-beta01` 的正式发布变更日志（新增动态音乐引擎、组曲/SFC/对战/主菜单、世界地图路线、Flow 编辑器、点唱机、备份/M3U、小组件/磁贴、特别杯解锁与图标本地化等）。 |
| [`docs/USAGE.md`](../../docs/USAGE.md) | `FAIL` | 仍描述 `0.1.0-alpha01` 旧版单页界面（*`MKW Player Debug`*、`Add a .bwav file`、`Open a stage`），与当前多Tab完整应用界面完全脱节。 | 更新为面向终端用户的 **JukeKart Beta 使用指南**（首次 `.mkwpack` 导入、赛道动态控制台操作、点唱机与播放列表、自定义路线、Flow 编辑器、数据备份与恢复、Android 13+ 通知权限说明）。 |
| [`LICENSE`](../../LICENSE) | `FAIL` | 本身保留著作权（All Rights Reserved，**本次严禁私自改成 MIT/GPL/Apache**），但第 5 行称其为 `MKW standalone music player`，第 24–25 行宣称 **`"No game assets are included"`**，与仓库现状自相矛盾。 | 保留现有权利声明口径不擅自改为标准开源协议；在清除侵权素材后，修正产品名与资产范围陈述并交由开发者最终审定法律措辞。 |
| [`NOTICE-THIRD-PARTY.md`](../../NOTICE-THIRD-PARTY.md) | `FAIL` | 1. 第 39–44 行同样错误声称仓库与 APK 不含任何 `.bwav` 或游戏图标/CG；<br>2. 未列出 Gradle 引入的 Jetpack Compose、AndroidX Media、`androidx.graphics:graphics-path`（会打入 `libandroidx.graphics.path.so`）、Kotlinx Coroutines、Google Oboe 的具体许可说明。 | 删除虚假的无资产承诺（并在清退资产后实事求是更新），补全 Android/Jetpack/Oboe 等依赖及 `assets/licenses/` 全量目录对应表，并在 App 「关于」页提供可查看入口。 |
| `MKW-PLAYER-AI-HANDOFF.md` / `docs/research/*` / `evidence/*` | `NEEDS_REVIEW` | 属于内部 AI 交接记录与逆向实验笔记，含大量本机绝对路径与过期状态描述，容易误导公开仓库贡献者。 | 建议归档整理、脱敏个人路径，或在 `README` 中明确标明 `docs/research/` 为内部研究档案，不应作为使用文档。 |

---

## 7. Beta 发布阻断项（P0 Must-Fix Before `v0.1.0-beta01`）

1. **[版权合规 / 法律阻断]** 仓库及当前 APK 内嵌未经授权的第三方受版权保护素材，包括 `rainbow_appears.webm`（`97.11 MiB`）、`BGM_Seq_FinalLapFF.bwav`、`120` 个 `drawable-nodpi/` 游戏截图/封面/徽章/地图/UI贴图、`ic_launcher*.png` 角色图标与 `evidence/bootstrap/*.png`，且与 `LICENSE`/`README`/`NOTICE` 自称矛盾。
2. **[构建与功能撕裂]** `.gitignore`（`*.bwav`）隐藏了 [`BGM_Seq_FinalLapFF.bwav`](../../app/src/main/assets/audio/BGM_Seq_FinalLapFF.bwav)，导致干净 `git clone` 无法通过 `CourseMedleyCompilerTest`，且脱离开发者本机编译出的 APK 缺少终圈开场音效资产。
3. **[Android 14–16 运行时崩溃 + Lint Failure]** [`PlayerTileService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/widget/PlayerTileService.kt#L51) 使用已移除支持的 `startActivityAndCollapse(Intent)`，`targetSdk = 35` 下在真机点击快捷磁贴会崩溃并阻断 Lint/Release 构建。
4. **[Android 15/16 Native 兼容性阻断]** `libmkw_native.so` 与 `libgme.so`（`arm64-v8a`、`x86_64`）ELF `LOAD` 段仅 `0x1000`（4 KB）对齐，未启用 16 KB 页大小对齐（`0x4000`）。
5. **[Release 签名与构建缺失]** 尚无 `release` `buildType` 与混淆规则，若用公开在 Git 里的 `mkwplayer-debug.p12`（密码 `"android"`）发布首个 Beta，将造成签名私钥公开泄露并锁死后续升级路径。
6. **[API 26–32 广播接收器风险]** [`PlaybackService.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/player/PlaybackService.kt#L175) 无版本保护传递 `Context.RECEIVER_EXPORTED`（API 33 常量）。
7. **[关键测试缺口与文档过期]** `0.1.0-beta01` 最新改动（横屏演出视频淡入淡出、慢慢龟海滩 B、SFC/对战淡出、通知小图标、磁贴与后台服务）尚未真机/完整端到端验证；`README.md` / `CHANGELOG.md` / `docs/USAGE.md` 仍停留在过时的 `alpha01` 状态。

---

## 8. 可自动修复项目（技术整改，待批准后可由下一阶段直接修改）

以下项目有清晰明确的代码/配置修复方案，无需更改产品核心逻辑即可完成：

1. **修复 3 处 Lint Error**:
   - `PlayerTileService.kt:51`：对 `Build.VERSION.SDK_INT >= 34` 改用 `startActivityAndCollapse(PendingIntent.getActivity(...))`；
   - `app/src/main/res/values/strings.xml:3`：为 `<string name="app_name" translatable="false">JukeKart</string>` 添加 `translatable="false"`；
   - `StageLocalizationDeviceTest.kt:22`：改用符合 Compose Lint 规则的本地化 `Context`/`Configuration` 测试写法或 `createConfigurationContext`。
2. **修复 `PlaybackService.kt:167-176` 广播注册兼容性与 `ServiceCompat.startForeground`**:
   - 使用 `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_EXPORTED)` 或参照 `DiscoveryViewModel.kt:77` 增加 `Build.VERSION.SDK_INT >= 33` 判断；并改用 `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`。
3. **开启 NDK 16 KB ELF 页大小对齐**:
   - 在 [`app/src/main/cpp/CMakeLists.txt`](../../app/src/main/cpp/CMakeLists.txt)（覆盖 `mkw_native`）及 [`third_party/game-music-emu/prepare.cmake`](../../third_party/game-music-emu/prepare.cmake) / `engine/native` 链接选项为 Android `16K` 对齐添加 `-Wl,-z,max-page-size=16384`，随后用 `llvm-readelf -l` 验证全部 `.so` 达到 `0x4000`。
4. **增设 `release` `buildType` 与 `proguard-rules.pro`**:
   - 添加 `app/proguard-rules.pro`（保留 `NativeAudioLibrary`、`NativeSourceCalls`、`org.json` 及 JNI 回调类），配置从环境变量/本地未入库 `keystore.properties` 读取发布签名的 `release` 变体，并分离 `fixedDebug` 与 `release`。
5. **配置 `android:dataExtractionRules` 与 `fullBackupContent`**:
   - 显式排除 `files/packs`、`files/import_checkpoints` 等超大体积音频目录，仅允许系统备份用户歌单/收藏/Preset/路线/解锁进度等轻量 JSON。
6. **补齐应用内「开源许可」展示界面并清理死代码**:
   - 在 `AppSettingsScreen` 「关于」卡片新增可浏览 `app/src/main/assets/licenses/` 全量 12 份协议及 AndroidX/Oboe/GitHub 反馈链接入口；移除 [`MainActivity.kt`](../../app/src/main/kotlin/com/mylittleblover/mkwplayer/MainActivity.kt#L89-L514) 闲置的 `LegacyPlayerScreen`（可同时消除 1 个 `InlinedApi` 警告及冗余依赖）；清理 `res/values/` 确认废弃的 `UnusedResources` 字符串；完善 `UserBackupManager` 备份解锁进度与跨 Store 错误安全提示。
7. **消除本机硬编码路径与重写 Beta 文档**:
   - 从 [`gradle.properties`](../../gradle.properties#L5) 移除个人 本机工具链 路径；将 `tools/*.py` 默认路径参数化（支持环境变量 / CLI `--input` 参数）；全面更新 `README.md`、`CHANGELOG.md`、`docs/USAGE.md` 与 `NOTICE-THIRD-PARTY.md`。

---

## 9. 需要开发者决策的项目（无法由 AI 擅自决定）

1. **受版权保护素材的处置方案（核心法律/发布策略选择）**:
   - **选项 A（强烈推荐，符合 GitHub 公开规范）**: 从开源仓库与公开预编译 APK 中 **完全剥离** 所有任天堂音视频及美术资源（`rainbow_appears.webm`、`BGM_Seq_FinalLapFF.bwav`、`drawable-nodpi/` 120 张游戏图文、`ic_launcher*` 角色图标、`evidence/bootstrap/*.png`），并将原本依赖内嵌资源的曲目/封面/底图/开场视频逻辑全面改为 **优先从用户自建 `.mkwpack` 曲包读取 + 无曲包时使用内置原创矢量/配色/Canvas 特效回退**（不破坏任何播放核心能力）。
   - **选项 B（仅供明确评估）**: 仅剥离不可公开部分并将仓库转为 Private 或仅在自建渠道分发 —— 但若维持公开 GitHub 仓库与 Release APK 分发，随包附带近 115 MiB 任天堂版权视频与游戏资源仍面临 DMCA 下架及账号合规风险，Audit 无法背书为 `READY`。
2. **`ic_launcher` 与通知栏图标品牌重绘方案**:
   - 是否授权设计/生成一套 **JukeKart 原创自适应图标**（含 Android 13+ Monochrome 单色图层，彻底脱离朱盖木角色版权范围）并同步替换 `ic_stat_jukekart.png`？
3. **正式软件许可证（`LICENSE`）定位**:
   - 当前 [`LICENSE`](../../LICENSE) 为作者个人「保留所有权利（All Rights Reserved）」，并非标准开源协议（因此 GitHub 识别为 `Other`）。请确认公开 Beta 是**继续保留闭源免费发布（All Rights Reserved + Source Available）**，还是**在剥离任天堂资源后将原创代码改为开源协议（如 GPL-3.0 / Apache-2.0 / MIT + LGPL 兼容说明）**？（注：本次审计未改写 `LICENSE`）。
4. **正式 Release 签名密钥生成与 Git 历史处置**:
   - 开发者需在本机离线生成全新的正式 Release Keystore（切勿提交至 Git，妥善保管密码及指纹）；由于此前提交历史上已存在 `rainbow_appears.webm`（97.11 MiB）等大文件与游戏截图，请决定在公开宣传前是否仅从新提交开始清理（保留历史）或择机做历史仓库重构/重写（本次未执行任何改写历史操作）。
5. **个人本机研究档案去留（`docs/research/`、`MKW-PLAYER-AI-HANDOFF.md`、`evidence/`）**:
   - 是否将含本机磁盘目录与 `romfs`/`readable_assets` 提取记录的文档移出公开主分支，或做路径脱敏与合规边界整理。

---

## 10. 最终发布建议与下一阶段路线

**当前状态（2026-11-22 最终验收）**: **`READY`** — 阶段一至阶段三已全部完成，且已在 Android 15 模拟器（`mkw_test`，API 35，x86_64）上完成签名 R8 Release APK 全量 6 项端到端回归（`6/6 PASS`，`0` 条 logcat 崩溃/异常），根 `LICENSE` 已正式定稿为 `Apache-2.0`，准予提交 `main`、打 `v0.1.0-beta01` Tag 并发布 GitHub Pre-release。

> 以下为基线阶段记录的四步收束路线（阶段一至三与模拟器端到端回归均已落地）：

1. **阶段一 — 确认版权资产策略与图标（开发者决策 + 改造）**: 确定采用 **`.mkwpack` 运行时提供外部资源 + 内置原创回退视觉**，从 `main` 清理 `rainbow_appears.webm`、`BGM_Seq_FinalLapFF.bwav`（以及修改单测摆脱本地隐藏 `.bwav` 依赖）、游戏 UI 贴图并更换原创应用与通知图标。
2. **阶段二 — 技术阻断与 16 KB 修复（可自动执行）**: 修复 `PlayerTileService.kt:51`（API 34+ `PendingIntent`）、`PlaybackService.kt:175`（`SDK_INT >= 33` 保护）、`strings.xml` `translatable="false"`、CMake `-Wl,-z,max-page-size=16384`、配置 `release` 构建与 ProGuard 规则、完善 `AppSettingsScreen` 许可浏览页及 `dataExtractionRules`。
3. **阶段三 — 文档与清洁仓库验证**: 更新 `README.md`、`CHANGELOG.md`、`docs/USAGE.md`、`NOTICE-THIRD-PARTY.md` 与脱敏 `gradle.properties`/`tools/*.py`；在一个全新 `git clone` 目录中实际验证 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleRelease` 零错误通过，并检查全部 ABI 的 `.so` 达到 `0x4000` 对齐。
4. **阶段四 — 真机 Beta 验收与正式签发**: 在开发者真机（覆盖 Android 14–16，及若可行补测低版本或横竖屏切换场景）实际安装自签 `app-release.apk`，实测导入 `.mkwpack`、动态音乐切段、终圈风扇音、SFC/对战 10s 淡出、横屏解锁演出、快捷设置磁贴与备份恢复，全部通过后再打 `v0.1.0-beta01` Tag 发布 GitHub Beta Release。
