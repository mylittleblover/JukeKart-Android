# 页面实现续作基线（2026-11-13）

## 互动页修复

alpha08 的 `InteractiveNavigationDeviceTest` 实际点击 Interactive 后抛出 `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`。控制台外层 verticalScroll 同时嵌入 PlayScreen.verticalScroll、SettingsScreen.LazyColumn、ImportScreen.LazyColumn。

alpha09 改成单个 LazyColumn，播放器关闭内部滚动，设置/导入通过 LazyListScope 共享条目。APK 编译与 JVM 1176 项测试已运行：零失败、9 项外部素材/导出测试按条件跳过。实际触屏打开、滚动到导入按钮、返回 Library 再重新进入 Console 全部通过，最终结果 `OK (1 test)`，耗时62.888秒。测试曾误匹配同名 Library 标题，现以遍历 Compose 虚拟节点和底部标签触屏操作解决。证据在 `/workspace/mkw-planning/interactive-accessibility-*.log`。

## 剩余页面的真实现状

- 当前四个标签仍为 Library / Map / Interactive / Routes，尚未对应最终方案 A 的首页 / 媒体 / 地图 / 播放列表。
- LibraryPage 的搜索回调为空，不可将其记作可用搜索。首页每日十首轮播、收藏、继续收听入口尚未接入。
- CoursePage 已修正为按所点稳定赛道 ID 显示名称，仅在匹配当前声源时展示其结构；主体仍主要展示结构/诊断，尚未完成最终两张播放卡。
- MapPage 已改用 OfficialMapScreen：白底控件、原始赛道图标、只读官方路线、起终点、顺序箭头、赛道预览与详情入口。横竖屏、缩放/重置、站点→详情→返回路线的实际操作已通过。自定义连线与路线自动演奏尚未开放。
- RoutesPage 传入空集合及空保存回调，尚未实现官方路线库或个人列表。旧 RouteModel/nearest-neighbour 逻辑不得用来补造合法连接。
- 普通冻结队列、完整组曲预设、完整会话恢复及新导入库语义尚未贯通产品页面。新增按钮必须连接真实行为；没有素材/候选关系的功能显示具体缺失状态，不用演示数据冒充运行结果。
- 已有 Pattern 控件、地图新增文案和50条名称支持四语；统一语言设置正在接入。导航身份、地图相机与选择已通过保存恢复测试，不能据此宣称全应用已翻译。

## 已准备的可靠输入

地图/官方路线阶段执行 `docs/superpowers/plans/2026-11-12-map-official-routes-languages.md`。来源目录 `/workspace/mkw-planning/official-route-sources`：20 条已发布官方路线（8 GP，每条4站；12 Rally，每条6站）；全部站点属于现有30个地图锚点；50条名称记录含 zh-Hans / zh-Hant / en / ja 及来源 URL/hash。接入前仍需测试解析、顺序、隐藏解锁与不可变性。

自定义连接等待用户后续提供的有向表。当前仅做地图浏览和官方路线，不做自动寻路、不从旧站反复分叉。地图截图封面继续标为占位，正式风景封面与其他音乐具体封面由用户后续提供。

用户已明确授权：修复验收互动闪退后，直接按交接清单和设计稿继续剩余页面，不再执行之前“交付后停止”的边界。

## 地图阶段验收

官方路线/名称、坐标变换、相机和导航保存恢复已加入测试。全量 JVM：1206项，零失败、9项素材/导出测试按条件跳过。实际 Android 地图验收 `official-map-device-green3.log` 为 `OK (1 test)`，102.179秒；原互动页导航回归在组合测试中继续通过。矮横屏采用侧栏并调整控件布局，修复了底部卡片遮住重置按钮的问题。地图与路线仍是浏览功能，不宣称已完成整条路线音频自动编排。
