# MKW 原声带曲包（`.mkwpack`），格式版本 1

**简体中文** | [繁體中文](PACK-FORMAT.zh-Hant.md) | [English](PACK-FORMAT.md) | [日本語](PACK-FORMAT.ja.md)

本文档即协议本身。撰写本文档的目的，是让第二位实现者无需阅读本仓库的 Kotlin 代码即可编写曲包写入器或读取器，也让后续维护者在未看清规则所防范的问题之前，不会「顺手简化」某条规则。

状态：`formatVersion` 1。读取器与数据模型位于
`app/src/main/kotlin/com/mylittleblover/mkwplayer/pack/`。

---

## 0. 为什么需要这一格式

《马力欧卡丁车 世界》的赛道主题曲并不是单个文件，而是一组由游戏在播放时切换的片段：短前奏（short prelude）、完整前奏（full prelude）、过渡桥段（bridge）、主循环（main loop）以及一个或多个尾奏（outro）。以 `BGM_Stage_Coaster` 的实测数据为例：

| part | frames | length | loop |
|---|---:|---:|---|
| Pre_Short | 534,857 | 11.143 s | 205714..534857 |
| Pre | 2,427,426 | 50.571 s | 452571..2427426 |
| Bridge | 88,320 | 1.840 s | none |
| Main | 3,373,711 | 70.286 s | 493714..3373711 |
| Outro00 | 226,286 | 4.714 s | none |

因此，一条赛道就有五到八个音频资产。三十条赛道大约两百个文件，再加上点唱机目录又会增加数百个文件。要求用户逐个手动挑选这些文件不是功能，而是缺陷：他们在听到任何声音之前，就必须按正确组合先挑完数百个文件。

曲包用一次导入取代了这一过程。单个文件即可携带全部音频资产、赛道结构、音乐图配置、标记/cue 数据、路线数据、封面插图与校验和。

因此，这一协议必须回答普通音乐格式不需要回答的问题：
**一个文件如何明确声明哪些是它确知的事实、哪些只是它做出的选择、哪些是它无法验证的，并且使运行时不被允许擅自「优化」或改写这些声明？**

---

## 1. 容器

### 1.1 为什么使用 ZIP64

`.mkwpack` 是一个 **ZIP64** 归档文件。不是 ZIP32，不是自定义流格式，也不是 tarball。

ZIP32 使用 32 位字段存储大小和偏移量，无法装下无损原声带曲包：

- 30 首赛道主题曲，每首 5 个片段，平均每段 60 秒 48 kHz 16-bit 立体声，约为
  1.7 GB（`48000 × 2 ch × 2 B = 192,000 B/s`，每分钟约 11.5 MB）。
- 实测目录中的点唱机曲集有 227 首曲目。按保守估计每首 3 分钟计算，仅这一项就约 7.8 GB。
- 过场动画、序列音乐、对战主题曲、封面插图与校验索引还会进一步增加体积。

完整曲包通常会超过 10 GB，32 位归档连寻址都做不到。ZIP64 将大小与偏移字段都提升到了 64 位。ZIP64 同时移除了 65,535 条目的上限，这一点很关键，因为曲包会为每项资产以及每张音乐图、标记集、路线和插图记录各占一个条目。

ZIP64 还提供了导入路径所需的能力：中央目录（central directory）。读取器无需解密或解码任何内容即可列出并校验每个成员，还可以逐个流式读取成员。如果使用单一拼接流，则必须完整扫完一遍才能检查第一个资产。

### 1.2 读取器在读取任何内容前必须强制执行的规则

1. **归档根目录必须存在名称完全为 `manifest.json` 的文件，且它应当（SHOULD）是第一个条目**，这样读取器在触碰 10 GB 音频前就能拒绝不可用的曲包。
2. 成员名称受到严格限制。遇到符合以下任一条件的成员名称，读取器必须（MUST）拒绝：
   - 是绝对路径，或包含盘符；
   - 包含 `..` 作为路径段；
   - 包含反斜杠；
   - 不在第 2 节规定的某个目录下；
   - 包含多于一个路径分隔符（目录布局严格只有一层深）。

   这不是纸上谈兵：否则名为 `../../files/library.json` 的 ZIP 成员就会被写到暂存目录之外。第 2 节的目录检查就是全部防线，因此绝非可选项。
3. 不使用加密。读取器一旦遇到加密条目即拒绝该曲包。
4. 音频条目采用 `STORE`（方法 0）存储：已压缩音频无法进一步压缩，且未压缩存储的条目无需解压即可对照其声明大小进行校验。文本条目（`manifest.json`、`catalog.json`、图、标记、路线）可以采用 `DEFLATE`。插图二者皆可。读取器必须同时支持这两种方式。
5. `.mkwpack` 扩展名只是声明，不是安全边界。读取器通过 `manifest.json` 识别曲包，绝不依据文件扩展名或平台报告的 MIME 类型。

### 1.3 媒体类型

文件媒体类型为 `application/zip`。客户端在磁盘上的文件名形式为 `<packId>-v<packVersion>.mkwpack`，但文件名仅供展示参考：真正的身份标识保存在清单中。

---

## 2. 目录布局

