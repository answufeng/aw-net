# aw-net 審查與優化計畫（v2 合併版）

**版本**：v2（合併 [aw-net-review-and-optimization-plan1.md](./aw-net-review-and-optimization-plan1.md) 與先前 [aw-net-review-and-optimization-plan.md](./aw-net-review-and-optimization-plan.md) 之 R1–R10 預審項）  
**範圍**：`aw-net` 函式庫與 `demo`；不含單元測試、不含 Compose。  
**技術棧**（以 [gradle/libs.versions.toml](gradle/libs.versions.toml) 為準）：Kotlin 2.0.21、Hilt 2.56.2、OkHttp 4.12.0、Retrofit 2.12.0、Coroutines 1.10.2、Gson、AGP 8.2.2。

---

## 一、文件關係

| 檔案 | 用途 |
|------|------|
| **本檔（v2）** | 唯一合併版：原計畫分級 + 程式碼核對結論 + 整合路線圖 |
| [aw-net-review-and-optimization-plan1.md](./aw-net-review-and-optimization-plan1.md) | 你的原始審查清單（保留歷史） |
| [AW_NET_OPTIMIZATION_ROADMAP.md](./AW_NET_OPTIMIZATION_ROADMAP.md) | 先前僅基於 R1–R10 的對照表；**以本檔 v2 為準**，路線圖可逐步與本檔同步 |

---

## 二、合併索引表（原編號 → 核對結論 → 對應 R）

**狀態說明**：**仍存在**｜**部分仍存在**｜**已緩解（文件/工具）**｜**設計如此**｜**敘述需修正**

