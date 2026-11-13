# 当前交付与后续优先级（保留用户补充）

本文件记录最新用户指令，优先于旧交接包的停工/审批门禁：用户明确要求完成当前验证后直接继续，保留此前要求，不重复确认。

## 先完成当前交付

- alpha07 + `MKW-30-Courses-alpha07-final.mkwpack` + 正确参考重录的 Rainbow 16 项预览。
- 音源包 328 assets / 30 stages / 81 索引条目，格式 v1。含所有已提供 Pattern/Fast/区域声源；不宣称包含完整 Jukebox/其他 OST。
- 用户要求在实际导入并播放验证后交付。JVM 1159、Python 147、实际安装后的 81 图准备已通过；Android 30/30 赛道入口输出与 FirstCircuit Pre→Bridge→Full 实际切换已通过，设备结果 OK (1 test)，耗时 1257.405 秒。
- 首轮设备测到 FGS 首次创建超时。现已在 `PlaybackService.onCreate` 的 channel 建立后、MediaSession 初始化前进入前台；重跑验证已通过。
- 当前基线只验证原有互动路径，不宣称已完成下述新增 Pattern 开关。
- 交付完成：Filebin 两份 ZIP（APK+mkwpack / 全部现有预览）均上传并完整回下载 SHA256 一致，已向用户提供链接；有效期至 2026-11-19。附件内含说明、SHA256/验收记录。

## 随后立即做 Pattern 控制与真实预览

1. **DonkeyKong**：base + Pattern2/3/4/5 属于不同区域，不是可互换的同 Full 版本。按当前区域源 MINF label-1 节点离开，按目标实际 phase 提前进入并在锚点提交；不得拿 UI 计时或估算秒数代替采样调度。提供区域控制开关，状态跟随 Native 实际开始/提交。
2. **Cinema / SnowMountain**：base Full 与 Pattern2 Full 是同曲不同版本；显式 48000 帧双向交叉淡化，并保持 source frame/乐句进度。对应 Fast 两版本也有相同的内部 grid/loop/phase，Full 与 Fast 两档分别切版本，不能把 Full↔Fast 当 SameTime。
3. 依据是 `/workspace/mkw-planning/donkey-regions-analysis.{md,json}`、`full-versions-analysis.{md,json}` 与原始 MINF/音频。所有实现前复核原始字段，报告不等于 Native/听音验收。
4. Donkey 各 Pattern 没有已确认的专属 Outro/Fast 事件；禁止复制 base Full 的 Outro 采样坐标。数据不存在的操作应保留明确边界，不造 0 或猜映射。
5. 交接包既有要求继续有效：Pre/Short 独立、真实 Fast、单向主阶段、下一段实际样本前可取消、实际开始后不可撤销；暂停冻结播放钟；一切控制回到 Native 状态。
6. 新区域/版本的所有支持衔接均需实际 Native WAV 预览，覆盖循环边缘、返切（适用时）、1 秒包络边界、主阶段和 Fast 归属，不以配置字段自洽代替真实输出。

## 地图与官方路线阶段

- 先做地图界面和**官方已有路线**；用户后续提供每条赛道可达总表，当前不开放自定义连线，不把地理邻近或 HTML 演示当有向连接。
- 当前核对资料为 MarioWiki `Mario_Kart_World` revision 5544799 的 8 个 GP 杯赛与 12 条已发布 Rally 顺序；未来计划路线不当成已发布。数据源快照在 `/workspace/mkw-planning/official-route-sources`。
- 后续自定义规划必须有明确起点、终点；选中后仅从**当前尾节点**继续选下一赛道，不能回到旧节点反复拉出分支。保留已确认的重复经过同一赛道规则（按每次经过的 occurrence 建模），只可末尾改拓扑，不自动补路。
- 官方路线只读，不复制，不插临时曲。地图图像、图标、线、箭头和点击使用同一个可逆变换；使用现有底图/真实图标/已提供锚点，不猜位置和道路。
- 界面依选定的白底轻量 MD3 方案 A。地图截图赛道封面仍是占位，正式赛道风景图和其他音乐具体封面由用户后续提供。

## 多语言

- 新代码预留可扩展语言接口，优先简体中文、繁体中文、英语、日语（zh-Hans / zh-Hant / en / ja）。
- 新增界面字符串放 Android resources，不硬编码于 Composable；曲目/路线稳定 ID 与本地化名称分离，语言变化不重建音乐身份/路线顺序。
- Android 13+ 应用语言与更早系统的可持久化选择需要兼容，保留跟随系统选项；新语言可在统一配置中扩展。
- 原始路线/赛道名称的四语候选表与来源已抓取到 `/workspace/mkw-planning/official-route-sources/localized-names.json`，实际接入前校验与清理，不把未核实转写当官方命名。

## 仍保留的后续交接工作

完整资源库与后台原子导入/恢复、普通队列/冻结会话、Jukebox 候选池、官方路线音频编排、完整页面与无障碍/多尺寸验收等继续按交接包执行。未具备数据的部分保持明确的待补状态；不要将本次基础音源包交付称为整个应用完成。

## 最新执行边界（2026-11-13）

用户最新要求：“完成这部分后停止并提交给我”。本轮只完成 DonkeyKong 区域、Cinema/SnowMountain 版本开关及其取消/设备验收修复，交付通过验收的 APK、兼容 mkwpack 和实际预览后停止。地图/官方路线/完整多语言等已准备的后续计划保留，本轮不继续实施。此条覆盖先前自动继续后续模块的执行安排，不删除任何既有产品要求。

本轮最终状态：alpha08 三赛道新增控制和取消已通过实际 Android 组合测试（2/2）；JVM1176零失败、8项按条件跳过。混合准备解决即时版本切换的 DeadlineMiss，未修改已确认的采样衔接。完成合包/上传完整性核验后停止，后续模块仍保留。
