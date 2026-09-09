# B2 — אישור בקשה: בדיקות וראיות

## מצב

2026-09-09: **מומש ואומת חלקית ללא Docker**. הרצה ב-15:51:44 +03:00: 23 בדיקות יחידה B2 + 4 MockMvc B2 + 24 בדיקות רגרסיה B1 = **51 עברו**, ללא כישלונות, שגיאות או דילוגים; BUILD SUCCESS. קומפלו 14 קובצי יישום ו-6 קובצי בדיקות. לא הופעלו שרת, Docker או PostgreSQL. B3 לא התחיל.

הראיה המקומית היא `.local-run/b2-tests.log`. סיכום שמות הבדיקות וחתימות קובצי Java ב-[B2-test-run.json](evidence/B2-test-run.json). דוחות XML המקומיים ב-backend/target/surefire-reports; הם עשויים להתחלף בהרצה אחרת. חבילת מסד שקיימת בקוד אינה בדיקה שעברה.

## תרחישים

| מזהה / סוג | תרחיש ותוצאה צפויה | מצב | ראיה / פעולה שנותרה |
|---|---|---|---|
| U01 יחידה | יתרה מדויקת: אישור ושמירה | עבר | LeaveApprovalTests.rechecksCalendarBalance[1]; יומן ההרצה |
| U02 יחידה | חריגה מהיתרה: 409 ללא שינוי סטטוס או save | עבר | rechecksCalendarBalance[2] |
| U03 יחידה | מכסה מוצתה: 409 ללא שינוי | עבר | rechecksCalendarBalance[3] |
| U04 יחידה | שנה קודמת אינה גורעת מהנוכחית | עבר | rechecksCalendarBalance[4] |
| U05 יחידה | שנה עתידית אינה גורעת מהנוכחית | עבר | rechecksCalendarBalance[5] |
| U06 יחידה | חציית שנה: בדיוק היתרה בכל שנה מתקבלת | עבר | rechecksCalendarBalance[6] |
| U07 יחידה | חריגה בשנה השנייה: דחייה | עבר | rechecksCalendarBalance[7] |
| U08 יחידה | שנה מעוברת נספרת נכון | עבר | rechecksCalendarBalance[8] |
| U09 יחידה | קריאת מזהה עובד, נעילה, טעינת מצב, חישוב ושמירה בסדר זה | עבר | locksOwnerBeforeReadingStateAndBalance; מוכיח סדר קריאות מדומות בלבד |
| U10 יחידה | בקשה חסרה: 404 ללא גישה לעובד | עבר | missingRequest |
| U11 יחידה | עובד חסר: 409 ללא שינוי | עבר | missingEmployee |
| U12 יחידה | בקשה נמחקה בזמן ההמתנה: 404 | עבר | requestDeletedWhileWaiting |
| U13 יחידה | בעלות השתנתה: 409 | עבר | changedOwner |
| U14 יחידה | בקשה APPROVED: 409, נשארת APPROVED | עבר | rejectsTerminalStatus[1] |
| U15 יחידה | בקשה REJECTED: 409, נשארת REJECTED | עבר | rejectsTerminalStatus[2] |
| U16 יחידה | SICK אינה כפופה למכסת החופשה | עבר | nonVacationDoesNotConsumeQuota[1]; אין שאילתת ניצול |
| U17 יחידה | UNPAID אינה כפופה למכסת החופשה | עבר | nonVacationDoesNotConsumeQuota[2]; אין שאילתת ניצול |
| U18 יחידה | סוג/התחלה/סיום חסרים, תאריכים הפוכים או days לא עקבי: דחייה ללא שינוי | עבר | invalidStoredData[1–5] |
| U19 יחידה | שגיאת שמירה אינה נבלעת כהצלחה | עבר | databaseFailurePropagatesRatherThanReturningSuccess; אינו מוכיח rollback במסד |
| M01 MockMvc | POST מאשר ומחזיר status=1 ומזהה | עבר | LeaveApprovalHttpTests.approvedJsonRetainsNumericStatus; שירות מדומה |
| M02 MockMvc | שגיאת בקשה חסרה: HTTP 404 והודעה | עבר | mapsBusinessErrors[1] |
| M03 MockMvc | שגיאת מצב: HTTP 409 והודעה | עבר | mapsBusinessErrors[2] |
| M04 MockMvc | חוסר יתרה: HTTP 409 והודעה | עבר | mapsBusinessErrors[3] |
| R01 רגרסיה | 20 יחידה + 4 MockMvc של B1 לאחר שיתוף החישוב | עבר | אותה הרצה; רשימת התרחישים נשארת ב-B1_QA |
| P01 PostgreSQL | APPROVED נשמר ונקרא מחדש | חסום | approvalIsPersisted הוכן וקומפל בלבד |
| P02 PostgreSQL | חריגה אינה משנה PENDING במסד | חסום | insufficientBalanceDoesNotChangeStoredStatus הוכן וקומפל בלבד |
| P03 PostgreSQL | חריגה לאחר flush מבטלת שינוי במסד | חסום | outerFailureRollsBackFlushedApproval הוכן וקומפל בלבד |
| P04 PostgreSQL | שתי בקשות שונות: רק אחת מאושרת כששתיהן חורגות יחד | חסום | concurrentDifferentRequestsCannotExceedQuota הוכן וקומפל בלבד |
| P05 PostgreSQL | שני אישורים לאותה בקשה: הצלחה אחת ו-409 אחד | חסום | concurrentSameRequestIsApprovedOnce הוכן וקומפל בלבד |
| H01 HTTP ברשת | POST אמיתי, סטטוס נשמר וניתן לקריאה; אישור חוזר נדחה | חסום | תוכנית בדיקה בלבד; דורש API ומסד |
| UI01 ממשק | כפתור האישור משנה בקשה תקינה ומציג תוצאה נכונה | חסום | לא נבדק; משוב מתקדם שייך ל-F2 |