```
manifest.json                   必需，位于根目录，首个条目
checksums.json                  必需，非资产成员的完整性索引
catalog.json                    必需，曲包自身对其曲目的目录清单
audio/<sha256>                  每项资产一个条目；无扩展名，无子目录
music-graphs/<trackSlug>.json   每首带有已编译图的曲目一个（可选）
markers/<stageCode>.json        每条赛道的 cue/marker 数据（可选）
routes/<routeId>.json           路线预设（可选）
artwork/<sha256>.<ext>          图像，按内容寻址（可选）
```

根目录下不得出现任何其他文件。位于这些目录之外的成员一律拒绝，即使它看起来无害——因为「未知的额外文件」正是曲包开始携带无人校验载荷的开端。

`music-graphs/` 内的成员名是推导出来的，而非声明的：将曲目键（track key）中的 `:` 替换为 `_`。例如曲目键 `course:1111...`（32 个十六进制字符）对应
`music-graphs/course_1111....json`。通过推导生成文件名意味着没有任何清单字段需要携带路径，清单也就不可能指向归档外部。

`markers/<stageCode>.json` 和 `routes/<routeId>.json` 采用相同规则：赛道代号（stage code）或路线 ID 必须匹配 `[A-Za-z0-9_-]{1,64}`，并按原样用作文件名。

### 2.1 `audio/` 按内容寻址

条目名称**就是**该资产的 SHA-256 摘要，64 个小写十六进制字符，无扩展名，无子目录。由此带来的性质：

- 归档内不可能以两个名称存放同一资产的两份副本；
- 提取 `audio/<x>` 的读取器只需对字节计算哈希并与 `<x>` 比对即可完成校验，无需查阅任何查找表；
- 资产在曲包之间迁移时保持同一身份。

媒体类型和编解码器由清单**声明**，而不从文件扩展名推断——因为本来就没有扩展名。

---

## 3. 稳定身份标识

### 3.1 资产 ID

资产是其精确字节序列的 SHA-256 摘要，写作 64 个小写十六进制字符，并且它是**引用资产的唯一方式**。绝不用路径、绝不用文件名、绝不用资产列表中的索引。

该摘要基于存储的字节计算，而非解码后的音频。重新编码的文件属于不同的资产，协议如实反映这一点而不假装它们相同：摘要是该格式无需信任任何人即可做出的唯一声明。

### 3.2 逻辑曲目键

逻辑曲目是用户收听的单个曲目单元，由一个或多个资产组成。其键是推导出的，而非任意选取：

```
trackKey = <小写的 kind> ":" <主资产摘要的前 32 个十六进制字符>
```

例如 `course:5d41402abc4b2a76b9719d911017c592`。其中 `kind` 为目录类别之一（`course`、`jukebox`、`battle`、`sequence`、`cutscene`、`mission`、`demo`、`sound_effect`、`boot`、`unclassified`）。

有两个刻意设计的结果：

- 同一主资产既用作赛道主题曲又用作点唱机条目时，它们是**两条**曲目，因为 `kind` 是键的一部分。将它们合并会静默丢失其中之一。
- 曲目键不能被重命名。如果曲包声明了一个与其自身 `kind` 和主资产摘要不匹配的键，该曲包即无效（`TRACK_KEY_INCONSISTENT`）。能被公告改写的「稳定」键算不上身份标识。

### 3.3 播放配置引用 ID

已编译的图（`music-graphs/`）通过摘要引用资产，并通过 `(trackKey, markerId)` 引用 cue。它绝不引用文件中的位置、目录行号或缓存文件。因此，图描述的是**内容**，而非**位置**。

### 3.4 这样设计的收益与代价

由于身份由内容推导，以下操作**不会**使用户保存的任何内容失效：

- **重新发布曲包。** 新版曲包若只是补充插图、路线、更多尾奏或修正显示名称，所有未改动资产的摘要保持不变。已保存的引用这些资产的收听版本依然可解析，音乐图也依然可编译。
- **支持另一种编解码器。** 新版程序学会解码 codec 2 后，改变的是它能**播放**什么，而不是资产**是什么**。没有任何 ID 会移动。原本无法播放的曲目会以完全相同的身份变为可播放。
- **在磁盘上移动或重命名任何文件。** 格式中不包含任何路径。

唯一会改变身份的操作是**重新编码音频**，因为字节变了，而摘要如实反映变化。这并不是身份系统故障，也不能被当作原地升级处理：

- 重新编码的资产会获得新摘要，进而得到新的曲目键；
- 携带它的曲包必须提升为新的曲包版本，且只要还有已保存的收听版本引用旧资产，旧资产就不得删除；
- 应用必须将重编码后的曲目展示为新增条目，而绝不能用相同 ID 进行替换。

因此安装器的铁律是：**永远不要删除仍有已保存用户版本引用的已安装资产，也永远不要在已有 ID 下重写资产内容。** 若一次导入试图在相同摘要下用不同字节替换既有资产，该曲包即属于损坏包并必须被拒绝（校验和比对会捕获此事）。

---

## 4. 曲包必须记录什么，以及运行时不得推导什么

