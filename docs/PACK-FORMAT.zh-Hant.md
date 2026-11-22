# MKW 原聲帶曲包（`.mkwpack`），格式版本 1

[简体中文](PACK-FORMAT.zh-Hans.md) | **繁體中文** | [English](PACK-FORMAT.md) | [日本語](PACK-FORMAT.ja.md)

本文件即為協定本身。撰寫本文件的目的，是讓第二位實作者無需閱讀本儲存庫的 Kotlin 程式碼即可編寫曲包寫入器或讀取器，也讓後續維護者在未看清規則所防範的問題之前，不會「順手簡化」某條規則。

狀態：`formatVersion` 1。讀取器與資料模型位於
`app/src/main/kotlin/com/mylittleblover/mkwplayer/pack/`。

---

## 0. 為什麼需要這個格式

《瑪利歐賽車 世界》的賽道主題曲並不是單一檔案，而是一組由遊戲在播放時切換的片段：短前奏（short prelude）、完整前奏（full prelude）、過渡橋段（bridge）、主循環（main loop）以及一個或多個尾奏（outro）。以 `BGM_Stage_Coaster` 的實測資料為例：

| part | frames | length | loop |
|---|---:|---:|---|
| Pre_Short | 534,857 | 11.143 s | 205714..534857 |
| Pre | 2,427,426 | 50.571 s | 452571..2427426 |
| Bridge | 88,320 | 1.840 s | none |
| Main | 3,373,711 | 70.286 s | 493714..3373711 |
| Outro00 | 226,286 | 4.714 s | none |

因此，一條賽道就有五到八個音訊資產。三十條賽道大約兩百個檔案，再加上點唱機目錄又會增加數百個檔案。要求使用者逐個手動挑選這些檔案不是功能，而是缺陷：他們在聽到任何聲音之前，就必須按正確組合先挑完數百個檔案。

曲包用一次匯入取代了這個過程。單一檔案即可攜帶全部音訊資產、賽道結構、音樂圖設定、標記/cue 資料、路線資料、封面插圖與校驗和。

因此，這個協定必須回答一般音樂格式不需要回答的問題：
**一個檔案如何明確宣告哪些是它確知的事實、哪些只是它做出的選擇、哪些是它無法驗證的，並且使執行時期不被允許擅自「最佳化」或改寫這些宣告？**

---

## 1. 容器

### 1.1 為什麼使用 ZIP64

`.mkwpack` 是一個 **ZIP64** 封存檔。不是 ZIP32，不是自訂串流格式，也不是 tarball。

ZIP32 使用 32 位元欄位儲存大小與偏移量，無法裝下無損原聲帶曲包：

- 30 首賽道主題曲，每首 5 個片段，平均每段 60 秒 48 kHz 16-bit 立體聲，約為
  1.7 GB（`48000 × 2 ch × 2 B = 192,000 B/s`，每分鐘約 11.5 MB）。
- 實測目錄中的點唱機曲集有 227 首曲目。按保守估計每首 3 分鐘計算，僅這一項就約 7.8 GB。
- 過場動畫、序列音樂、對戰主題曲、封面插圖與校驗索引還會進一步增加體積。

完整曲包通常會超過 10 GB，32 位元封存檔連定址都做不到。ZIP64 將大小與偏移欄位都提升到了 64 位元。ZIP64 同時移除了 65,535 條目的上限，這一點很關鍵，因為曲包會為每項資產以及每張音樂圖、標記集、路線與插圖記錄各佔一個條目。

ZIP64 還提供了匯入路徑所需的能力：中央目錄（central directory）。讀取器無需解密或解碼任何內容即可列出並校驗每個成員，還可以逐個串流讀取成員。若使用單一拼接串流，則必須完整掃完一遍才能檢查第一個資產。

### 1.2 讀取器在讀取任何內容前必須強制執行的規則

1. **封存檔根目錄必須存在名稱完全為 `manifest.json` 的檔案，且它應當（SHOULD）是第一個條目**，這樣讀取器在觸碰 10 GB 音訊前就能拒絕不可用的曲包。
2. 成員名稱受到嚴格限制。遇到符合以下任一條件的成員名稱，讀取器必須（MUST）拒絕：
   - 是絕對路徑，或包含磁碟機代號；
   - 包含 `..` 作為路徑區段；
   - 包含反斜線；
   - 不在第 2 節規定的某個目錄下；
   - 包含多於一個路徑分隔符（目錄佈局嚴格只有一層深）。

   這不是紙上談兵：否則名為 `../../files/library.json` 的 ZIP 成員就會被寫到暫存目錄之外。第 2 節的目錄檢查就是全部防線，因此絕非可選項。
3. 不使用加密。讀取器一旦遇到加密條目即拒絕該曲包。
4. 音訊條目採用 `STORE`（方法 0）儲存：已壓縮音訊無法進一步壓縮，且未壓縮儲存的條目無需解壓即可對照其宣告大小進行校驗。文字條目（`manifest.json`、`catalog.json`、圖、標記、路線）可以採用 `DEFLATE`。插圖兩者皆可。讀取器必須同時支援這兩種方式。
5. `.mkwpack` 副檔名只是宣告，不是安全邊界。讀取器透過 `manifest.json` 識別曲包，絕不依據副檔名或平台回報的 MIME 類型。

