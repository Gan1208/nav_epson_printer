# 01 — App 用字改書面語

Status: resolved
Type: task

Spec: `.scratch/receipt-print/spec.md`（「用字」段）

## 要做

App 畫面上所有字（包括出錯訊息、掣、`contentDescription`、印出嚟嘅頁）改用書面語，唔用口語。

已知要改嘅（唔齊，要自己再搵一次）：

- `print/ReceiptPrinter.kt`：`PrintProblem` 全部訊息。例如 `UNREACHABLE` 改做「無法連接打印機，請檢查打印機是否已開啟，以及手機是否已連接公司 wifi」。
- `settings/PrinterSettingsScreen.kt`：「打印緊…」、測試打印成功嗰句。
- `receipt/ReceiptPreviewScreen.kt`：「打印緊…」、「只印呢頁」、「重印呢頁」。
- `landing/LandingScreen.kt`：「冇送貨單」、「揀日期」。

## 完成條件

- `grep` 唔再搵到 唔/冇/咗/緊/嘅/係/喺/呢/揀 等口語字喺 UI 字串。
- 有 assert 呢啲字嘅 test 跟住改，全部 test pass。