应用在开发导入路径中确实有从文件名推导下列事实的代码（`StageFlowDiscovery.prefixOf`、`StageFlowReader.roleOf`）。那些代码的存在是为了让手动挑选的文件也能使用。**它绝不能针对曲包内容运行。** 从文件名推导的角色只是猜测，一旦清单被接受，曲包里的猜测就会与明确陈述的事实真假难辨。

因此，本仓库中的曲包读取器完全没有任何 `roleOf` 风格的辅助函数：下面每一项事实都直接从字段读取，缺失字段即判定失败，绝不回退猜测。

| 事实 | 曲包声明的位置 | 靠猜测会变成什么样 |
|---|---|---|
| 段落角色（Pre_Short、Pre、Bridge、Main、Outro） | `tracks[].parts[].role` | 把 `_Fast` 当成 Main，或者因为子串匹配把 `_Pre_Short` 当成 Pre |
| 哪个尾奏对应哪一个 | `tracks[].parts[].outro` | 只保留先读到的那个尾奏文件，导致另一个结局消失 |
| cue 位置及其证据等级 | `markers/<stage>.json`，附带 `provenance` | 把标记偏移量当成游戏调度切换的时机来展示 |
| 循环窗口 | `assets[].loopStart` / `loopEnd` | 循环整个文件，导致前奏一直循环播放 |
| 过渡同步点 | `tracks[].timing` | 随便选个 `Immediate`，然后把结果称为「游戏的过渡方式」 |
| 主循环重复次数 | `tracks[].timing.mainRepeats` | 默认设为 1 或 4；两者都会改变单次收听的时长 |
| 淡入淡出长度 | `tracks[].timing.bridgeFade{In,Out}Beats` | 凭空捏造一个从未测量的可听交叉淡变 |
| 赛道到曲目的映射 | `stages[].tracks` | 按显示名称匹配，而显示名称会随本地化翻译变化 |

如果某项事实缺失，读取器**拒绝受影响的曲目**，而不会替它补全。对于上表中的任何一行，本格式都没有默认值，这也是为什么省略字段会触发校验失败而不是被当作 0。

---

## 5. `provenance`（来源/证据等级）是必填字段

每一条声明的 `timing` 都必须带有对应的 `provenance` 记录。声明了 `timing` 却没有对应来源记录的曲包无效（`TIMING_WITHOUT_PROVENANCE`），而 `provenance` 列表为空的曲包同样无效（`PROVENANCE_MISSING`）。

等级划分。以下四个等级构成了 v1 的完整词表；新增第五个等级属于格式变更，而非扩展。

| 等级 | 含义 | 示例 |
|---|---|---|
| `MEASURED_L1` | 资源结构事实：容器中的字段、指定位置处的标记记录。 | 研究导出结果中的 174 个 `Full_ToOutroNN` 标记位置 |
| `MEASURED_L2` | 通过直接测量音频本身确立的事实。 | 解码波形与参考渲染逐采样一致；从 BWAV 头部读取的循环坐标 |
| `CHOSEN_BY_PROJECT` | 由于没有证据确立其他方案，本项目做出的选择。 | `preludeExit = EXIT_CUE`、`mainRepeats = 2` |
| `UNVERIFIED` | 已记录，但没有任何证据证实。保留该值以便界面展示并如实说明。 | 含义尚未确定的尾奏名称 |

规则：

1. 未知等级一律拒绝，绝不赋默认值。声称了当前版本不认识的等级的曲包必须明确失败，而不能降级按最弱等级读取：否则 `L3_VERIFIED` 这样的声明就会被洗白成「未验证」，再被当作数据展示出来。
2. 每条记录都指明其主体（subject）与所覆盖的字段，因此来源声明无法脱离其描述的值而悬空存在。对于 `timing`，主体是曲目键，字段是 `timing`。
3. `MEASURED_L1` 级别的标记位置并不能说明游戏**何时使用**某次过渡。曲包绝不能把 cue 列表标注为调度表。v1 中标记文件自身的 `provenance` 字段固定为 `L1_MARKER_ONLY`，正是出于这个原因。
4. **段落时序（section timing）永远不得标注为已测量。** `preludeExit`、`bridgeEntry`、`outroEntry`、`outroSwitch`、`mainRepeats` 以及桥段淡入淡出长度都是调度决策。没有任何容器字段或音频测量能确立游戏何时执行切换，因此等级为 `MEASURED_L1` 或 `MEASURED_L2` 的时序记录属于无效（`TIMING_PROVENANCE_OVERCLAIMS`）。这是本格式唯一不仅附加免责声明、而且直接拒绝声明的地方：标记偏移确实是文件的真实 L1 事实，但把它的权威性套用到时序调度上，正是本项目曾经不得不推翻重来的那一次替代错误。
5. 来源随数据同行。界面凡是展示该值的地方就必须展示其来源；运行时绝不可丢弃它，因为不带等级展示的时序会被读者当成既定事实。

---

## 6. 覆盖度与编解码器兼容性必须声明

清单在 `coverage` 中声明：

- `totalLogicalTracks`：曲包共包含多少条逻辑曲目；
- `playableLogicalTracks`：曲包自身声称其中有多少条可播放；
- `codecKinds`：曲包中任意资产使用到的所有编解码器类型。