### 1.3 媒體類型

檔案媒體類型為 `application/zip`。用戶端在磁碟上的檔案名稱形式為 `<packId>-v<packVersion>.mkwpack`，但檔案名稱僅供展示參考：真正的身分識別保存在清單中。

---

## 2. 目錄佈局

```
manifest.json                   必需，位於根目錄，首個條目
checksums.json                  必需，非資產成員的完整性索引
catalog.json                    必需，曲包自身對其曲目的目錄清單
audio/<sha256>                  每項資產一個條目；無副檔名，無子目錄
music-graphs/<trackSlug>.json   每首帶有已編譯圖的曲目一個（可選）
markers/<stageCode>.json        每條賽道的 cue/marker 資料（可選）
routes/<routeId>.json           路線預設（可選）
artwork/<sha256>.<ext>          圖像，按內容定址（可選）
```

根目錄下不得出現任何其他檔案。位於這些目錄之外的成員一律拒絕，即使它看起來無害——因為「未知的額外檔案」正是曲包開始攜帶無人校驗酬載的開端。

`music-graphs/` 內的成員名是推導出來的，而非宣告的：將曲目鍵（track key）中的 `:` 替換為 `_`。例如曲目鍵 `course:1111...`（32 個十六進位字元）對應
`music-graphs/course_1111....json`。透過推導產生檔案名稱意味著沒有任何清單欄位需要攜帶路徑，清單也就不可能指向封存檔外部。

`markers/<stageCode>.json` 和 `routes/<routeId>.json` 採用相同規則：賽道代號（stage code）或路線 ID 必須匹配 `[A-Za-z0-9_-]{1,64}`，並按原樣用作檔案名稱。

### 2.1 `audio/` 按內容定址

條目名稱**就是**該資產的 SHA-256 摘要，64 個小寫十六進位字元，無副檔名，無子目錄。由此帶來的性質：

- 封存檔內不可能以兩個名稱存放同一資產的兩份副本；
- 擷取 `audio/<x>` 的讀取器只需對位元組計算雜湊並與 `<x>` 比對即可完成校驗，無需查閱任何查表；
- 資產在曲包之間遷移時保持同一身分。

媒體類型與編解碼器由清單**宣告**，而不從副檔名推斷——因為本來就沒有副檔名。

---

## 3. 穩定身分識別

### 3.1 資產 ID

資產是其精確位元組序列的 SHA-256 摘要，寫作 64 個小寫十六進位字元，並且它是**參照資產的唯一方式**。絕不用路徑、絕不用檔案名稱、絕不用資產列表中的索引。

該摘要是基於儲存的位元組計算，而非解碼後的音訊。重新編碼的檔案屬於不同的資產，協定如實反映這一點而不假裝它們相同：摘要是該格式無需信任任何人即可做出的唯一宣告。

### 3.2 邏輯曲目鍵

邏輯曲目是使用者收聽的單一曲目單元，由一個或多個資產組成。其鍵是推導出的，而非任意選取：

```
trackKey = <小寫的 kind> ":" <主資產摘要的前 32 個十六進位字元>
```

例如 `course:5d41402abc4b2a76b9719d911017c592`。其中 `kind` 為目錄類別之一（`course`、`jukebox`、`battle`、`sequence`、`cutscene`、`mission`、`demo`、`sound_effect`、`boot`、`unclassified`）。

有兩個刻意設計的結果：

- 同一主資產既用作賽道主題曲又用作點唱機條目時，它們是**兩條**曲目，因為 `kind` 是鍵的一部分。將它們合併會靜默遺失其中之一。
- 曲目鍵不能被重新命名。如果曲包宣告了一個與其自身 `kind` 和主資產摘要不匹配的鍵，該曲包即無效（`TRACK_KEY_INCONSISTENT`）。能被公告改寫的「穩定」鍵算不上身分識別。

### 3.3 播放設定參照 ID

已編譯的圖（`music-graphs/`）透過摘要參照資產，並透過 `(trackKey, markerId)` 參照 cue。它絕不參照檔案中的位置、目錄列號或快取檔案。因此，圖描述的是**內容**，而非**位置**。

### 3.4 這樣設計的收益與代價

由於身分由內容推導，以下操作**不會**使使用者儲存的任何內容失效：

- **重新發佈曲包。** 新版曲包若只是補充插圖、路線、更多尾奏或修正顯示名稱，所有未改動資產的摘要保持不變。已儲存的參照這些資產的收聽版本依然可解析，音樂圖也依然可編譯。
- **支援另一種編解碼器。** 新版程式學會解碼 codec 2 後，改變的是它能**播放**什麼，而不是資產**是什麼**。沒有任何 ID 會移動。原本無法播放的曲目會以完全相同的身分變為可播放。
- **在磁碟上移動或重新命名任何檔案。** 格式中不包含任何路徑。

