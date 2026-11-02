# Nav Print

An Android app for choosing and printing delivery documents.

## Language

**送貨單 (Delivery Note, DN)**:
A document listing goods to be delivered. Shown in the UI as 送貨單; "DN" is the short form.
_Avoid_: Order, invoice

**送貨單號 (DN Number)**:
The number that identifies a 送貨單. In lists, people see only its last four characters (the whole number if shorter); short numbers can repeat.
It can change: another program may delete and make it again. The 送貨單 keeps the same 訂單, so it is still the same 送貨單.
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
Staff pick it when searching; it starts at the phone's 本裝置預設類別 each time the app opens.
_Avoid_: Type, product type

**本裝置預設類別 (Device default category)**:
The 類別 a phone starts on when the app opens: 全部, 牛奶 or 雪糕, set in 設定. Staff can change the 類別 for a search without changing this. Starts as 全部.
_Avoid_: Printer category

**收據 (Receipt set)**:
What gets printed for one 送貨單: three pages: 回單, 執貨單 and 客戶單.
_Avoid_: Invoice

**多張打印 (Multi print)**:
Printing the 收據 of two or more 送貨單 picked from the list for the chosen 送貨日 and 類別, in 送貨單號 order. One 送貨單's three pages all come out before the next 送貨單 starts. Picking every 送貨單 in the list prints the whole day.
_Avoid_: Batch print, 全日打印 (it no longer has to be the whole day)

**已儲存打印機 (Saved printer)**:
A printer that has printed a test page from this phone, kept so it can be picked again. Each has an IP, and may have a model and a 打印機名稱. Staff pick one each time they print; the one picked last time is picked for them.
_Avoid_: Printer list, paired printer

**打印機名稱 (Printer name)**:
A name staff give a 已儲存打印機 so others can tell which one they are using, e.g. 倉庫. Optional, and two printers may share one; the IP tells them apart.
_Avoid_: Label, alias

**已打印 (Printed)**:
A 送貨單 whose whole 收據 (all three pages) has come out of a printer. Pressing print is not enough; a failed print leaves it 未打印. If the 送貨單 changes after printing (lines added or removed), it is 未打印 again.
It is remembered for each 訂單 on the 送貨單, for that 送貨日 and the location the goods ship from, not by 送貨單號, so a new 送貨單號 does not make it 未打印. A 送貨單 is 已打印 only when every 訂單 on it is.
_Avoid_: Sent, done

**未打印提醒 (Unprinted alert)**:
A loud alert, between 4 and 6 in the afternoon, on phones that turned it on, when a 送貨單 for tomorrow's 送貨日, of any 類別, is not yet 已打印. It is there because 送貨單 can appear after the 4 o'clock print.
_Avoid_: New order alert

**回單 (Return Receipt)**:
The page the fleet team brings back. The customer signs it, and the fleet team writes how many bottles came back.

**執貨單 (Packing Note)**:
The page warehouse colleagues use to pack the goods. Big print, no logo, short Chinese item names.
_Avoid_: Picking list

**客戶單 (Customer Copy)**:
The page the customer keeps. It has remarks, a contact-us QR code, and the customer's personal message if there is one.
_Avoid_: 送貨單 (that is the whole delivery, not one page)