`playableLogicalTracks` 是关于曲包本身的陈述，而不是关于读取器的陈述：它的意思是「曲包已列出其全部资产且大小均非零的曲目数」。读取器会根据资产列表重新计算该值，若声明数量无法由曲包自身内容支撑则拒绝该曲包（`PackCoverage.Coverage.declaredCoverageMatches`）。曲包不能只列出 412 条却声称有 650 条可播曲目。声明的 `codecKinds` 列表也以同样方式校验：它必须恰好等于资产实际用到的类型集合，否则漏报一个类型就会把不可解码的资产藏在看似诚实的数字背后。

当前构建版本**实际**能播放多少是另一项独立计算，依据读取器的解码能力得出：

- `PackCoverage` 报告当前构建版本能解码曲包中的多少条曲目；
- 列出曲包中存在但当前构建版本无法解码的每一种编解码器及其影响的曲目数；
- 只要存在任何不可播放的曲目，`isComplete` 即为 `false`。

文案规则非常严格：**当曲包包含不可解码资产时，应用绝不能显示「100 %」。** 覆盖度类型刻意完全不暴露百分比字段，从源头杜绝四舍五入进位。它只提供计数和两句说明。

这里存在两个不同的完整性事实，把它们混为一谈正是本节要防止的错误：

| 属性 | 回答的问题 | 何时为 false |
|---|---|---|
| `isPackComplete` | 文件本身在内部是否一致且完整？ | 声明数量与内容不符、包内缺少资产、某条赛道引用了不存在的曲目 |
| `isComplete` | 当前构建版本能否播放文件包含的全部内容？ | 因任何原因存在任意一条不可播放的逻辑曲目 |

一个曲包完全可以「自身完整，但并非全部可播」，而且界面必须把两件事都说出来：「曲包完整；当前版本可播放其中 650 首中的 412 首」是一句真话，而「100 %」不是。

---

## 7. `FULL` 包与 `DELTA` 包

```json
"kind": "FULL"    // 或 "DELTA"
```

`FULL` 包自身即为完整曲包。`DELTA` 包用于修改一个已安装的基础包（base pack），且必须指明它：

```json
"kind": "DELTA",
"basePackId": "org.example.mkw.base",
"basePackVersion": 4
```

规则：

1. `DELTA` **必须**指明 `basePackId` 与 `basePackVersion`（`DELTA_WITHOUT_BASE`）。仅有版本号是不够的：两个不同曲包的版本 4 并不是同一个基础包。没有基础包 ID 的增量包会被应用到当时恰好已安装的任意曲包上，从而拼出两个产物的混合体。
2. `FULL` 包**不得**指明基础包（`FULL_WITH_BASE`）。指定基础包会使曲包变成增量包，而将其作为完整包导入则会静默丢弃基础包原先持有的全部内容。
3. 增量包必须向前递增：`packVersion > basePackVersion`（`DELTA_NOT_FORWARD`）。
4. 若增量包所依赖的基础包未安装、已安装版本不一致或 `packId` 不一致，将在**写入任何内容之前被拒绝**。绝不半途应用：暂存目录会被丢弃，已安装曲包保持原封不动。
5. 增量包只携带它所变更的成员。应用增量包的结果是以增量内容覆盖基础内容（按摘要按键叠加）。v1 中增量包不得删除内容：若需要移除某些内容，应重新发布一个新的 `FULL` 包。
6. 增量包不得更改已冻结基础包的解锁范围。如果基础包已冻结，增量包的 `unlock` 块必须与之完全一致；否则拒绝导入，因为能改写合格赛道列表的增量包可以把用户已经听过的赛道塞进去，从而把奖杯解锁条件缩短到形同虚设。

---

## 8. 解锁契约

特别杯的合格赛道列表来自**曲包自身经过校验且已冻结的逻辑曲目列表**，绝不来自用户恰好导入了什么文件。

```json
"unlock": {
  "frozen": true,
  "stages": ["Coaster", "PeachBeach", "..."]
}
```

- `stages` 是奖杯所需的已冻结赛道代号列表。它必须非空，且每一项都必须是曲包所描述的赛道，同时该赛道包含的曲目在曲包中确实存在（`UNLOCK_SCOPE_EMPTY`、`UNLOCK_STAGE_UNKNOWN`、`UNLOCK_STAGE_INCOMPLETE`）。
- `frozen` 声明该列表在此曲包版本下不会再变更。只有已冻结的列表才能定义奖杯资格。
- 若曲包不是 `FULL`、其范围未冻结、内容不完整，或范围中包含了当前构建版本无法解码的曲目，则产生的**合格赛道数为零**。上述任一情形下 `PackCoverage.cupEligibleStages` 都返回空集，并由 `cupBlocker` 指出具体原因。

为什么资格绝不能来自用户的文件：如果那样做，资格就成了「导入了什么」的属性。只导入一条赛道、听满三十秒，单元素集合上的 `allCompleted` 就会为真，特别杯随之直接解锁。`CupEligibility` 中的 `set.isNotEmpty()` 堵住了空集漏洞，但堵不住单赛道漏洞：单元素集合并不空。唯一的防线，就是让这个集合来自用户无法编辑、导入流程也无法缩减的声明文档。