唯一會改變身分的操作是**重新編碼音訊**，因為位元組變了，而摘要如實反映變化。這並不是身分系統故障，也不能被當作原地升級處理：

- 重新編碼的資產會獲得新摘要，進而得到新的曲目鍵；
- 攜帶它的曲包必須提升為新的曲包版本，且只要還有已儲存的收聽版本參照舊資產，舊資產就不得刪除；
- 應用程式必須將重編碼後的曲目展示為新增條目，而絕不能用相同 ID 進行替換。

因此安裝器的鐵律是：**永遠不要刪除仍有已儲存使用者版本參照的已安裝資產，也永遠不要在已有 ID 下重寫資產內容。** 若一次匯入試圖在相同摘要下用不同位元組替換既有資產，該曲包即屬於損壞包並必須被拒絕（校驗和比對會捕獲此事）。

---

## 4. 曲包必須記錄什麼，以及執行時期不得推導什麼

應用程式在開發匯入路徑中確實有從檔案名稱推導下列事實的程式碼（`StageFlowDiscovery.prefixOf`、`StageFlowReader.roleOf`）。那些程式碼的存在是為了讓手動挑選的檔案也能使用。**它絕不能針對曲包內容執行。** 從檔案名稱推導的角色只是猜測，一旦清單被接受，曲包裡的猜測就會與明確陳述的事實真假難辨。

因此，本儲存庫中的曲包讀取器完全沒有任何 `roleOf` 風格的輔助函式：下面每一項事實都直接從欄位讀取，缺失欄位即判定失敗，絕不回退猜測。

| 事實 | 曲包宣告的位置 | 靠猜測會變成什麼樣 |
|---|---|---|
| 段落角色（Pre_Short、Pre、Bridge、Main、Outro） | `tracks[].parts[].role` | 把 `_Fast` 當成 Main，或者因為子字串匹配把 `_Pre_Short` 當成 Pre |
| 哪個尾奏對應哪一個 | `tracks[].parts[].outro` | 只保留先讀到的那個尾奏檔案，導致另一個結局消失 |
| cue 位置及其證據等級 | `markers/<stage>.json`，附帶 `provenance` | 把標記偏移量當成遊戲排程切換的時機來展示 |
| 循環視窗 | `assets[].loopStart` / `loopEnd` | 循環整個檔案，導致前奏一直循環播放 |
| 過渡同步點 | `tracks[].timing` | 隨便選個 `Immediate`，然後把結果稱為「遊戲的過渡方式」 |
| 主循環重複次數 | `tracks[].timing.mainRepeats` | 預設為 1 或 4；兩者都會改變單次收聽的時長 |
| 淡入淡出長度 | `tracks[].timing.bridgeFade{In,Out}Beats` | 憑空捏造一個從未量測的可聽交叉淡變 |
| 賽道到曲目的映射 | `stages[].tracks` | 按顯示名稱匹配，而顯示名稱會隨在地化翻譯變化 |

如果某項事實缺失，讀取器**拒絕受影響的曲目**，而不會替它補全。對於上表中的任何一列，本格式都沒有預設值，這也是為什麼省略欄位會觸發校驗失敗而不是被當作 0。

---

## 5. `provenance`（來源/證據等級）是必填欄位

每一條宣告的 `timing` 都必須帶有對應的 `provenance` 記錄。宣告了 `timing` 卻沒有對應來源記錄的曲包無效（`TIMING_WITHOUT_PROVENANCE`），而 `provenance` 列表為空的曲包同樣無效（`PROVENANCE_MISSING`）。

等級劃分。以下四個等級構成了 v1 的完整詞表；新增第五個等級屬於格式變更，而非擴充。

| 等級 | 含義 | 範例 |
|---|---|---|
| `MEASURED_L1` | 資源結構事實：容器中的欄位、指定位置處的標記記錄。 | 研究匯出結果中的 174 個 `Full_ToOutroNN` 標記位置 |
| `MEASURED_L2` | 透過直接量測音訊本身確立的事實。 | 解碼波形與參考算繪逐採樣一致；從 BWAV 標頭讀取的循環座標 |
| `CHOSEN_BY_PROJECT` | 由於沒有證據確立其他方案，本專案做出的選擇。 | `preludeExit = EXIT_CUE`、`mainRepeats = 2` |
| `UNVERIFIED` | 已記錄，但沒有任何證據證實。保留該值以便介面展示並如實說明。 | 含義尚未確定的尾奏名稱 |

規則：

