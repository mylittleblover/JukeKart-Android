# 官方路线与四语名称来源

`app/src/main/assets/official_routes.json` 记录 MarioWiki《Mario Kart World》修订号5544799已发布列表的8个大奖赛杯赛、12条 Knockout Tour/Rally 路线。每个 stop 保存明确 ordinal，`geometry=ordered_stops_only` 只表达官方经过顺序，不是道路折线或自定义合法连接数据库。主快照来源 URL、SHA256 与逐路线资料页 URL 一同保留。

`localized_names.json` 保存30个赛道与20条官方路线的四语名称（en / zh-Hans / zh-Hant / ja），来源于各自资料页的 Names in other languages 表；每条含 URL 和原始 wikitext 的 SHA256。源表没有多义语言值，生成时确认50条记录的四语字段齐备且路线名称与路线草稿一致。

原始抓取与核对文件：`/workspace/mkw-planning/official-route-sources/`。快照哈希用于复查抓取版本，不构成道路方向、Jukebox 候选池或音乐事件证据。

特别杯保持实际 entitlement 门禁；其他官方拉力赛经过 BigTree、SFCMarioCircuit 和 Central，不能因为这些赛道同时属于特别杯而隐藏它们。当前唯一仅出现在特别杯的赛道为 Rainbow。
