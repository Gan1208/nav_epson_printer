# Mark 送貨單 printed through a stored procedure, not table write access

The phone's SQL user stays read-only on every table. To record that a 送貨單 is 已打印, the phone calls `dbo.usp_MarkDeliveryPrinted`, and that user is granted `EXECUTE` on this one procedure only; ownership chaining lets the procedure write to `[Home Delivery$Printed Record]`. A trigger was considered first but cannot work: printing happens on the phone and never writes to the database, so there is nothing for a trigger to fire on.

## Consequences

- The phone passes the number of 收據 lines it printed. When the 送貨單 later has a different number of lines, it counts as 未打印 again.
- Anything printed outside this app (e.g. from NAV) is never recorded and will raise the 未打印提醒.