1. 未知等級一律拒絕，絕不賦預設值。聲稱了目前版本不認識的等級的曲包必須明確失敗，而不能降級按最弱等級讀取：否則 `L3_VERIFIED` 這樣的宣告就會被洗白成「未驗證」，再被當作資料展示出來。
2. 每條記錄都指明其主體（subject）與所涵蓋的欄位，因此來源宣告無法脫離其描述的值而懸空存在。對於 `timing`，主體是曲目鍵，欄位是 `timing`。
3. `MEASURED_L1` 級別的標記位置並不能說明遊戲**何時使用**某次過渡。曲包絕不能把 cue 列表標註為排程表。v1 中標記檔案自身的 `provenance` 欄位固定為 `L1_MARKER_ONLY`，正是出於這個原因。
4. **段落時序（section timing）永遠不得標註為已量測。** `preludeExit`、`bridgeEntry`、`outroEntry`、`outroSwitch`、`mainRepeats` 以及橋段淡入淡出長度都是排程決策。沒有任何容器欄位或音訊量測能確立遊戲何時執行切換，因此等級為 `MEASURED_L1` 或 `MEASURED_L2` 的時序記錄屬於無效（`TIMING_PROVENANCE_OVERCLAIMS`）。這是本格式唯一不僅附加免責聲明、而且直接拒絕宣告的地方：標記偏移確實是檔案的真實 L1 事實，但把它的權威性套用到時序排程上，正是本專案曾經不得不推翻重來的那一次替代錯誤。
5. 來源隨資料同行。介面凡是展示該值的地方就必須展示其來源；執行時期絕不可丟棄它，因為不帶等級展示的時序會被讀者當成既定事實。

---

## 6. 覆蓋度與編解碼器相容性必須宣告

清單在 `coverage` 中宣告：

- `totalLogicalTracks`：曲包共包含多少條邏輯曲目；
- `playableLogicalTracks`：曲包自身聲稱其中有多少條可播放；
- `codecKinds`：曲包中任意資產使用到的所有編解碼器類型。

`playableLogicalTracks` 是關於曲包本身的陳述，而不是關於讀取器的陳述：它的意思是「曲包已列出其全部資產且大小均非零的曲目數」。讀取器會根據資產列表重新計算該值，若宣告數量無法由曲包自身內容支撐則拒絕該曲包（`PackCoverage.Coverage.declaredCoverageMatches`）。曲包不能只列出 412 條卻聲稱有 650 條可播曲目。宣告的 `codecKinds` 列表也以同樣方式校驗：它必須恰好等於資產實際用到的類型集合，否則漏報一個類型就會把不可解碼的資產藏在看似誠實的數字背後。

目前建置版本**實際**能播放多少是另一項獨立計算，依據讀取器的解碼能力得出：

- `PackCoverage` 報告目前建置版本能解碼曲包中的多少條曲目；
- 列出曲包中存在但目前建置版本無法解碼的每一種編解碼器及其影響的曲目數；
- 只要存在任何不可播放的曲目，`isComplete` 即為 `false`。

文案規則非常嚴格：**當曲包包含不可解碼資產時，應用程式絕不能顯示「100 %」。** 覆蓋度類型刻意完全不暴露百分比欄位，從源頭杜絕四捨五入進位。它只提供計數和兩句說明。

這裡存在兩個不同的完整性事實，把它們混為一談正是本節要防止的錯誤：

| 屬性 | 回答的問題 | 何時為 false |
|---|---|---|
| `isPackComplete` | 檔案本身在內部是否一致且完整？ | 宣告數量與內容不符、包內缺少資產、某條賽道參照了不存在的曲目 |
| `isComplete` | 目前建置版本能否播放檔案包含的全部內容？ | 因任何原因存在任意一條不可播放的邏輯曲目 |

一個曲包完全可以「自身完整，但並非全部可播」，而且介面必須把兩件事都說出來：「曲包完整；目前版本可播放其中 650 首中的 412 首」是一句真話，而「100 %」不是。

---

## 7. `FULL` 包與 `DELTA` 包

```json
"kind": "FULL"    // 或 "DELTA"
```

`FULL` 包自身即為完整曲包。`DELTA` 包用於修改一個已安裝的基礎包（base pack），且必須指明它：

```json
"kind": "DELTA",
"basePackId": "org.example.mkw.base",
"basePackVersion": 4
```

規則：

1. `DELTA` **必須**指明 `basePackId` 與 `basePackVersion`（`DELTA_WITHOUT_BASE`）。僅有版本號是不夠的：兩個不同曲包的版本 4 並不是同一個基礎包。沒有基礎包 ID 的增量包會被套用到當時恰好已安裝的任意曲包上，從而拼出兩個產物的混合體。
2. `FULL` 包**不得**指明基礎包（`FULL_WITH_BASE`）。指定基礎包會使曲包變成增量包，而將其作為完整包匯入則會靜默丟棄基礎包原先持有的全部內容。
3. 增量包必須向前遞增：`packVersion > basePackVersion`（`DELTA_NOT_FORWARD`）。
4. 若增量包所依賴的基礎包未安裝、已安裝版本不一致或 `packId` 不一致，將在**寫入任何內容之前被拒絕**。絕不半途套用：暫存目錄會被丟棄，已安裝曲包保持原封不動。
5. 增量包只攜帶它所變更的成員。套用增量包的結果是以增量內容覆蓋基礎內容（按摘要按鍵疊加）。v1 中增量包不得刪除內容：若需要移除某些內容，應重新發佈一個新的 `FULL` 包。
6. 增量包不得變更已凍結基礎包的解鎖範圍。如果基礎包已凍結，增量包的 `unlock` 區塊必須與之完全一致；否則拒絕匯入，因為能改寫合格賽道列表的增量包可以把使用者已經聽過的賽道塞進去，從而把獎盃解鎖條件縮短到形同虛設。

