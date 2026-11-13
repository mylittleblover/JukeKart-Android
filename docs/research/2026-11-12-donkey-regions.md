# DonkeyKong 区域与待执行切换取消

用户确认 base Full 与 Pattern2/3/4/5 是区域切换。DonkeyRegionCompiler 在生产 MINF 图上新增 5 个区域与 20 条有向边；源退出点来自每个源自己的 label=1，包含非循环前奏内的原始点，目标按真实 phase 提前进入并在 G 提交。当前五个目标 phase 都是 0，不使用 SameTime 或一秒版本淡化。Pattern 没有确认的 Outro/Fast 标记，因此只开放区域间切换，需返回区域 1 才可使用已验证的结束/Fast。

40 份实际 Native WAV（20 个方向 × early/loop-end）已全部成功，sourceSync、destinationEntry、firstStart 均等于原始首拍。最早测试 G=132413，循环末端分别为 2979305、993101、1191722；没有把末端前后一帧四舍五入。首次 voice 观测的 transport-source 恰等于 G；未将后续 cursor 推演描述成直接观测 G 时刻 source=0。全部 PCM16 双声道48kHz，零削波、欠载、解码失败，长度另经 WAV 头独立复核。

首次 Native 验证拒图：向 base cue 列表追加原始 region grid 后没有整体排序，违反 GraphValidation.hpp 的单段 cue 时间序要求。失败报告保留为 invalid-cue-order，新增排序回归观察失败后修正；40 个生产图重录通过。

区域按钮为四语言资源，选中/可用状态跟随 Native；pack 仅加载同 stage group 声明的已知 SHA 对应音源，补充字节再次核验摘要。合并图使用 streamed，避免 Native prepared 128 MiB 预算超限。

新增稳定命令 ID17 CancelPendingTransition，不用 Play(source) 冒充取消。Native 仅接受尚未启动 destination/bridge、尚未离开 source 的待执行转换，清理排队目标并调用既有取消清理。首样本前一帧和边界未发声时可取消，开始后返回 InvalidRequest，目标继续。Driver 等待实际 Native 取消确认，观察 phase=0 才提供取消；若点击与样本开始竞态，保留真实已启动目标。Session 清理自动转换的 pending 标记并保留源去重标记，不重复提交同一下一 Pre。

最终 Host 取消强化测试在 /workspace/mkw-planning/cancel-native-reference-results.json：40/40 用例，每例 G−1（含暂停）、G、G+1；成功取消后的 PCM 与不中断参考逐采样一致（最大误差 0），播放钟及 G+1024 的 source phase 一致，晚取消返回 InvalidRequest 且目标继续。最初绝对 cursor 判据在精确 EOF 暂无活动 Voice 时误报 20 个循环末端用例，原报告保留为 cancel-native-absolute-cursor-check-results.json；没有改动 Native 取消语义来迁就验收。

NativeDriver 完成状态另须 transport stopped，不能仅凭精确循环 EOF 的一帧 voices=0 判定结束；新增失败回归后修正。停止时清空旧区域/版本可用状态，重播重启前台服务，避免旧 Stop 延时任务撤掉通知。

Android 控件验收仍待最终结果。设备实际导入后立即选择 Cinema 暴露前台服务启动竞态：第二次主线程音源准备可能抢在 onCreate 之前阻塞；已增加逐请求 generation 的前台确认门闩，onStartCommand 完成后仅确认其对应请求，旧回调不能释放更新的重载；Stop/新选择使旧等待失效。软件模拟器的测试框架冷启动也出现过系统级 ANR，与该实际服务竞态分别记录，均不据此宣称设备验收通过。

## 最终设备验收（2026-11-13）

混合准备版 alpha08 的组合 Android 测试最终 `OK (2 tests)`，924.204 秒。Donkey 五区域选择、目标开始前取消、返回基础区域、停止后重播及媒体通知保持通过；Cinema/SnowMountain Full/Fast 版本控制同时通过。完整结果为本会话 android-alpha08-hybrid-final.log、alpha08-accepted-donkey-regions.json、alpha08-accepted-full-versions.json，随交付包提供。前述失败记录用于保留诊断过程，不代表最终状态。
