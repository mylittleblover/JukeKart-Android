# MKW Player · AI 交接

更新日期 2026-11-15。当前版本是可安装测试的 Android Alpha。接手优先做真机听感与定位回归，再调整媒体和互动播放的界面层级。

## 从哪里接手

- Windows 仓库为 `G:\Mario Kart World\MKW-Player`。应用包名 `com.mylittleblover.mkwplayer`，界面用 Kotlin / Compose，音频用 C++ / JNI / Oboe。
- Git 基线是 [9110fb5](https://github.com/mylittleblover/MarioKartWorldPlayer-Android/commit/9110fb5acc93447dddcb8f5366e8a87ba5e9915a)。本地 `main` 跟踪 GitHub 的 `origin/codex/interactive-minf-playback`，已核对远端同一提交。GitHub 默认 `main` 更早，换机器请检出 `codex/interactive-minf-playback`。
- 构建版本是 `0.1.0-alpha19` / versionCode 19。分发目录里另有日期更早的 alpha20 APK，本轮测试请使用下方标明的修复版。
- `docs/research/2026-11-15-handoff.md` 是本机早期交接草稿，其中“修改未提交”“Jukebox 无法播放”等说法已过时。`app/build-audit/` 是本机备用构建目录。

## 当前功能与验证证据

| 项目 | 已实现及验证范围 |
|---|---|
| Jukebox | 用户已确认出声。已有独立流式播放、队列、随机和循环。早期探针记录了 227 个文件解码成功，属于文件级证据。 |
| 两首胜利巡游、结算画面 A | 三首都带合法的 BWAV Opus 循环点，旧解析器因此拒绝打开。修复后三首通过解码、定位、末尾渲染探针，最终 APK 在模拟器上逐首起播并推进进度。 |
| 普通曲目拖动 | 普通单轨定位可直接准备目标页，未缓存的 BWAV Opus 跳转用短预解码，输出增益设置不会再让单轨走完整传输重放。模拟器把「结算画面 A」从 0:01 拖到 4:53，随后继续播放。由播放图承载的叠录或串联曲目还要单独测。 |
| 彩虹之路 | 从「区域 1 · 序曲」进入时预解码序曲，减少等待循环期间对流式缓存的依赖。模拟器看到进度推进、等待切换和取消入口。断音、循环接缝和其他段的卡顿仍需真机复测。 |
| 封面与图片性能 | 普通播放器和迷你播放器均实测显示「结算画面 A」封面。赛道详情按源图宽高比完整显示。图片在进入视口时按显示尺寸异步解码，离屏释放位图引用并保留布局比例，重新出现时渐入。首页已取消整张地图预解码。 |
| 媒体默认页 | 仅保留搜索和分类，移除了下方重复的赛道索引和最近播放。输入关键词后直接显示结果。首页仍有最近播放和收藏，分类内部仍可浏览。 |
| 自动化 | Debug 构建成功。JVM 测试共 1,286 项，1,270 通过、16 跳过、0 失败。模拟器另跑 `ArtworkViewportDeviceTest`，1 项通过。 |

`MKW-Complete-with-Jukebox.mkwpack` 里没有 `artwork/`。验收封面请用 `MKW-Complete-All.mkwpack`。它的清单覆盖 417 个逻辑曲目，尚未逐首在 App 内试听。

## 接手后的顺序

1. **真机复测播放。** 从彩虹之路第一圈序曲开始，等至少一次自然循环，点下一阶段，试一次取消，再重新触发并听实际过渡。若有断音，记录曲目、当前阶段、时间、设备，核对 Oboe XRuns、回调耗时及引擎流式缓存 underrun。另测长曲目前后拖动、暂停时拖动、叠录和串联曲目拖动。模拟器和原生探针没有完成真机听感验收。
2. **先收敛媒体分类和返回层级。** 用户已明确不要在媒体首页重复列赛道和最近播放。模拟器 1080 px 宽时多数卡约 330 px 高，Nintendo Music 卡约 396 px。1.3 倍字体使折行差异更明显。下一步统一八张卡的尺寸和文字槽位，固定顶栏显示当前分类，返回恢复之前位置。
3. **把互动操作放到第一屏。** 「下一阶段」目前要往下找，红色「停止」最显眼。点击下一阶段后已有等待和取消反馈，二者隔得远，且没有说明等待哪个音乐点。将当前阶段、目标阶段、等待时机和取消放在同一控制区，播放时收起禁用的起始阶段选择。用户点名用 `$apple-design` 和 `$gestures` 评审，重点是即时反馈、拖动时跟手、松手后保持目标值，以及方向一致且可打断的手势。保持 Android 原生返回习惯。
4. **对照规格确认剩余页面。** 旧资料列出独立的全部播放列表页、大奖赛杯赛网格、自由驾驶来源索引、分段流程编辑器。实施前对照现有代码和 HTML 规格确认缺口。赛道详情中的完整组曲目前仍是占位。

## 从哪读代码

- **解码和定位**：`engine/native/decoder/{BwavFormat.hpp,BwavSource.cpp,OpusSource.cpp}`、`engine/native/runtime/RuntimeSeek.cpp`。实文件探针在 `tools/interactive-preview/bwav_decode_probe.cpp`。
- **应用播放和阶段**：`app/src/main/kotlin/com/mylittleblover/mkwplayer/` 下的 `player/{PlaybackSession,LinearPlaybackCoordinator,LinearQueue,PlayerViewModel}.kt`。彩虹之路从 `PlayerViewModel.startRainbow` 追到 `interactive/InteractivePlaybackPlan.kt`、`RainbowInteractiveCompiler.kt`、`DirectBridgeCompiler.kt`。回调 timing 在 `app/src/main/cpp/platform/audio/native/oboe/NativeOutput.cpp`。
- **页面和图片**：同包 `MkwApp.kt`、`ui/NavState.kt`、`ui/home/DiscoveryScreens.kt` 和 `ui/interactive/InteractivePlaybackControls.kt`。图片实现是 `ui/art/{VisibleArtwork,PackArtwork,CourseCover}.kt`。
- **规格和数据**：`docs/superpowers/specs/2026-11-14-discovery-pages.md`、`docs/research/` 下的循环与曲目映射，以及 `app/src/main/assets/` 的曲目、连接表 JSON。

使用稳定 ID，避免拿翻译文字做身份。地图连接有方向，彩虹之路只有从碧姬竞技场来的入边。UI 进度插值只用于显示。没有真实资源时用已有的回退或空态。用户音频、曲包、APK、截图和本机构建目录留在 Git 之外。

## 构建和复测

Windows PowerShell：

~~~powershell
Set-Location -LiteralPath 'G:\Mario Kart World\MKW-Player'
$env:JAVA_HOME = 'G:\Mario Kart World\_toolchain\jdk-21.0.13+9'
$env:ANDROID_HOME = 'G:\Mario Kart World\_toolchain\android-sdk'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
~~~

NDK 是 `27.0.12077973`，本机 CMake 是 `3.22.1`。依赖已经缓存时加 `--offline`。默认 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。前一轮默认路径里的 ARM64 `libmkw_native.so` 曾被 Windows 占用。遇到相同问题，可用以下临时初始化脚本切换构建输出目录：

~~~powershell
$mkwInit = Join-Path $env:TEMP 'mkw-alternate-build.init.gradle'
@'
gradle.beforeProject { p ->
    if (p.path == ':app') {
        p.layout.buildDirectory.set(new File(p.projectDir, 'build-audit'))
    }
}
'@ | Set-Content -LiteralPath $mkwInit -Encoding utf8
.\gradlew.bat -I $mkwInit :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline
~~~

备用目录下 APK 和测试报告分别在 `app/build-audit/outputs/apk/debug/` 与 `app/build-audit/test-results/testDebugUnitTest/`。本次模拟器为 `mkw_test`（x86_64，1080×2340）。安装 App 和测试 APK 后，可用 `adb shell am instrument -w -e class com.mylittleblover.mkwplayer.ArtworkViewportDeviceTest com.mylittleblover.mkwplayer.test/androidx.test.runner.AndroidJUnitRunner` 单跑图片视口测试。

## 本机交付物

| 文件 | 用途 |
|---|---|
| `G:\Mario Kart World\MKW-Player-dist\MKW-Player-0.1.0-alpha19-fixes-20261115.apk` | 最终测试 APK。SHA-256 `DC6F57CD4EF70700228B9DABEBAC59413557F83EAA9223A6EA77B158096869AA` |
| `G:\Mario Kart World\_packs\MKW-Complete-All.mkwpack` | 带封面的完整测试曲包 |
| `C:\Users\x\.codex\visualizations\2026\11\15\01a1df16-b176-76c0-a1ce-563f889335b9\MKW-Player-UI-Audit.zip` | 34 个页面和交互状态的审查与 56 张截图，含长截图及原比例、大字号对照 |
| `G:\Mario Kart World\_handoff_2026-11-15\` | 旧交接包、HTML 规格及研究参考 |

换机器时，先把本文和 GitHub 分支交给接手 AI。需要实机复测时另提供 APK、可用曲包和审查压缩包。
