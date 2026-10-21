# Nav Print

An Android app for choosing and printing delivery documents.

## Language

**送貨單 (Delivery Note, DN)**:
A document listing goods to be delivered. Shown in the UI as 送貨單; "DN" is the short form.
_Avoid_: Order, invoice

**送貨單號 (DN Number)**:
The number that identifies a 送貨單. In lists, people see only its last four characters (the whole number if shorter); short numbers can repeat.
_Avoid_: ID

**訂單 (Order)**:
A customer's order. One 送貨單 can carry goods from several 訂單.
_Avoid_: 送貨單 (a different thing)

**訂閱單 (Subscription Order)**:
A 訂單 that repeats on a schedule. A 送貨單 with at least one 訂閱單 is marked `**` after its number.
_Avoid_: Recurring order

**送貨日 (Delivery Date)**:
The day the goods on a 送貨單 are delivered. Shown in the UI as 日期.
_Avoid_: Issue date, print date

**類別 (Category)**:
The kind of goods: 牛奶 (milk) or 雪糕 (ice cream). 全部 means no filter, not a category: it gives both the 牛奶 送貨單 and the 雪糕 送貨單, each on its own.
Decided by the location the goods ship from: locations 92 and 93 are 牛奶, location T1 is 雪糕.
A 送貨單 never mixes 牛奶 and 雪糕, because they go out on different trucks.
On printed pages the location is shown as Route.
_Avoid_: Type, product type

**收據 (Receipt set)**:
What gets printed for one 送貨單: three pages: 回單, 執貨單 and 客戶單.
_Avoid_: Invoice

**回單 (Return Receipt)**:
The page the fleet team brings back. The customer signs it, and the fleet team writes how many bottles came back.

**執貨單 (Packing Note)**:
The page warehouse colleagues use to pack the goods. Big print, no logo, short Chinese item names.
_Avoid_: Picking list

**客戶單 (Customer Copy)**:
The page the customer keeps. It has remarks, a contact-us QR code, and the customer's personal message if there is one.
_Avoid_: 送貨單 (that is the whole delivery, not one page)