---

## 8. 解鎖契約

特別盃的合格賽道列表來自**曲包自身經過校驗且已凍結的邏輯曲目列表**，絕不來自使用者恰好匯入了什麼檔案。

```json
"unlock": {
  "frozen": true,
  "stages": ["Coaster", "PeachBeach", "..."]
}
```

- `stages` 是獎盃所需的已凍結賽道代號列表。它必須非空，且每一項都必須是曲包所描述的賽道，同時該賽道包含的曲目在曲包中確實存在（`UNLOCK_SCOPE_EMPTY`、`UNLOCK_STAGE_UNKNOWN`、`UNLOCK_STAGE_INCOMPLETE`）。
- `frozen` 宣告該列表在此曲包版本下不會再變更。只有已凍結的列表才能定義獎盃資格。
- 若曲包不是 `FULL`、其範圍未凍結、內容不完整，或範圍中包含了目前建置版本無法解碼的曲目，則產生的**合格賽道數為零**。上述任一情形下 `PackCoverage.cupEligibleStages` 都回傳空集合，並由 `cupBlocker` 指出具體原因。

為什麼資格絕不能來自使用者的檔案：如果那樣做，資格就成了「匯入了什麼」的屬性。只匯入一條賽道、聽滿三十秒，單元素集合上的 `allCompleted` 就會為真，特別盃隨之直接解鎖。`CupEligibility` 中的 `set.isNotEmpty()` 堵住了空集合漏洞，但堵不住單賽道漏洞：單元素集合並不空。唯一的防線，就是讓這個集合來自使用者無法編輯、匯入流程也無法縮減的宣告文件。

「凍結」就是字面意義的凍結：該列表屬於特定曲包版本的屬性。改動該列表的重新發佈必須作為新版本，且只有當舊列表從未被滿足時，應用程式才可採納新列表。增量包則完全不得改動它（第 7 節規則 6）。

---

## 9. 匯入：原子性與校驗

一次匯入絕不能損壞它所替換的對象。

### 9.1 串流讀取，絕不整體緩衝

曲包透過封存檔的中央目錄按條目逐一讀取。沒有任何步驟會把整個曲包或單一資產完整讀入記憶體：音訊資產合計可達數百 MB，而應用程式執行在手機上。每個資產都會在計算雜湊的同時，透過固定大小的緩衝區（64 KiB）複製到暫存路徑。

### 9.2 逐資產校驗

對於每一個音訊成員，匯入器會：

1. 檢查條目名稱是否為 `audio/<digest>`，且摘要為 64 個小寫十六進位字元；
2. 在讀取資料**之前**檢查宣告的 `byteSize` 是否與條目的未壓縮大小一致（不一致則不寫入任何資料直接中止）；
3. 串流傳輸位元組並同步計算 SHA-256；
4. 將計算出的摘要與成員名以及清單中的 `assets[].digest` 進行比對；
5. 檢查擷取出的資產集合是否與清單的資產列表完全一致：既無缺失條目，也無多餘條目。

任一成員在以上任意一步失敗，都會中止整個匯入。只通過部分校驗的曲包不會被安裝，而是直接刪除。

`checksums.json` 用 `sha256` 和 `bytes` 涵蓋了每一個非音訊成員（`manifest.json`、`catalog.json`、圖、標記、路線、插圖）。它無法涵蓋自身；它的完整性建立在封存檔自身完好之上，且協定不做超出損壞偵測之外的保證。**v1 中沒有簽章機制**，因此校驗和偵測的是損壞，而非偽造。

### 9.3 先暫存，再替換

```
staging/  <packId>/<packVersion>.tmp/     <- 所有內容先寫到這裡
    audio/<sha256> ...
    manifest.json ...
```

1. 擷取並校驗到 `<library>/.staging/<random>/`。目錄名隨機生成，以防先前失敗的嘗試被恢復成半合併狀態。
2. **先**解析並校驗清單，再擷取音訊。結構無效的曲包必須零代價失敗。
3. 對於增量包，在寫入任何成員之前先解析並校驗基礎包。若基礎包與 `(basePackId, basePackVersion)` 不匹配，則未寫入任何內容即拒絕並中止。
4. 全部成功後，對暫存目錄執行 `fsync`，隨後**原子重新命名**為 `<library>/packs/<packId>/<packVersion>/`。同一檔案系統內的重新命名是原子的：即使發生當機，磁碟上要麼是舊目錄樹，要麼是新目錄樹，絕不會出現混合體。
5. 透過暫存檔與 `Files.move(..., REPLACE_EXISTING)` 更新已安裝曲包指標（`installed.json`），與 `LibraryStore` 採用相同模式。指標才是提交點：在指標移動之前，新目錄樹處於未被參照狀態，隨時可刪除。
6. 發生任何失敗時，刪除暫存目錄並保持原先已安裝曲包原封不動（包括指標）。向使用者回報的錯誤訊息應指明失敗的資產或成員。仍被已儲存收聽版本參照的舊曲包必須保留，不得替換。
7. 絕不直接向已安裝目錄樹解壓覆蓋，也絕不在匯入過程中刪除既有資產。垃圾回收是一項獨立的顯式操作，必須先查閱已儲存的收聽版本。

