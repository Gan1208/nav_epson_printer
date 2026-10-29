# 未打印提醒 + 已打印標記

Status: confirmed

## 數據庫

- 新表 `[Home Delivery$Printed Record]`：`[Delivery ID]`（PK）、`[Line Count]`、`[Printed At]`、`[Printed By]`。
- 手機 sqluser 對所有表仍然只讀，只多 `EXECUTE` `usp_MarkDeliveryPrinted`。見 `docs/adr/0001-mark-printed-through-stored-procedure.md`。
- SQL：`.scratch/unprinted-alert/printed-record.sql`。

## 已打印

- 一張 送貨單 三頁全部印成功先叫 proc，傳 `[Delivery ID]`、印咗幾多行、邊部打印機。
- 撳打印但印唔到 → 唔記錄。
- 單一張同多張打印都一樣。多張打印每張印完即刻記錄，唔係等全部印完。
- 而家嘅行數 ≠ 記錄嘅行數 → 當 未打印（加咗單或者減咗單）。
- 只有用呢個 app 印嘅先有記錄。

## 查詢清單

- 每行 送貨單 旁邊顯示「已印」。未印冇標記。同一個查詢 LEFT JOIN，唔多查一次。

## 未打印提醒

- 設定入面開關，預設關。只有負責嘅一兩部手機開。
- 每日（包括週末假期），牛奶同雪糕都查。
- 16:00 exact alarm 叫醒 → foreground service → 每 5 分鐘查一次 → 18:00 自己停。
- 查：送貨日 = 聽日，有冇 送貨單 未打印。
- 有 → 用鬧鐘聲道響 30 秒（唔改手機音量）+ 通知。每 5 分鐘都響，直到冇未打印。
- 撳通知 → 打開聽日嘅清單，未打印嗰啲已經剔好。
- 連續 2 次連唔到數據庫 → 安靜通知「未打印提醒：連不上數據庫」。
- 手機重啟後重新排 alarm。
- 第一次開提醒：問 exact alarm 權限、通知權限，再提示去關電池優化（打開系統電池優化頁）。

## 上線

- 一定要先跑 SQL，再裝新 app：查詢會 JOIN `[Home Delivery$Printed Record]`，冇呢個表就會「連線失敗」。
- 上線當日手動跑 `printed-record.sql` 最底嘅 backfill。

## 前提

- `[Delivery ID]` 隔日唔會重複。
- 手機只喺公司網絡連數據庫（jTDS 冇加密）。