## בדיקות המקביליות שהוכנו

LeaveApprovalPostgresTests משתמש ב-Testcontainers עם postgres:16-alpine ובשירות Spring אמיתי. כל מבחן יוצר עובד משלו. בדיקות המקביליות מחזיקות נעילת עובד בטרנזקציה נפרדת, מפעילות שני workers ומוודאות שלא מתקבלת תוצאה כל עוד הנעילה מוחזקת. לאחר שחרור הנעילה נבדקות התשובות והמצב הנשמר. לכל המתנה יש timeout, וה-executor נסגר ב-finally. הבדיקות אינן משתמשות ב-sleep כתחליף לבדיקת תוצאה.

לא קיימת ראיית הרצה לבדיקות אלו. גם לאחר קומפילציה ייתכנו שגיאות שאילתה, proxy, תשתית או מקביליות שיתגלו רק במסד אמיתי. בפרט, בדיקת סדר קריאות ב-mocks אינה ראיה לנעילה אפקטיבית. בדיקת rollback מזריקה כשל אחרי flush בטרנזקציה חיצונית; היא אינה מדמה כל סוג כשל commit.

## סגירה והמשך

מנגנון וקודי תשובה ב-DECISIONS, כולל גבולות ההגנה והנחת שימוש באותו מסלול נעילה לכל האישורים. במהלך הסבב לא נצפה כשל בדיקות זמין, ולכן לא נטען לשחזור כשל שלא התרחש. אין לשנות את מצבי P/H/UI לעבר לפני הרצה וראיות.

כעת עוצרים לפני B3. נדרשת בעתיד סביבה זמינה ואישור לחזור להפעלה, כדי להריץ את כלל בדיקות PostgreSQL של B1 ו-B2 ולבדוק H01. אין צורך בפעולה מהמשתמשת לצורך השלב המבודד שהושלם. B2 לא הושלם מקצה לקצה.

פקודה שהורצה מתוך backend לאחר טעינת scripts/Use-LocalTools.ps1:

```powershell
mvn.cmd -o '-Dmaven.repo.local=../.local-tools/m2' '-Dtest=LeaveBalanceTests,LeaveRequestHttpTests,LeaveApprovalTests,LeaveApprovalHttpTests' test
```

רק בעתיד עם Docker מאושר ופעיל: mvn test יריץ גם את חבילות PostgreSQL. לא הורץ בסבב זה.