| ID | 類別 | 原計畫摘要 | 核對結論 | 對應 R |
|----|------|------------|----------|--------|
| **S1** | 嚴重 | ThreadLocal 傳 extraHeaders 有協程/執行緒風險 | **仍存在，且應上調嚴重度**：`NetworkExecutor.withExtraHeaders` 在當前協程執行緒 `set`；`RequestExecutor.executeBusinessCall` 內 `withContext(dispatcher)` 預設為 `Dispatchers.IO`，Retrofit/OkHttp 攔截器在 **IO 執行緒** 讀取 `ThreadLocal` → 自 **主執行緒** 呼叫 `executeRequest` 且帶 `extraHeaders` 時，**極可能讀不到**（非僅「同執行緒交錯」）。`finally` 的 `remove` 亦可能清在錯的執行緒。 | **R4** |
| **S2** | 嚴重 | `ExceptionHandle.customMappers` 非執行緒安全 | **仍存在**：`mutableListOf` + 無鎖 `register` / 遍歷。 | **R5** |
| **S3** | 嚴重 | `NetEventDispatcher.delegate` 雙路徑與 `track` 一致性 | **部分仍存在**：KDoc / `NetworkModule` 已警告勿與手動賦值並用；`track()` 直接讀 `delegate`，`trackAsync()` 先快照，極端併發下語意略有差異（低機率）。 | **R7** |
| **S4** | 嚴重 | `WebSocketClientImpl` scope 重建非原子 | **理論仍存在、實務風險較低**：`ensureScopeActive()` 對 `supervisorJob`/`scope` 分兩次賦值；若多執行緒同時 `reconnect`/`connect` 可能不一致，一般 UI 單執行緒呼叫較少觸發。 | — |
| **S5** | 嚴重 | `PersistentCookieJar` init 同步 `loadFromDisk` | **仍存在**：`init { loadFromDisk() }`（[PersistentCookieJar.kt](aw-net/src/main/java/com/answufeng/net/http/util/PersistentCookieJar.kt)）；若於主執行緒建立單例且檔案大，有 ANR/I/O 阻塞風險。 | — |
| **M1** | 中 | `NetworkConfig` 建構時 vs 執行時欄位混在一起 | **仍存在（模型層）**：與 KDoc 描述一致，編譯期無法區分。 | **R3** |
| **M2** | 中 | `NetworkModule` 與 `AwNet` 建 Client 重複 | **仍存在**：邏輯平行維護。 | — |
| **M3** | 中 | `DynamicRetryInterceptor` 使用 `Thread.sleep`；與協程重試缺運行時協調 | **仍存在**：KDoc 已說明阻塞；僅 Debug `Log.w` 雙層重試（[RequestExecutor](aw-net/src/main/java/com/answufeng/net/http/util/RequestExecutor.kt)）；無自動關閉其中一層。README 已補「重试只开一层」。 | **R1** |
| **M4** | 中 | `TokenRefreshCoordinator` 阻塞等待為 busy-wait | **敘述需修正**：`waitForBlockingRefresh()` 使用 `kotlinx.coroutines.delay(50)`（[TokenRefreshCoordinator.kt](aw-net/src/main/java/com/answufeng/net/http/auth/TokenRefreshCoordinator.kt)），**非** `Thread.sleep` 的 CPU 忙等；仍屬輪詢式等待，可改為事件驅動（如 `CompletableDeferred`）以降低喚醒次數。 | — |
| **M5** | 中 | `GlobalResponseTypeAdapterFactory` 多次 `mappingProvider()` | **仍存在（邊界/效能）**：與 **R6** 同。 | **R6** |
| **M6** | 中 | WebSocket `pingInterval` 被覆寫為 0 | **仍存在（設計）**：`WebSocketClientImpl` 的 `wsClient` 強制 `pingInterval(0)`；`WebSocketModule` 預設 client 的 `pingInterval(30s)` 在「使用該預設實例」時仍會被 child builder 覆寫。README 已簡短說明與 HTTP 共用 Client 之差異。 | **R9** |
| **M7** | 中 | `MockInterceptor` 每次編譯 Regex | **仍存在**：`findMock` 內對 `*` 路徑與 `regexMocks` 每次 `Regex(...)`（[MockInterceptor.kt](aw-net/src/main/java/com/answufeng/net/http/util/MockInterceptor.kt)）。 | — |
| **M8** | 中 | `Builder` 與 `copy`/`toBuilder` 並存 | **仍存在**。 | — |
| **L1** | 輕 | HTTP `NetLogger` 與 WS `WebSocketLogger` 介面不一致 | **仍存在**（風格議題）。 | — |
| **L2** | 輕 | `java.util.Optional` 擴充 | **設計如此**（Hilt `Optional` 綁定）；建議維持文件說明即可。 | — |
| **L3** | 輕 | `NetCode.Business` 與 HTTP 同號混淆 | **仍存在**（API 語意）；若改數值為破壞性變更。 | — |
| **L4** | 輕 | `PrettyNetLogger` 大 JSON 脫敏路徑 | **仍存在**（可強化）。 | — |
| **L5** | 輕 | successCode 轉換重複 | **仍存在**（可重構）。 | — |
| **L6** | 輕 | `consumer-rules.pro` 過度 `-keep` | **仍存在**（體積與混淆權衡）。 | **R8**（規則已有；精簡屬優化） |
| **L7** | 輕 | CI 是否跑 ktlint | **已緩解**：[.github/workflows/ci.yml](.github/workflows/ci.yml) 已執行 `./gradlew :aw-net:ktlintCheck :demo:ktlintCheck`。 | — |
| **F1–F8** | 增強 | 見原 plan1「功能增強」 | **屬 backlog**；程式碼核對未實作，非「缺陷是否存在」命題。 | — |
| **R10** | 預審 | Demo 與文件一致 | **已緩解**：`AdvancedConfigActivity` 逾時單位已改為秒；README 補重試與 WebSocket 說明。 | **R10** |

---

## 三、嚴重項（S1–S5）— 核對摘要與建議

### S1 / R4 — 請求級 `extraHeaders`

