# 收據預覽 (Receipt preview)

Status: confirmed

## 目標

首頁揀了一張 送貨單，按「預覽」，看到它的 收據：三個 tab，`回單` / `執貨單` / `客戶單`。
這次只做預覽，不做打印。

## 範圍

- 只預覽首頁揀中的 **一張** 送貨單。
- 不做一次預覽全日所有單。
- 不做打印。

## 紙張

- 打印機：Epson TM-m30II，80mm 熱敏紙。
- 預覽按 80mm 紙的可印寬度畫，之後打印可以直接用同一個排版。

## 資料來源

| 印出來的 | 來源 |
|---|---|
| 送貨單號 | `[Home Delivery$Sales Line].[Delivery ID]` |
| 訂單號 | `[Sales Line].[Document No_]` = `[Sales Header].[No_]` |
| Delivery Date / 日期 | `[Sales Line].[Requested Delivery Date]`（送貨日） |
| Route | `[Sales Line].[Location Code]` |
| Name | `[Sales Header].[Ship-to Name]` |
| Contact | `[Sales Header].[Ship-to Contact]` |
| Phone | `[Sales Header].[MagTelephone]` |
| 地址 | 見下面「地址」 |
| 個人留言 | `[Sales Header].[MagSub_district]`，長度 > 1 才算有 |
| 貨品英文名 | `[Sales Line].[Description]` |
| 貨品中文名 | `[Sales Line].[Description 2]` |
| 貨品短名（執貨單） | `[Home Delivery$Item].[Short Chinese Description]`，`[Item].[No_]` = `[Sales Line].[No_]`；空的就用中文名 |
| 數量 | `[Sales Line].[Quantity]`，印成整數 |
| 訂閱單 mark | 跟首頁一樣：任何一個訂單 `[MagIs_order_subscription] > 0` |

### 地址

四行，空的欄位不印那一行：

```
[Ship-to Address], [Ship-to Address 2]
[Bill-to Address]
[Bill-to Address 2]
[Bill-to City]
```

### 貨品規則

- 數量 0 的貨品不印。
- 一張 送貨單 可以有幾個 訂單：每個 訂單 印「訂單號」做小標題，下面是它的貨品，然後下一個 訂單。只有一個 訂單 時，樣子跟截圖一樣。

### Route

一張牛奶 送貨單 可以同時有 92 和 93 的貨品。這時印 `92 / 93`。

## QR code

- 回單、執貨單：`#dd/MM/yy###<完整送貨單號>##`，日期用送貨日。
  例：送貨日 2026-10-20，送貨單號 `1799` → `#20/10/26###1799##`
- 客戶單：`https://homedelivery.kowloondairy.com/contact-us`

## 顯示格式

- 日期：`20 Oct 2026`
- Delivery ID：短號（後四位）+ 有訂閱單就加 `**`，例 `1799**`。跟首頁一樣。

## 三頁排版

### 回單

1. logo（`kdl_receipt_logo.jpeg`），置中
2. `Delivery Date : 20 Oct 2026`
3. `Name :` / `Contact :` / `Phone :` / `Address :`（地址多行，向內縮）
4. 每個 訂單：訂單號，然後貨品 `1  x <英文名>`，下一行縮入 `<中文名>`
5. 左：`Route : 92`、`Delivery ID : 1799**`；右：QR code
6. `Signature :`
7. `Bottle Return Qty :`（留空給車隊填）
8. 底部置中：`-- For internal usage --`

### 執貨單

大字，沒有 logo。

1. 虛線
2. 左：`20 Oct 2026`；右：QR code，下面 `1799**`
3. 每個 訂單：訂單號，然後貨品 `1  x <短名>`，每行之間有實線
4. 虛線

短名：`[Item].[Short Chinese Description]`（例 `鮮奶 (大)`）。貨品沒有短名，就用 `[Description 2]`。

### 客戶單

1–4 跟 回單 一樣
5. 左：`Route : 92`、`Delivery ID : 1799**`、`Remarks:` 和固定文字；右：contact-us QR code
6. 有個人留言就印在 Remarks 下面
7. 底部置中：`-- Thank you --`

Remarks 固定文字（寫死在 app）：

```
Products must be placed in the refrigerator to avoid spoilage.
請盡快將產品放入雪櫃，以免變壞。
```