---

## 10. `manifest.json` 參考

所有鍵名區分大小寫。未知鍵將被忽略；已知鍵的未知**值**將被拒絕（第 12 節）。

### 10.1 頂層欄位

| Key | Type | Required | Rule |
|---|---|---|---|
| `formatVersion` | integer | 是 | 必須為 `1`；其他任何值直接拒絕，不再繼續讀取 |
| `packId` | string | 是 | `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`；它會成為目錄名，因此不得包含分隔符或 `..` |
| `packVersion` | integer | 是 | >= 1，隨每個 `packId` 遞增 |
| `name` | string 或省略 | 否 | 人類可讀名稱；省略或空白時回退為 `packId` |
| `kind` | `"FULL"` 或 `"DELTA"` | 是 | 其他任何值一律拒絕 |
| `basePackId` | string 或 null | `DELTA` 時 | `DELTA` 時必填，`FULL` 時禁止填寫 |
| `basePackVersion` | integer 或 null | `DELTA` 時 | `DELTA` 時必填，`FULL` 時禁止填寫，>= 0 |
| `coverage` | object | 是 | 見 10.2 節 |
| `assets` | array | 是 | 見 10.3 節 |
| `tracks` | array | 是 | 見 10.4 節 |
| `stages` | array | 是（可為空） | 見 10.5 節 |
| `provenance` | array | 是 | 見 10.6 節 |
| `unlock` | object | 是 | 見 10.7 節 |

### 10.2 `coverage`

| Key | Type | Rule |
|---|---|---|
| `totalLogicalTracks` | integer | >= 0；對於完整包必須等於 `tracks` 中的條目數 |
| `playableLogicalTracks` | integer | `0 <= n <= totalLogicalTracks`；大於總數的值在解析階段即被拒絕 |
| `codecKinds` | integer 陣列 | 所有資產使用到的每一種編解碼器類型；必須存在（僅當空曲包時才可能為空，而空曲包本身無效） |

### 10.3 `assets[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `digest` | string | 是 | 64 個小寫十六進位字元；格式錯誤的值一律拒絕 |
| `byteSize` | integer | 是 | 音訊必須 >= 0 且 > 0；負數與零均為校驗失敗 |
| `mediaType` | string | 是 | 非空白；例如 `audio/x-bwav` |
| `codecKind` | integer | 是 | >= 0；對應 BWAV 聲道編解碼器類型，`1` = DSP-ADPCM |
| `sampleRate` | integer | 是 | > 0 |
| `channels` | integer | 是 | > 0 |
| `frames` | integer | 是 | > 0 |
| `loopStart` | integer 或 null | 否 | 與 `loopEnd` 成對出現，`0 <= start < end <= frames` |
| `loopEnd` | integer 或 null | 否 | 省略表示該資產不循環；絕不為 0 |

無循環時取值為 `null`，絕不是 `0`。第 0 幀是一個真實位置；`0..0` 的循環意味著聲稱該資產在零長度區間上循環。

### 10.4 `tracks[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `key` | string | 是 | 必須等於 3.2 節的推導結果，否則報錯 `TRACK_KEY_INCONSISTENT` |
| `kind` | string | 是 | 目錄類別之一，按列舉名原樣書寫（例如 `COURSE`、`SOUND_EFFECT`） |
| `title` | string 或省略 | 否 | 僅用於顯示；省略或空白時回退為 `key`；絕不用於匹配 |
| `primary` | string | 是 | 資產摘要；必須出現在該曲目自身的 `parts` 中 |
| `isFlow` | boolean | 是 | 當曲目的各片段在同步點切換時為 `true` |
| `parts` | array | 是 | 至少包含主資產；每個資產對應一條，有序排列 |
| `timing` | object 或 null | flow 時 | `isFlow: true` 時必填，否則忽略 |

`parts[]`：

| Key | Type | Rule |
|---|---|---|
| `asset` | string | 資產摘要；必須存在於 `assets` 中 |
| `role` | string | `PRE_SHORT`、`PRE`、`BRIDGE`、`MAIN`、`OUTRO` |
| `outro` | integer 或 null | `OUTRO` 時必填（`>= 0`），其他所有角色禁止填寫；同一曲目內唯一 |

role 與 sync-point 詞表均為凍結字串，刻意不與 Kotlin 識別符名稱綁定：重新命名 Kotlin 列舉絕不應靜默改變協定。

`timing`：

