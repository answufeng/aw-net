# aw-net 優化路線圖（對照結果）

**產出日期**：依倉庫實作階段更新。  
**審查基準檔（最新）**：[aw-net-review-and-optimization-plan-v2.md](./aw-net-review-and-optimization-plan-v2.md)（合併 `plan1` + R1–R10 與程式碼核對）  
**簡版條目**：[aw-net-review-and-optimization-plan.md](./aw-net-review-and-optimization-plan.md)  
**範圍**：`aw-net`、 `demo`；不含單元測試與 Compose。

> **注意**：下表仍為早期僅對照 R1–R10 之摘要；**S1/R4（ThreadLocal + `withContext(IO)`）在 v2 中已上調嚴重度**，請以 v2 §二、§三為準。

---

## 1. 審查項對照表

| ID | 審查項（摘要） | 狀態 | 證據 / 說明 | 建議動作 |
|----|----------------|------|-------------|----------|
| R1 | 重試疊加 | **已緩解（文件 + Debug 警告）** | `RequestExecutor.warnIfLayeredCoroutineRetry`（`BuildConfig.DEBUG` 且 `enableRetryInterceptor` 與 `retryOnFailure>0` 時 `Log.w`）；預設 `enableRetryInterceptor = false`（`NetworkConfig`）。 | 維持；整合方遵守「只開一層」。可選：Release 改為可設開關的輕量日誌（非必須）。 |
| R2 | 雙軌 Token | **已緩解（設計 + 註解）** | `RequestExecutor` 檔首 KDoc；`handleUnauthorizedIfNeeded` 與 `TokenAuthenticator` + `TokenRefreshCoordinator` 協調。 | 維持；後端若混用 401 與 body code，在專案 README 已說明共享刷新臨界區，需在 API 規格層統一約定。 |
| R3 | 設定快照 vs 執行時 | **已緩解（文件）** | `NetworkConfig` KDoc 區分兩類欄位；`NetworkModule.provideOkHttpClient` KDoc 說明單例與動態攔截器。 | 維持；可選：在 README 加一張「執行時可變 / 僅建構時」簡表（見 §3）。 |
| R4 | ThreadLocal + `extraHeaders` | **仍存在（設計限制）** | `NetworkExecutor.withExtraHeaders` + `RequestExtraHeadersInterceptor.threadLocalHeaders`。 | **P0 評估**：若呼叫端會在同執行緒上巢狀或交錯 suspend 且依賴不同 headers，改為 `Request.tag` 或 OkHttp `Chain` 可見的請求級承載；屬潛在 **API 行為變更**，需 semver 與遷移說明。短期：**文件**標註「避免並行於同執行緒依賴不同 extraHeaders」。 |
| R5 | `ExceptionHandle` 併發 | **仍存在（低機率）** | `ExceptionHandle.customMappers` 為 `mutableListOf`，`register` / `clearCustomMappers` 無鎖。 | **P1**：改為 `CopyOnWriteArrayList` 或 `@Synchronized` / 啟動階段一次性註冊並 `Collections.unmodifiableList` 快照。 |
| R6 | Gson 映射多次讀取 | **仍存在（邊界）** | `GlobalResponseTypeAdapterFactory` 多處 `mappingProvider()`。 | **P2**：在單次 `read` 開頭快照 `val m = mappingProvider()` 並全程使用 `m`；或文件約定「解析期間不變更 mapping」。 |
| R7 | `NetEventDispatcher` | **已緩解（文件）** | `NetEventDispatcher` KDoc；`NetworkModule.provideNetTrackerDelegate` 說明勿與手動賦值並用。 | 維持。 |
| R8 | R8 / Gson | **已緩解** | [aw-net/consumer-rules.pro](aw-net/consumer-rules.pro)；README ProGuard 小節。 | 維持。 |
| R9 | WebSocket Client 覆寫 | **設計如此** | `WebSocketClientImpl` 內 `wsClient` lazy：`newBuilder()`、`pingInterval(0)`、自管心跳。 | **P2**：README WebSocket 小節可補一句「與共用 Client 的逾時 / ping 差異」；見本次 README 更新。 |
| R10 | Demo / 文件一致 | **已緩解（本次修正）** | `AdvancedConfigActivity` 將連線/讀/寫逾時由錯誤的 `ms` 改為 `s`，與 `NetworkConfig` 語意一致。 | 已於 demo 修正顯示。 |

---

## 2. 合併預審與清單後的優先級

| 優先級 | 項目 | 說明 |
|--------|------|------|
| P0 | R4 ThreadLocal | 僅在確認真實場景會踩雷時實作傳遞方式改造；否則以文件與使用守則為主。 |
| P1 | R5 `ExceptionHandle` | 低成本加固，建議下一個小版本納入。 |
| P2 | R6 映射快照、R3/R9 README 表格化 | 體驗與邊界行為清晰化。 |
| — | R1、R2、R7、R8 | 以現狀文件與預設為主，無強制程式變更。 |

---

## 3. 相容性與風險註記

- **R4 若改為非 ThreadLocal**：可能影響僅依賴現有 `RequestOption.extraHeaders` 行為的進階用法；需 **CHANGELOG** 與遷移指南。
- **攔截器順序**：任何調整 `NetworkModule` / `AwNet.createOkHttpClient` 的順序，需手動驗證日誌、重試、額外 Header、401 刷新鏈。

---

## 4. Demo / README 已採取動作（對應計畫 optional）

- **Demo**：`AdvancedConfigActivity` 逾時顯示單位修正為秒（對齊 `NetworkConfig`）。
- **README**：新增「重試只开一层」簡短小節，與 `RequestExecutor` Debug 警告呼應；WebSocket 小節補充與共用 `OkHttpClient` 的差異一句話。

---

## 5. 後續維護

新增審查項時：先更新 [aw-net-review-and-optimization-plan-v2.md](./aw-net-review-and-optimization-plan-v2.md)，再同步本檔 §1 / §2（或逐步以 v2 取代本檔表格）。
