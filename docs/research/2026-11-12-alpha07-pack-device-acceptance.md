# alpha07 实际音源包导入与播放验收

Android 15 x86_64 模拟器，安装最终 alpha07 APK，调用应用真实 importPack 路径导入交付文件。

- 音源包 SHA256：`adf8a6fcf98055342619863e11fb201a39e8d449f42172066bb174a69972e1a5`。
- 导入后 81/81 索引可播放，30/30 赛道通过已安装 stage 选择产生 Native 输出并推进 sourceFrame。
- 非 Rainbow 的 29 个入口均恢复 Fast。FirstCircuit 实际完成 Pre→Bridge→Full。
- Instrumentation：`OK (1 test)`，1257.405 秒。完整逐赛道记录与设备测试日志随交付包提供。
- 首轮发现前台服务创建超时；onCreate 中建立 channel 后立即进入前台，先于 MediaSession 初始化。最终重跑通过。
- APK v2 签名校验通过。既有 JVM 1159 项零失败（6 项按条件跳过），Python 147 项通过；真实安装包的 81 个图准备通过。

此验收证明导入、音频设备输出活动与 Native 采样推进；模拟器不代替实体手机扬声器听音或全程 UI 流畅度验收。新要求的 DonkeyKong 区域及 Cinema/SnowMountain 版本开关尚未包含于 alpha07，后续继续接入。
