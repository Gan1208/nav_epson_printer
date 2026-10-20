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
The kind of goods: 牛奶 (milk) or 雪糕 (ice cream). 全部 means no filter, not a category.
Decided by the location the goods ship from: locations 92 and 93 are 牛奶, location T1 is 雪糕.
_Avoid_: Type, product type