| Key | Type | Required | Meaning |
|---|---|---|---|
| `preludeExit` | sync point | 是 | 允許離開前奏的位置 |
| `bridgeEntry` | sync point | 是 | 允許進入橋段 / 交接給 Main 的位置 |
| `outroEntry` | sync point | 是 | 允許請求切換至尾奏的位置 |
| `outroSwitch` | sync point | 是 | 允許替換待切換尾奏的位置 |
| `mainRepeats` | integer | 是 | >= 1；在提供尾奏前 Main 需播放的遍數 |
| `bridgeFadeOutBeats` | number | 否，預設 0 | >= 0 |
| `bridgeFadeInBeats` | number | 否，預設 0 | >= 0 |

同步點取值：`IMMEDIATE`、`NEXT_BEAT`、`NEXT_BAR`、`NEXT_GRID`、`EXIT_CUE`、`NEXT_CUE`、`NEXT_CUSTOM_CUE`。

缺失或未知的同步點將被拒絕。如果把預設值設為 `IMMEDIATE`，就會抹平該欄位原本存在的意義。

### 10.5 `stages[]`

| Key | Type | Rule |
|---|---|---|
| `code` | string | 非空白，`[A-Za-z0-9_-]{1,64}`，唯一 |
| `name` | string 或省略 | 僅用於顯示；省略或空白時回退為 `code` |
| `tracks` | string 陣列 | 邏輯曲目鍵；每一項都必須存在於 `tracks` 中 |

允許一條賽道命名多條曲目是有意為之：一條賽道可以分別攜帶它的賽道主題曲、對戰編曲以及變速版本作為獨立的邏輯曲目。

### 10.6 `provenance[]`

| Key | Type | Rule |
|---|---|---|
| `subject` | string | 曲目鍵、賽道代號或 `pack` |
| `field` | string | 該記錄涵蓋的欄位，例如 `timing` |
| `level` | string | `MEASURED_L1`、`MEASURED_L2`、`CHOSEN_BY_PROJECT`、`UNVERIFIED`；未知值一律拒絕 |
| `note` | string 或 null | 可選的人類可讀說明，解釋該值來源 |

### 10.7 `unlock`

| Key | Type | Rule |
|---|---|---|
| `frozen` | boolean | 僅當為 `true` 時才能定義獎盃資格 |
| `stages` | string 陣列 | 非空；每一項都必須是 `stages` 中存在的賽道代號 |

### 10.8 範例

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

## 11. 其他成員

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

涵蓋除自身以外的所有成員。`name` 必須是合法成員名（見 1.2 節）且必須存在於封存檔中；指向缺失成員的校驗條目，或沒有對應校驗條目的成員，均屬於校驗失敗。

### 11.2 `catalog.json`

曲庫介面讀取的目錄。其條目參照邏輯曲目鍵，且每個條目重複給出**曲包視角**下的可播性 `playable`（第 6 節）。它僅用於展示參考：清單才是權威來源，讀取器絕不能允許目錄條目引入清單中並未攜帶的曲目。

### 11.3 `music-graphs/<trackSlug>.json`

單首曲目的已編譯播放設定，參照資產摘要與 `(trackKey, markerId)` cue。它自身不攜帶任何時序設定：所有時序均來自清單，從而確保同步點只在一個地方宣告。若圖中段落參照了曲包未攜帶的摘要，該圖即無效。

### 11.4 `markers/<stageCode>.json`

單條賽道的 cue/marker 資料，沿用內建 `cues.json` 條目的結構：

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

規則：cue 按 `frames` 排序；沒有位置的 cue 直接丟棄，絕不放到 0 位置；未知的 `provenance` 一律拒絕（v1 僅定義了 `L1_MARKER_ONLY`）；cue 列表只是位置清單而非排程表，介面凡展示之處都必須明確說明這一點。

### 11.5 `routes/<routeId>.json`

路線預設。賽道透過賽道代號參照，絕不透過顯示名稱或路徑參照，原因與已儲存路線相同：名稱會變，ID 不會變。

### 11.6 `artwork/<sha256>.<ext>`

按內容定址的圖像。允許的副檔名：`png`、`jpg`、`webp`。摘要基於儲存的位元組計算。

---

## 12. 版本控制與相容性

1. **`formatVersion` 是整份文件的版本號。** 讀取器遇到自身未實作的版本時必須拒絕該曲包。不得只解析它認識的那部分，因為部分讀取會拼出一個看似合理、實則把缺失事實變成猜測的曲包。這與曲庫檔案遇到未知版本時將其隔離放置的規則一致。
2. 宣告 `formatVersion: 1` 的曲包不得依賴本文件未定義的任何鍵。未知鍵會被忽略（以便未來版本新增提示欄位），但只要曲包的**行為**依賴這些鍵，對 v1 讀取器而言即屬無效，必須宣告更高的版本號。
3. 新增必填鍵、改變欄位含義或變更列舉取值，都必須升級 `formatVersion`。
4. `packVersion` 對單一 `packId` 的**內容**進行版本控制，而非對 schema 進行版本控制。即使一次重發只是修正了顯示名稱、位元組零改動，只要作為新檔案發佈，依然必須提升 `packVersion`，因為增量包需要能夠將其指定為基礎包。