- **程式行為**：[NetworkExecutor.withExtraHeaders](aw-net/src/main/java/com/answufeng/net/http/util/NetworkExecutor.kt) + [RequestExtraHeadersInterceptor](aw-net/src/main/java/com/answufeng/net/http/interceptor/RequestExtraHeadersInterceptor.kt) 使用 `ThreadLocal`；業務請求在 [RequestExecutor.executeBusinessCall](aw-net/src/main/java/com/answufeng/net/http/util/RequestExecutor.kt) 的 `withContext(dispatcher)` 中執行。
- **建議（與原 plan1 一致）**：改為與 [SuccessCodeInterceptor](aw-net/src/main/java/com/answufeng/net/http/interceptor/SuccessCodeInterceptor.kt) 相同思路，以 `Request.tag(...)`（或等價、與執行緒無關）傳遞請求級 Header；**屬行為修正，需 semver 與遷移說明**。
- **優先級**：**P0**。

### S2 / R5 — `ExceptionHandle`

- **建議**：`CopyOnWriteArrayList`、同步區塊，或僅允許 Application `onCreate` 註冊並文件化。
- **優先級**：**P1**。

### S3 / R7 — `NetEventDispatcher`

- **建議**：維持「單一賦值策略」文件；可選：`track()` 亦使用區域快照 `val d = delegate` 再呼叫，與 `trackAsync` 一致。
- **優先級**：**P2**。

### S4 — WebSocket scope

- **建議**：`synchronized` 或 `AtomicReference<CoroutineScope>` 包裝重建。
- **優先級**：**P2**（視多執行緒連線管理需求）。

### S5 — `PersistentCookieJar`

- **建議**：非同步載入 + 記憶體先空後替換（注意併發讀取一致性）。
- **優先級**：**P1**（若宿主會在主執行緒建立含大量磁碟讀取的 Jar）。

---

## 四、中等項（M1–M8）— 與 v1 差異補正

- **M4**：原稿「busy-wait / 浪費 CPU」— **不精確**；實作為 **協程 `delay` 輪詢**，不佔用 CPU 自旋，但仍可優化為信號式等待。
- **其餘 M 項**：與原 plan1 一致，狀態見 **§二索引表**。

---

## 五、輕微與增強（L / F）

- **L1–L6、L8**：維持原 plan1 建議，作為 **P3 / 技術債** 分批處理。
- **L7**：視為 **已關閉**（CI 已跑 ktlint）。
- **F1–F8**：產品路線 backlog，與「缺陷是否存在」分開管理。

---

## 六、整合後分階段路線圖（優先級）

| 階段 | 優先級 | 涵蓋項目 | 說明 |
|------|--------|----------|------|
| 第一階段 | **P0** | **S1/R4** | 修正 `extraHeaders` 傳遞方式；影響面最大，建議獨立 PR + CHANGELOG。 |
| 第二階段 | **P1** | **S2/R5**、**S5**、可選 **M2/M3/M4** | 執行緒安全與 I/O；OkHttp 建構去重、重試協調、Token 等待模型可併行評估。 |
| 第三階段 | **P2** | **S4**、**M5/R6**、**M6/R9**、**S3/R7** | 邊界行為、WS 設定透明化、追蹤 API 微調。 |
| 第四階段 | **P3** | **M7–M8**、**L1/L3–L6**、**F*** | 效能、一致性、擴充能力。 |

---

## 七、長期架構（沿用原 plan1 §四）

1. 模組化（HTTP core / WebSocket 子模組）  
2. Converter 外掛化（Gson / Moshi / kotlinx.serialization）  
3. 協程優先、減少 `ThreadLocal` / 阻塞式重試對執行緒池的壓力  
4. 建構時設定不可變、執行時設定明確暴露（如 `StateFlow`）  
5. 非 Hilt DI 的配套說明或模組（選配）

---

## 八、總結

- **原 plan1 所列問題大多仍存在於程式碼或設計層**；例外：**L7（ktlint CI）已存在**；**M4 的「CPU busy-wait」應改為「delay 輪詢」**。
- **S1/R4 在對照 `withContext(Dispatchers.IO)` 後，應視為比「僅 ThreadLocal 交錯」更高優先的 correctness 風險**（主執行緒發起 + `extraHeaders`）。
- **先前 R1–R10** 已併入 **§二索引表**；**R10** 與 Demo/README 相關修正已在倉庫落地。
- 維護時請以 **本檔 v2** 為主索引；若單項狀態變更，更新 **§二** 與 **§六** 即可。
