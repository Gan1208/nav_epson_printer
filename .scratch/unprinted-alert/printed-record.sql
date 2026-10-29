-- Run once in MSSQL as an admin. [NAVPrintApp] is the phone's login (navSql.user in local.properties).
USE [KDLNAV_HD];
GO

-- If the table already exists with only [Delivery ID], ALTER it to add the other columns instead.
CREATE TABLE [dbo].[Home Delivery$Printed Record] (
    [Delivery ID] nvarchar(20) NOT NULL PRIMARY KEY,
    [Line Count]  int          NOT NULL,
    [Printed At]  datetime     NOT NULL DEFAULT GETDATE(),
    [Printed By]  nvarchar(50) NOT NULL DEFAULT ''
);
GO

-- Same owner (dbo) as the table, so the phone needs EXECUTE only, no INSERT/UPDATE.
CREATE PROCEDURE [dbo].[usp_MarkDeliveryPrinted]
    @DeliveryID nvarchar(20),
    @LineCount  int,
    @PrintedBy  nvarchar(50)
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE [dbo].[Home Delivery$Printed Record]
       SET [Line Count] = @LineCount, [Printed At] = GETDATE(), [Printed By] = @PrintedBy
     WHERE [Delivery ID] = @DeliveryID;
    IF @@ROWCOUNT = 0
        INSERT INTO [dbo].[Home Delivery$Printed Record] ([Delivery ID], [Line Count], [Printed By])
        VALUES (@DeliveryID, @LineCount, @PrintedBy);
END
GO

GRANT EXECUTE ON [dbo].[usp_MarkDeliveryPrinted] TO [NAVPrintApp];
-- Only needed if the user is not already in db_datareader:
GRANT SELECT ON [dbo].[Home Delivery$Printed Record] TO [NAVPrintApp];
GO

-- Launch day only: mark what was already printed today, so the first afternoon does not ring for all of it.
-- INSERT INTO [dbo].[Home Delivery$Printed Record] ([Delivery ID], [Line Count], [Printed By])
-- SELECT l.[Delivery ID], COUNT(*), 'backfill'
--   FROM [dbo].[Home Delivery$Sales Line] l
--  WHERE l.[Requested Delivery Date] >= '<tomorrow yyyy-mm-dd>' AND l.[Requested Delivery Date] < '<day after>'
--    AND l.[Location Code] IN ('92', '93', 'T1') AND l.[Delivery ID] <> ''
--  GROUP BY l.[Delivery ID];
