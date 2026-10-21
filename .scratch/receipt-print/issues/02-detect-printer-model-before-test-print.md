# 02 — 測試打印前偵測打印機型號

Status: resolved
Type: task
Blocked by: 01

Spec: `.scratch/receipt-print/spec.md`（「設定頁」段，「測試打印」下面全部）

## 要做

撳「測試打印」→ 用 Epson ePOS SDK 嘅 `Discovery`（`app/libs/ePOS2.jar`）喺 wifi 搵嗰個 IP 嘅打印機型號，最多 5 秒 → 彈框問「打印」/「取消」→ 撳「打印」先行而家嘅 `printTest()`。

- 三款彈框字、型號比對（以 `TM-m30II` 開頭）、搵唔到都照俾印：見 spec。
- 搜尋期間掣顯示「搜尋打印機中…」，唔可以再撳。
- 印 送貨單 唔受影響。
- 偵測放喺 `print/` 一個新 interface 後面，等 `PrinterSettingsViewModel` 可以用 fake 測試（跟 `ReceiptPrinter` 一樣做法）。
- `Discovery` 一次只可以開一個；做完一定要 `stop()`。

## 完成條件

- `PrinterSettingsViewModelTest` 覆蓋：搵到啱型號、型號唔同、搵唔到、撳取消唔印、撳打印就印。
- 全部 test pass，`assembleDebug` 成功。