「冻结」就是字面意义的冻结：该列表属于特定曲包版本的属性。改动该列表的重新发布必须作为新版本，且只有当旧列表从未被满足时，应用才可采纳新列表。增量包则完全不得改动它（第 7 节规则 6）。

---

## 9. 导入：原子性与校验

一次导入绝不能损坏它所替换的对象。

### 9.1 流式读取，绝不整体缓冲

曲包通过归档的中央目录按条目逐一读取。没有任何步骤会把整个曲包或单个资产完整读入内存：音频资产合计可达数百 MB，而应用运行在手机上。每个资产都会在计算哈希的同时，通过固定大小的缓冲区（64 KiB）复制到暂存路径。

### 9.2 逐资产校验

对于每一个音频成员，导入器会：

1. 检查条目名称是否为 `audio/<digest>`，且摘要为 64 个小写十六进制字符；
2. 在读取数据**之前**检查声明的 `byteSize` 是否与条目的未压缩大小一致（不一致则不写入任何数据直接中止）；
3. 流式传输字节并同步计算 SHA-256；
4. 将计算出的摘要与成员名以及清单中的 `assets[].digest` 进行比对；
5. 检查提取出的资产集合是否与清单的资产列表完全一致：既无缺失条目，也无多余条目。

任一成员在以上任意一步失败，都会中止整个导入。只通过部分校验的曲包不会被安装，而是直接删除。

`checksums.json` 用 `sha256` 和 `bytes` 覆盖了每一个非音频成员（`manifest.json`、`catalog.json`、图、标记、路线、插图）。它无法覆盖自身；它的完整性建立在归档文件自身完好之上，且协议不做超出损坏检测之外的保证。**v1 中没有签名机制**，因此校验和检测的是损坏，而非伪造。

### 9.3 先暂存，再替换

```
staging/  <packId>/<packVersion>.tmp/     <- 所有内容先写到这里
    audio/<sha256> ...
    manifest.json ...
```

1. 提取并校验到 `<library>/.staging/<random>/`。目录名随机生成，以防先前失败的尝试被恢复成半合并状态。
2. **先**解析并校验清单，再提取音频。结构无效的曲包必须零代价失败。
3. 对于增量包，在写入任何成员之前先解析并校验基础包。若基础包与 `(basePackId, basePackVersion)` 不匹配，则未写入任何内容即拒绝并中止。
4. 全部成功后，对暂存目录执行 `fsync`，随后**原子重命名**为 `<library>/packs/<packId>/<packVersion>/`。同一文件系统内的重命名是原子的：即使发生崩溃，磁盘上要么是旧目录树，要么是新目录树，绝不会出现混合体。
5. 通过临时文件与 `Files.move(..., REPLACE_EXISTING)` 更新已安装曲包指针（`installed.json`），与 `LibraryStore` 采用相同模式。指针才是提交点：在指针移动之前，新目录树处于未被引用状态，随时可删除。
6. 发生任何失败时，删除暂存目录并保持原先已安装曲包原封不动（包括指针）。向用户报告的错误信息应指明失败的资产或成员。仍被已保存收听版本引用的旧曲包必须保留，不得替换。
7. 绝不直接向已安装目录树解压覆盖，也绝不在导入过程中删除既有资产。垃圾回收是一项独立的显式操作，必须先查阅已保存的收听版本。

---

## 10. `manifest.json` 参考

所有键名区分大小写。未知键将被忽略；已知键的未知**值**将被拒绝（第 12 节）。

### 10.1 顶层字段

| Key | Type | Required | Rule |
|---|---|---|---|
| `formatVersion` | integer | 是 | 必须为 `1`；其他任何值直接拒绝，不再继续读取 |
| `packId` | string | 是 | `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`；它会成为目录名，因此不得包含分隔符或 `..` |
| `packVersion` | integer | 是 | >= 1，随每个 `packId` 递增 |
| `name` | string 或省略 | 否 | 人类可读名称；省略或空白时回退为 `packId` |
| `kind` | `"FULL"` 或 `"DELTA"` | 是 | 其他任何值一律拒绝 |
| `basePackId` | string 或 null | `DELTA` 时 | `DELTA` 时必填，`FULL` 时禁止填写 |
| `basePackVersion` | integer 或 null | `DELTA` 时 | `DELTA` 时必填，`FULL` 时禁止填写，>= 0 |
| `coverage` | object | 是 | 见 10.2 节 |
| `assets` | array | 是 | 见 10.3 节 |
| `tracks` | array | 是 | 见 10.4 节 |
| `stages` | array | 是（可为空） | 见 10.5 节 |
| `provenance` | array | 是 | 见 10.6 节 |
| `unlock` | object | 是 | 见 10.7 节 |

### 10.2 `coverage`

| Key | Type | Rule |
|---|---|---|
| `totalLogicalTracks` | integer | >= 0；对于完整包必须等于 `tracks` 中的条目数 |
| `playableLogicalTracks` | integer | `0 <= n <= totalLogicalTracks`；大于总数的值在解析阶段即被拒绝 |
| `codecKinds` | integer 数组 | 所有资产使用到的每一种编解码器类型；必须存在（仅当空曲包时才可能为空，而空曲包本身无效） |

