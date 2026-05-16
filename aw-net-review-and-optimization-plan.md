# aw-net 審查與優化基準清單

**已合併為 v2**：請以 [aw-net-review-and-optimization-plan-v2.md](./aw-net-review-and-optimization-plan-v2.md) 為主（合併 `plan1` 與本檔 R1–R10 之程式碼核對）。本檔保留簡版 R1–R10 條目供快速查閱。

本檔作為函式庫審查與後續對照的**基準問題清單**。若你另有更完整的內部審查筆記，請合併條目或覆寫對應章節；對照結果與建議動作請見同目錄 [AW_NET_OPTIMIZATION_ROADMAP.md](./AW_NET_OPTIMIZATION_ROADMAP.md)。

**範圍**：`aw-net` 模組與 `demo`（不含單元測試、不含 Compose）。

---

## R1 — 重試疊加（OkHttp 攔截器 × 協程層）

**描述**：同時啟用 `NetworkConfig.enableRetryInterceptor`（`DynamicRetryInterceptor`）與 `RequestOption.retryOnFailure` 時，退避與重試次數可能疊加，延遲與副作用放大。

**預期行為**：預設或文件化為「只開一層」；Debug 下可觀察到警告日誌。

---

## R2 — 雙軌 Token 刷新（HTTP 401 vs 業務未授權）

**描述**：HTTP 層 401 與「HTTP 200 + body 業務碼未授權」可能走不同路徑；若後端混用約定，可能只觸發單一路徑刷新。

**預期行為**：文件與 Demo 與後端約定一致；協調器避免併發重複刷新。

---

## R3 — `NetworkConfig` 快照與執行時可變項

**描述**：部分欄位僅在建立 `OkHttpClient` / `Retrofit` / `TokenRefreshCoordinator` 時讀取一次，之後僅改 `NetworkConfigProvider` 不會反映到已建立單例。

**預期行為**：KDoc / README 明確區分「執行時可變」與「僅建構時」。

---

## R4 — 請求級 `extraHeaders` 與 ThreadLocal

**描述**：`RequestExtraHeadersInterceptor` 使用 `ThreadLocal` 傳遞 `RequestOption.extraHeaders`；協程在同執行緒上交錯 suspend 時，理論上有誤用或汙染風險。

**預期行為**：文件標註限制；長期可評估改為請求 Tag 等與執行緒無關的傳遞方式。

---

## R5 — `ExceptionHandle` 自訂映射併發安全

**描述**：`customMappers` 為可變清單，若執行期並發 `register` / `clearCustomMappers` 與請求處理，可能有競態。

**預期行為**：啟動階段註冊或加同步／不可變快照。

---

## R6 — Gson `responseFieldMapping` 在單次解析內多次讀取

**描述**：`GlobalResponseTypeAdapterFactory` 在 `read`/`write` 內多次呼叫 `mappingProvider()`；極端情況下若映射在解析中途被改，單次解析內欄位鍵可能不一致。

**預期行為**：文件標註「解析期間勿變更映射」或於讀取開始快照映射。

---

## R7 — `NetEventDispatcher` 全域委派

**描述**：全域 `delegate` 與 Hilt 注入的 `NetTracker` 可能與手動賦值衝突。

**預期行為**：文件明確單一賦值策略；關閉追蹤優先使用 `enableRequestTracking`。

---

## R8 — ProGuard / R8 與 Gson 模型

**描述**：AAR `consumer-rules.pro` 已保留註解與 Gson 擴充點；宿主若大量依賴反射且無 `@SerializedName`，可能仍需額外 keep。

**預期行為**：README ProGuard 小節已說明；必要時補範例。

---

## R9 — WebSocket 與 HTTP 共用 `OkHttpClient`

**描述**：`WebSocketClientImpl` 對傳入 client 做 `newBuilder()` 覆寫逾時並關閉 `pingInterval`，與一般短請求預設行為不同。

**預期行為**：README / Demo 註解說明長連線與心跳自管。

---

## R10 — Demo 設定與文件一致性

**描述**：Demo 顯示的設定單位、重試開關說明等應與實際 API 一致，避免誤導整合方。

**預期行為**：畫面與 README 與 `NetworkConfig` 語意一致。

---

## 維護說明

- 新增審查項時：在本檔增加 **R{n}** 條目，並在 `AW_NET_OPTIMIZATION_ROADMAP.md` 增加對照列與狀態欄位。
- 狀態建議取值：**仍存在**、**已緩解**、**不適用**、**僅文件化**。