---

## 13. 讀取器一致性檢查清單

只有全部滿足以下要求，讀取器才算符合規範：

- [ ] 拒絕沒有 `manifest.json`、或非合法 UTF-8/JSON 的壓縮包；
- [ ] 遇到自身未實作的 `formatVersion` 時直接拒絕，不再繼續讀取；
- [ ] 拒絕未知的 `kind`、未知的來源等級、未知的角色、未知的同步點以及未知的曲目類型；
- [ ] 拒絕任意位置出現的格式錯誤資產摘要，包括 `parts` 與 `primary` 中；
- [ ] 拒絕 `playableLogicalTracks > totalLogicalTracks`（在解析階段完成，早於信任任何其他列）；
- [ ] 對照實際內容核驗宣告的 `totalLogicalTracks`、`playableLogicalTracks` 與 `codecKinds`，並在不符合時報告差異而非展示宣告數值；
- [ ] 拒絕來源等級為 `MEASURED_L1` 或 `MEASURED_L2` 的時序設定；
- [ ] 拒絕逃逸封存檔邊界的成員名（絕對路徑、`..`、反斜線、未知目錄、多於一個分隔符）；
- [ ] 串流讀取時逐一校驗每個音訊成員的名稱、宣告大小與 SHA-256；
- [ ] 核驗擷取出的資產集合與宣告的資產集合完全一致；
- [ ] 執行校驗並報告**全部**失敗項，而非遇到第一個就停下；
- [ ] 透過暫存目錄與原子重新命名完成安裝；失敗時絕不觸碰已安裝的曲包；
- [ ] 寫入任何內容之前，拒絕基礎包不存在或不匹配的增量包；
- [ ] 分別暴露「曲包自身完整」與「曲庫完全可播」，絕不為僅部分可解碼的曲包算繪出看似 100 % 的百分比；
- [ ] 僅在曲包已凍結、完整且全部可播放時滿足解鎖條件，否則一律拒絕解鎖。

---

## 14. 校驗失敗代碼

`PackValidation` 報告一組 `Failure(code, subject, message)`。代碼是穩定的；訊息面向人類閱讀。

| Code | Rule |
|---|---|
| `NO_TRACKS` | 曲包未攜帶任何邏輯曲目 |
| `ASSET_SIZE_NEGATIVE` | 某項資產宣告了負的位元組大小 |
| `ASSET_EMPTY` | 某個音訊資產為零位元組 |
| `DUPLICATE_IDENTITY` | 兩個資產共用同一摘要、兩條曲目共用同一鍵，或兩條賽道共用同一代號 |
| `TRACK_KEY_INCONSISTENT` | 曲目鍵不等於 `kind:first32(primary)` |
| `PRIMARY_NOT_IN_PARTS` | 曲目的主資產未出現在其自身的 `parts` 中 |
| `TRACK_ASSET_MISSING` | 曲目參照了資產列表中不存在的摘要 |
| `STAGE_TRACK_MISSING` | 賽道參照了曲包中未包含的曲目鍵 |
| `FLOW_WITHOUT_MAIN` | flow 曲目缺少 Main 片段 |
| `NON_FLOW_WITH_MANY_PARTS` | 非 flow 曲目攜帶了多於一個片段 |
| `OUTRO_NUMBER_INVALID` | outro 片段缺少編號、非 outro 片段攜帶了編號，或兩個片段共用同一編號 |
| `FLOW_TIMING_MISSING` | flow 曲目未宣告過渡時序 |
| `DELTA_WITHOUT_BASE` | 增量包未指明 `basePackId`/`basePackVersion` |
| `FULL_WITH_BASE` | 完整包指明了基礎包 |
| `DELTA_NOT_FORWARD` | 增量包的 `packVersion` 未大於其基礎包版本 |
| `PROVENANCE_MISSING` | 曲包完全未宣告任何來源記錄 |
| `TIMING_WITHOUT_PROVENANCE` | 曲目宣告了時序卻沒有對應的來源記錄 |
| `TIMING_PROVENANCE_OVERCLAIMS` | 時序被標註為 `MEASURED_L1` 或 `MEASURED_L2`；時序屬於排程決策，必須為 `CHOSEN_BY_PROJECT` 或 `UNVERIFIED` |
| `UNLOCK_SCOPE_EMPTY` | 解鎖範圍未列出任何賽道 |
| `UNLOCK_STAGE_UNKNOWN` | 解鎖範圍指明了曲包未描述的賽道 |
| `UNLOCK_STAGE_INCOMPLETE` | 某條解鎖賽道沒有曲目，或其曲目參照了曲包未攜帶的資產 |

用於獎盃門檻的覆蓋度阻塞原因由 `PackCoverage` 單獨報告：
`PACK_NOT_FULL`、`NO_UNLOCK_SCOPE`、`SCOPE_NOT_FROZEN`、`SCOPE_STAGE_UNKNOWN`、
`PACK_NOT_COMPLETE`、`UNPLAYABLE_TRACKS`。