### 10.3 `assets[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `digest` | string | 是 | 64 个小写十六进制字符；格式错误的值一律拒绝 |
| `byteSize` | integer | 是 | 音频必须 >= 0 且 > 0；负数和零均为校验失败 |
| `mediaType` | string | 是 | 非空白；例如 `audio/x-bwav` |
| `codecKind` | integer | 是 | >= 0；对应 BWAV 声道编解码器类型，`1` = DSP-ADPCM |
| `sampleRate` | integer | 是 | > 0 |
| `channels` | integer | 是 | > 0 |
| `frames` | integer | 是 | > 0 |
| `loopStart` | integer 或 null | 否 | 与 `loopEnd` 成对出现，`0 <= start < end <= frames` |
| `loopEnd` | integer 或 null | 否 | 省略表示该资产不循环；绝不为 0 |

无循环时取值为 `null`，绝不是 `0`。第 0 帧是一个真实位置；`0..0` 的循环意味着声称该资产在零长度区间上循环。

### 10.4 `tracks[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `key` | string | 是 | 必须等于 3.2 节的推导结果，否则报错 `TRACK_KEY_INCONSISTENT` |
| `kind` | string | 是 | 目录类别之一，按枚举名原样书写（例如 `COURSE`、`SOUND_EFFECT`） |
| `title` | string 或省略 | 否 | 仅用于显示；省略或空白时回退为 `key`；绝不用于匹配 |
| `primary` | string | 是 | 资产摘要；必须出现在该曲目自身的 `parts` 中 |
| `isFlow` | boolean | 是 | 当曲目的各片段在同步点切换时为 `true` |
| `parts` | array | 是 | 至少包含主资产；每个资产对应一条，有序排列 |
| `timing` | object 或 null | flow 时 | `isFlow: true` 时必填，否则忽略 |

`parts[]`：

| Key | Type | Rule |
|---|---|---|
| `asset` | string | 资产摘要；必须存在于 `assets` 中 |
| `role` | string | `PRE_SHORT`、`PRE`、`BRIDGE`、`MAIN`、`OUTRO` |
| `outro` | integer 或 null | `OUTRO` 时必填（`>= 0`），其他所有角色禁止填写；同一曲目内唯一 |

role 与 sync-point 词表均为冻结字符串，刻意不与 Kotlin 标识符名称绑定：重命名 Kotlin 枚举绝不应静默改变协议。

`timing`：

| Key | Type | Required | Meaning |
|---|---|---|---|
| `preludeExit` | sync point | 是 | 允许离开前奏的位置 |
| `bridgeEntry` | sync point | 是 | 允许进入桥段 / 交接给 Main 的位置 |
| `outroEntry` | sync point | 是 | 允许请求切换至尾奏的位置 |
| `outroSwitch` | sync point | 是 | 允许替换待切换尾奏的位置 |
| `mainRepeats` | integer | 是 | >= 1；在提供尾奏前 Main 需播放的遍数 |
| `bridgeFadeOutBeats` | number | 否，默认 0 | >= 0 |
| `bridgeFadeInBeats` | number | 否，默认 0 | >= 0 |

同步点取值：`IMMEDIATE`、`NEXT_BEAT`、`NEXT_BAR`、`NEXT_GRID`、`EXIT_CUE`、`NEXT_CUE`、`NEXT_CUSTOM_CUE`。

缺失或未知的同步点将被拒绝。如果把默认值设为 `IMMEDIATE`，就会抹平该字段原本存在的意义。

### 10.5 `stages[]`

| Key | Type | Rule |
|---|---|---|
| `code` | string | 非空白，`[A-Za-z0-9_-]{1,64}`，唯一 |
| `name` | string 或省略 | 仅用于显示；省略或空白时回退为 `code` |
| `tracks` | string 数组 | 逻辑曲目键；每一项都必须存在于 `tracks` 中 |

允许一条赛道命名多条曲目是有意为之：一条赛道可以分别携带它的赛道主题曲、对战编曲以及变速版本作为独立的逻辑曲目。

### 10.6 `provenance[]`

| Key | Type | Rule |
|---|---|---|
| `subject` | string | 曲目键、赛道代号或 `pack` |
| `field` | string | 该记录覆盖的字段，例如 `timing` |
| `level` | string | `MEASURED_L1`、`MEASURED_L2`、`CHOSEN_BY_PROJECT`、`UNVERIFIED`；未知值一律拒绝 |
| `note` | string 或 null | 可选的人类可读说明，解释该值来源 |

### 10.7 `unlock`

| Key | Type | Rule |
|---|---|---|
| `frozen` | boolean | 仅当为 `true` 时才能定义奖杯资格 |
| `stages` | string 数组 | 非空；每一项都必须是 `stages` 中存在的赛道代号 |

### 10.8 示例

