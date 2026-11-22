# Mario Kart World Official OST (120-346) <-> BGM_JukeBox_*.bwav 实际音频对照表

基于 `..\MKW`（官方 OST 348 首 `.m4a`）与 `romfs\Sound\Resource\Stream\BGM_JukeBox_*.bwav`（及 `readable_assets` 对应 48 kHz `.wav`）通过全量音频频谱与波形交叉相关实测建立的 **227 / 227 严格一对一实际对照关系**。

## 核心验证结论

- **严格 1 对 1 双射**：OST `120–346`（共 **227** 首，按 36 款游戏发行年代排序）与 227 个 `BGM_JukeBox_*.bwav` 完全一一对应，无一对多、无遗漏、无算法冲突（贪心 Top-1 与匈牙利全局指派结果 **0 例差异**）。
- **极高音频置信度**：227 对匹配的全曲 20 Hz 对数频谱相关性最低达 **0.9292**，局部 8 秒 4 kHz 波形 Pearson 相关系数达 **0.728–0.957**（仅 `#302 Sweet Sweet Canyon (Electro)` 为 `0.5416`，但其全曲频谱相关仍达 `0.9716`）；所有错误候选最高仅 **0.1218**。
- **信号级发现（不能仅靠整首定长滞后点积）**：OST `.m4a` 相对游戏 `.bwav` 普遍存在约 **-0.14s ~ -0.22s** 起始延迟与约 **-66 ppm** 采样时钟微漂移（约 3 分钟偏移 10 ms），且 OST 结尾普遍保留更长淡出/静音尾音（`+0.04s` ~ `+7.36s`，其中 `#165 Opening Melody` 多 `+7.355s`）。
- **非 Jukebox 边界确认**：OST `001–119`（止于 `119 - Staff Credits.m4a`）为游戏本篇曲目；`347 Title Theme (Bonus Track)`、`348 Crown City (Bonus Track)` 与全部 227 首 Jukebox BWAV 实测最高波形相关性仅 `0.039` / `0.051`（纯噪声水平），确认不在 Jukebox 资源库中。

## 仅凭文件名极易踩坑的关键差异（已通过音频实测修正/锁定）

### 1. 同曲多版本（21 组 44 首，`spec=0.929~0.998` vs 错配 `0.45~0.74`）
- 成对后缀 `B`/`E`（如 `M001B` vs `M001E`、`M004B` vs `M004E`、`M009B` vs `M009E`、`M011C` vs `M011E`、`M012B` vs `M012E`、`M014B` vs `M014E`、`M024B` vs `M024E`、`M026B` vs `M026E`）以及不同 M 编号双版本（`M551/M627`, `M463/M601`, `M469/M628`, `M301/M602`, `M539/M643`, `M552/M623`, `M033/M432`, `M465/M629`, `M039/M622`, `M042/M310`, `M041/M476`, `M038/M306`, `M025B/M312`）均已通过 2x2 交叉矩阵确认唯一归属。
- **特别纠偏**：`#225 Waluigi Pinball (Rock)` 对应的 `M011C_MKDS_WaluigiPingball_Funk_OutroFull.bwav` 虽在 BWAV 文件名里写着 `Funk`，但音频实测确为 Rock 版（8s 波形 `r=0.875`，而 `M011E` 对其仅 `0.064`）；`#157 Choco Island (Bossa Nova)` 对应的 `M004E` 内部名字仍写 `Samba`。

### 2. 游戏系列归属与代号不一致（按 BWAV `fields[3]` 拆分会出错）
| OST 曲号与曲名 | OST 游戏系列 | 实际 BWAV 文件名 | BWAV 内部游戏码异常 |
|---|---|---|---|
| `#255` Mario Drawing Song | Flipnote Studio | `BGM_JukeBox_M330_3DLand_PaintingSong_OutroFull.bwav` | 误归为 `3DLand` |
| `#258` Castle Boss | New Super Mario Bros. Wii | `BGM_JukeBox_M472_NewBrosU_KoopalingBattle_OutroFull.bwav` | 写成 `NewBrosU`（同作其余6首为 `WiiMario`） |
| `#271` Melty Monster Galaxy | Super Mario Galaxy 2 | `BGM_JukeBox_M550_Galaxy_MeltyMonsterGalaxy_OutroFull.bwav` | 漏掉 `2`，写成 `Galaxy` |
| `#324` Squeaky Clean Sprint | Mario Kart 8 Deluxe | `BGM_JukeBox_M343_MKTour_SqueakyCleanSprint_OutroFull.bwav` | 按首发写 `MKTour` |
| `#198` Peach Circuit | Mario Kart - Super Circuit | `BGM_JukeBox_M402_MKGBA_MarioCircuit_OutroFull.bwav` | 写成 `MKGBA`（同作其余7首为 `MKAGB`） |
| `#273` Ground Theme | Super Mario 3D Land | `BGM_JukeBox_M304_3DL_Ground_Outro.bwav` | 写成缩写 `3DL`（其余3首为 `3DLand`） |
| `#163` / `#211` | Wario Land / WarioWare, Inc. | `M337_WSeries_*` / `M342_WSeries_*` | 两款不同作品共用 `WSeries` |

### 3. 曲名共用赛道别名与内部拼写差异
- `#184 Moo Moo Farm` -> `M019B_MKN64_YoshiValley_Country`（MK64 奶牛农场与耀西山谷共用 BGM）
- `#220 Figure-8 Circuit` -> `M028_MKDS_MarioCircuit_AcidJazz`（MKDS 8字赛道与马力欧赛道共用 BGM）
- `#198 Peach Circuit` -> `M402_MKGBA_MarioCircuit`（Super Circuit 碧姬赛道与马力欧赛道共用 BGM）
- `#173/#174 Title Theme (SM64)` -> `M301 / M602_N64_Bob-OmbBattlefield`
- 日美版本/内部命名差：`#234 Egg Planet` <-> `GoodEggGalaxy`、`#235 Space Junk Road` <-> `StardustRoad`、`#269 Wild Glide Galaxy` <-> `JungleGlider`、`#266 The Starship's Journey` <-> `TheStarshipSails`、`#149 Painting Mode 1` <-> `CreativeExercise`
- BWAV 内部拼写错字/变体：`Counrty` (`M017B`)、`JungelLevel` (`M401`)、`Yoshi'sTalk` (`M553`)、`Pingball` (`M011C/E`)、`Savannah` (`M010B`)、`MainCountyard` (`M531`)、`DonutPlain` (`M007B`)、`PeachGarden` (`M625`)

## 交付文件清单
- 完整 Excel 工作簿：`马车世界_Jukebox_OST与BWAV实际对照表.xlsx`（4 个工作表：`OST_BWAV_对照总表`、`按游戏系列汇总`、`同名与特殊差异核验`、`非Jukebox与总览说明`）
- 结构化数据：`MKW-Player/docs/research/2026-11-15-jukebox-ost-bwav-mapping.json` & `.csv`（含 `ost_index`, `ost_game_order`, `ost_game_series`, `ost_display_name`, `bwav_filename`, `pack_track_key`, `bwav_sha256`, `pack_asset_sha256`, 音频相关性指标与 BPM/BARS 信息）
