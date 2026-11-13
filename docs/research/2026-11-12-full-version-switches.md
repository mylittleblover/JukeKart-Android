# Cinema / SnowMountain 版本切换

用户确认 Pattern2 是同 Full 的另一版本，采用一秒交叉淡化。MINF 校验两对 Full 以及两对 Fast 的 rate、BPM、phase、loop、逐拍 grid 完全一致。FullVersionCompiler 在生产图中增加同速双向 Immediate/SameTime，显式 fadeIn/out=graph BPM/60 拍，即 48000 帧；Full→Fast 保留各版本自己的 MINF 提前进入规则。

24 个实际 Native WAV 来自生产图（两赛道 × Full/Fast × 双向 × 曲中/loop前半秒/loop后首帧），全部零削波、欠载和解码失败。源入口从 voice at/source 还原的误差为 0；render 后 snapshot 游标相对 render 前公式差一帧。请求后 1/23999/47999/48000 帧为双 voice，48001 帧为单目标 voice。loop 前请求时旧声源真实跨界继续循环，未提前丢声。

多版本合并的全量解码会超过 Native prepared PCM 固定 128 MiB 预算；最初全部 streamed 的设备试验又证实 Immediate/SameTime 会因目标当前采样页尚未就绪而被 RuntimePreparation 返回 DeadlineMiss。最终采用混合准备：两版 Full 与两版 Fast 共 4 个长曲完整预解码（Cinema 83.05 MiB、SnowMountain 81.10 MiB，均低于128 MiB），其他 Pre/Outro 等继续 streamed。首载及重新加载都传递 preparedAssets；测试核对4个源和预算，并验证会话按名字选择绑定策略。音乐图、源位置和一秒包络没有更改。Native 立即转场在提交目标后会清除 transition active，但旧 voice 淡化仍在继续；控制器因此还检查实际 voice 数量，禁止淡化过程中发起第三路版本或 Fast。

Pack 只从已声明 stage group 中按已知 SHA256 加载对应版本，加载 bytes 再校验摘要；Pattern2 保留自己的 Fast 和已确认 Outro，不借用不适用的退出点。UI 显示 Native 实际提交的版本，四语资源已加入（en/zh-Hans/zh-Hant/ja）；并非宣称全部旧界面翻译完成。

完整渲染与快照证据在本会话 /workspace/mkw-planning/full-version-native-results.json、full-version-snapshots，实际 WAV 在 /workspace/mkw-preview-jobs/FullVersions。两赛道预览已分别上传并完整回下载校验。Android 控件验收另记最终结果，不以 host WAV 代替设备验证。

2026-11-13 实际 Android 混合准备版本已通过 FullVersionControlsDeviceTest：Cinema/SnowMountain 分别 Full2→Fast2→Fast1，实际输出活动且 Native 版本/阶段已提交，设备结果随最终交付保留。组合测试的 Donkey 部分另行验收，不把前一个 test 的通过描述为整套通过。