```json
{
  "formatVersion": 1,
  "packId": "org.example.mkw.base",
  "packVersion": 1,
  "name": "MKW Soundtrack Pack",
  "kind": "FULL",
  "basePackId": null,
  "basePackVersion": null,
  "coverage": {
    "totalLogicalTracks": 2,
    "playableLogicalTracks": 2,
    "codecKinds": [1, 2]
  },
  "assets": [
    {
      "digest": "1111111111111111111111111111111111111111111111111111111111111111",
      "byteSize": 1024,
      "mediaType": "audio/x-bwav",
      "codecKind": 1,
      "sampleRate": 48000,
      "channels": 2,
      "frames": 48000,
      "loopStart": 12000,
      "loopEnd": 48000
    },
    {
      "digest": "2222222222222222222222222222222222222222222222222222222222222222",
      "byteSize": 2048,
      "mediaType": "audio/x-bwav",
      "codecKind": 1,
      "sampleRate": 48000,
      "channels": 2,
      "frames": 24000
    }
  ],
  "tracks": [
    {
      "key": "course:11111111111111111111111111111111",
      "kind": "COURSE",
      "title": "Coaster",
      "primary": "1111111111111111111111111111111111111111111111111111111111111111",
      "isFlow": true,
      "parts": [
        { "asset": "1111111111111111111111111111111111111111111111111111111111111111", "role": "MAIN" },
        { "asset": "2222222222222222222222222222222222222222222222222222222222222222", "role": "OUTRO", "outro": 0 }
      ],
      "timing": {
        "preludeExit": "EXIT_CUE",
        "bridgeEntry": "NEXT_BAR",
        "outroEntry": "NEXT_BAR",
        "outroSwitch": "IMMEDIATE",
        "mainRepeats": 2,
        "bridgeFadeOutBeats": 0.0,
        "bridgeFadeInBeats": 0.0
      }
    }
  ],
  "stages": [
    { "code": "Coaster", "name": "Coaster", "tracks": ["course:11111111111111111111111111111111"] }
  ],
  "provenance": [
    {
      "subject": "course:11111111111111111111111111111111",
      "field": "timing",
      "level": "CHOSEN_BY_PROJECT",
      "note": "section timings are this project's choice; the game's own scheduling is not established"
    }
  ],
  "unlock": { "frozen": true, "stages": ["Coaster"] }
}
```

---

## 11. 其他成员

### 11.1 `checksums.json`

```json
{
  "formatVersion": 1,
  "members": [
    { "name": "manifest.json", "sha256": "<64 hex>", "bytes": 4321 },
    { "name": "catalog.json", "sha256": "<64 hex>", "bytes": 98765 }
  ]
}
```

覆盖除自身以外的所有成员。`name` 必须是合法成员名（见 1.2 节）且必须存在于归档中；指向缺失成员的校验条目，或没有对应校验条目的成员，均属于校验失败。

### 11.2 `catalog.json`

曲库界面读取的目录。其条目引用逻辑曲目键，且每个条目重复给出**曲包视角**下的可播性 `playable`（第 6 节）。它仅用于展示参考：清单才是权威来源，读取器绝不能允许目录条目引入清单中并未携带的曲目。

### 11.3 `music-graphs/<trackSlug>.json`

单首曲目的已编译播放配置，引用资产摘要与 `(trackKey, markerId)` cue。它自身不携带任何时序配置：所有时序均来自清单，从而确保同步点只在一个地方声明。若图中段落引用了曲包未携带的摘要，该图即无效。

### 11.4 `markers/<stageCode>.json`

单条赛道的 cue/marker 数据，沿用内置 `cues.json` 条目的结构：

```json
{
  "formatVersion": 1,
  "provenance": "L1_MARKER_ONLY",
  "provenanceNote": "Marker records only. The game's scheduling is not established.",
  "code": "Coaster",
  "name": "Coaster",
  "outroCount": 2,
  "hasBridge": true,
  "hasShortPre": true,
  "hasFast": false,
  "cues": [
    { "name": "Full_ToOutro00", "markerId": 12, "frames": 1234567, "sampleRate": 48000 }
  ]
}
```

规则：cue 按 `frames` 排序；没有位置的 cue 直接丢弃，绝不放到 0 位置；未知的 `provenance` 一律拒绝（v1 仅定义了 `L1_MARKER_ONLY`）；cue 列表只是位置清单而非调度表，界面凡展示之处都必须明确说明这一点。

### 11.5 `routes/<routeId>.json`

路线预设。赛道通过赛道代号引用，绝不通过显示名称或路径引用，原因与已保存路线相同：名称会变，ID 不会变。

### 11.6 `artwork/<sha256>.<ext>`

按内容寻址的图像。允许的扩展名：`png`、`jpg`、`webp`。摘要基于存储的字节计算。

---

## 12. 版本控制与兼容性

1. **`formatVersion` 是整份文档的版本号。** 读取器遇到自身未实现的版本时必须拒绝该曲包。不得只解析它认识的那部分，因为部分读取会拼出一个看似合理、实则把缺失事实变成猜测的曲包。这与曲库文件遇到未知版本时将其隔离放置的规则一致。
2. 声明 `formatVersion: 1` 的曲包不得依赖本文档未定义的任何键。未知键会被忽略（以便未来版本添加提示字段），但只要曲包的**行为**依赖这些键，对 v1 读取器而言即属无效，必须声明更高的版本号。
3. 新增必填键、改变字段含义或更改枚举取值，都必须升级 `formatVersion`。
4. `packVersion` 对单个 `packId` 的**内容**进行版本控制，而非对 schema 进行版本控制。即使一次重发只是修正了显示名称、字节零改动，只要作为新文件发布，依然必须提升 `packVersion`，因为增量包需要能够将其指定为基础包。

