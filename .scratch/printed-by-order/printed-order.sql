-- Run once in MSSQL as an admin, before the new app version goes on the phones.
-- [NAVPrintApp] is the phone's login (navSql.user in local.properties). See docs/adr/0002-printed-record-by-order.md.
USE [KDLNAV_HD];
GO

-- One row per (訂單, 送貨日, location) that came out of a printer.
CREATE TABLE [dbo].[Home Delivery$Printed Order] (
    [Document No_]            nvarchar(20) NOT NULL,
    [Requested Delivery Date] date         NOT NULL,
    [Location Code]           nvarchar(10) NOT NULL,
    [Line Count]              int          NOT NULL,
    -- Only for tracing a "printed but still rings" report; nothing decides 已打印 by these.
    [Printed At]              datetime     NOT NULL DEFAULT GETDATE(),
    [Printed By]              nvarchar(50) NOT NULL DEFAULT '',
    PRIMARY KEY ([Document No_], [Requested Delivery Date], [Location Code])
);
GO

-- Same owner (dbo) as the table, so the phone needs EXECUTE only, no INSERT/UPDATE.
CREATE PROCEDURE [dbo].[usp_MarkOrderPrinted]
    @DocumentNo   nvarchar(20),
    @DeliveryDate date,
    @LocationCode nvarchar(10),
    @LineCount    int,
    @PrintedBy    nvarchar(50)
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE [dbo].[Home Delivery$Printed Order]
       SET [Line Count] = @LineCount, [Printed At] = GETDATE(), [Printed By] = @PrintedBy
     WHERE [Document No_] = @DocumentNo
       AND [Requested Delivery Date] = @DeliveryDate
       AND [Location Code] = @LocationCode;
    IF @@ROWCOUNT = 0
        INSERT INTO [dbo].[Home Delivery$Printed Order]
               ([Document No_], [Requested Delivery Date], [Location Code], [Line Count], [Printed By])
        VALUES (@DocumentNo, @DeliveryDate, @LocationCode, @LineCount, @PrintedBy);
END
GO

GRANT EXECUTE ON [dbo].[usp_MarkOrderPrinted] TO [NAVPrintApp];
-- Only needed if the user is not already in db_datareader:
GRANT SELECT ON [dbo].[Home Delivery$Printed Order] TO [NAVPrintApp];
GO

-- Launch day: copy what the old table says is 已打印 into the new one, through the 訂單 each
-- [Delivery ID] still has. Only 送貨單 whose line count still matches count as 已打印, as before.
WITH Lines AS (
    SELECT l.[Delivery ID], l.[Document No_], l.[Location Code],
           CAST(l.[Requested Delivery Date] AS date) AS [Day],
           COUNT(*) OVER (PARTITION BY l.[Delivery ID]) AS [Note Lines]
      FROM [dbo].[Home Delivery$Sales Line] l
     WHERE l.[Location Code] IN ('92', '93', 'T1') AND l.[Delivery ID] <> ''
       AND l.[Requested Delivery Date] >= CAST(GETDATE() AS date)
)
INSERT INTO [dbo].[Home Delivery$Printed Order]
       ([Document No_], [Requested Delivery Date], [Location Code], [Line Count], [Printed At], [Printed By])
SELECT x.[Document No_], x.[Day], x.[Location Code], COUNT(*), MAX(r.[Printed At]), MAX(r.[Printed By])
  FROM Lines x
  JOIN [dbo].[Home Delivery$Printed Record] r
    ON r.[Delivery ID] = x.[Delivery ID] AND r.[Line Count] = x.[Note Lines]
 WHERE NOT EXISTS (
       SELECT 1 FROM [dbo].[Home Delivery$Printed Order] o
        WHERE o.[Document No_] = x.[Document No_] AND o.[Requested Delivery Date] = x.[Day]
          AND o.[Location Code] = x.[Location Code])
 GROUP BY x.[Document No_], x.[Day], x.[Location Code];
GO

-- Once the new table is trusted (a few weeks with no wrong alerts):
-- DROP PROCEDURE [dbo].[usp_MarkDeliveryPrinted];
-- DROP TABLE [dbo].[Home Delivery$Printed Record];
