# Record 已打印 per 訂單, not per 送貨單號

Another program can delete a 送貨單's `[Delivery ID]` and make a new one, keeping the same 訂單 on it, so a record keyed by `[Delivery ID]` loses the 送貨單 and the 未打印提醒 rings for paper that is already out. 已打印 is now kept in `[Home Delivery$Printed Order]`, one row per (`[Document No_]`, `[Requested Delivery Date]`, `[Location Code]`), each with that 訂單's line count for that day and location; a 送貨單 is 已打印 only when every (訂單, location) on it has a row with a matching line count. The 送貨單 list and 收據 still group lines by `[Delivery ID]`.

## Considered Options

- Treat one 訂單 as one 送貨單 and drop `[Delivery ID]`: rejected, a 送貨單 carries several 訂單 and the printed pages show the `[Delivery ID]`.
- Key by `[Document No_]` and date only: rejected, one 訂單 can have both 牛奶 and 雪糕 lines, which are two 送貨單 printed separately.
- Key by 類別 instead of `[Location Code]`: rejected, the raw NAV column needs no mapping in SQL; a 牛奶 訂單 with lines in both 92 and 93 just gets two rows.
- Store the whole 送貨單's line count on each row: rejected, a regrouping by the other program would then ring even though every 訂單 was printed unchanged.

## Consequences

- The phone calls `usp_MarkOrderPrinted` once per (訂單, location). If it stops partway, the 送貨單 stays 未打印 and the alert rings: safe side.
- `[Printed At]` and `[Printed By]` are kept only for tracing a "printed but still rings" report; nothing decides 已打印 by them. The `[Delivery ID]` is not stored: it can be found again from the 訂單.
- `[Home Delivery$Printed Record]` stays until the new table is trusted. Launch-day SQL copies it into the new table through the 訂單 its `[Delivery ID]` still has.