---

## 13. 读取器一致性检查清单

只有全部满足以下要求，读取器才算符合规范：

- [ ] 拒绝没有 `manifest.json`、或非合法 UTF-8/JSON 的压缩包；
- [ ] 遇到自身未实现的 `formatVersion` 时直接拒绝，不再继续读取；
- [ ] 拒绝未知的 `kind`、未知的来源等级、未知的角色、未知的同步点以及未知的曲目类型；
- [ ] 拒绝任意位置出现的格式错误资产摘要，包括 `parts` 与 `primary` 中；
- [ ] 拒绝 `playableLogicalTracks > totalLogicalTracks`（在解析阶段完成，早于信任任何其他行）；
- [ ] 对照实际内容核验声明的 `totalLogicalTracks`、`playableLogicalTracks` 与 `codecKinds`，并在不匹配时报告差异而非展示声明数值；
- [ ] 拒绝来源等级为 `MEASURED_L1` 或 `MEASURED_L2` 的时序配置；
- [ ] 拒绝逃逸归档边界的成员名（绝对路径、`..`、反斜杠、未知目录、多于一个分隔符）；
- [ ] 流式读取时逐一校验每个音频成员的名称、声明大小与 SHA-256；
- [ ] 核验提取出的资产集合与声明的资产集合完全一致；
- [ ] 运行校验并报告**全部**失败项，而非遇到第一个就停下；
- [ ] 通过暂存目录与原子重命名完成安装；失败时绝不触碰已安装的曲包；
- [ ] 写入任何内容之前，拒绝基础包不存在或不匹配的增量包；
- [ ] 分别暴露「曲包自身完整」与「曲库完全可播」，绝不为仅部分可解码的曲包渲染出看似 100 % 的百分比；
- [ ] 仅在曲包已冻结、完整且全部可播放时满足解锁条件，否则一律拒绝解锁。

---

## 14. 校验失败代码

`PackValidation` 报告一组 `Failure(code, subject, message)`。代码是稳定的；消息面向人类阅读。

| Code | Rule |
|---|---|
| `NO_TRACKS` | 曲包未携带任何逻辑曲目 |
| `ASSET_SIZE_NEGATIVE` | 某项资产声明了负的字节大小 |
| `ASSET_EMPTY` | 某个音频资产为零字节 |
| `DUPLICATE_IDENTITY` | 两个资产共用同一摘要、两条曲目共用同一键，或两条赛道共用同一代号 |
| `TRACK_KEY_INCONSISTENT` | 曲目键不等于 `kind:first32(primary)` |
| `PRIMARY_NOT_IN_PARTS` | 曲目的主资产未出现在其自身的 `parts` 中 |
| `TRACK_ASSET_MISSING` | 曲目引用了资产列表中不存在的摘要 |
| `STAGE_TRACK_MISSING` | 赛道引用了曲包中未包含的曲目键 |
| `FLOW_WITHOUT_MAIN` | flow 曲目缺少 Main 片段 |
| `NON_FLOW_WITH_MANY_PARTS` | 非 flow 曲目携带了多于一个片段 |
| `OUTRO_NUMBER_INVALID` | outro 片段缺少编号、非 outro 片段携带了编号，或两个片段共用同一编号 |
| `FLOW_TIMING_MISSING` | flow 曲目未声明过渡时序 |
| `DELTA_WITHOUT_BASE` | 增量包未指明 `basePackId`/`basePackVersion` |
| `FULL_WITH_BASE` | 完整包指明了基础包 |
| `DELTA_NOT_FORWARD` | 增量包的 `packVersion` 未大于其基础包版本 |
| `PROVENANCE_MISSING` | 曲包完全未声明任何来源记录 |
| `TIMING_WITHOUT_PROVENANCE` | 曲目声明了时序却没有对应的来源记录 |
| `TIMING_PROVENANCE_OVERCLAIMS` | 时序被标注为 `MEASURED_L1` 或 `MEASURED_L2`；时序属于调度决策，必须为 `CHOSEN_BY_PROJECT` 或 `UNVERIFIED` |
| `UNLOCK_SCOPE_EMPTY` | 解锁范围未列出任何赛道 |
| `UNLOCK_STAGE_UNKNOWN` | 解锁范围指明了曲包未描述的赛道 |
| `UNLOCK_STAGE_INCOMPLETE` | 某条解锁赛道没有曲目，或其曲目引用了曲包未携带的资产 |

用于奖杯门槛的覆盖度阻塞原因由 `PackCoverage` 单独报告：
`PACK_NOT_FULL`、`NO_UNLOCK_SCOPE`、`SCOPE_NOT_FROZEN`、`SCOPE_STAGE_UNKNOWN`、
`PACK_NOT_COMPLETE`、`UNPLAYABLE_TRACKS`。
