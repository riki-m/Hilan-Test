# B2 — אישור בקשה: בדיקות וראיות

## מצב עדכני — PostgreSQL, HTTP וממשק

2026-09-09: **דרישות הקבלה של B2 אומתו בהיקף המפורט להלן.** הרצה ראשונה של המימוש הקיים: 71 עברו ב-16:52:40. לאחר הוספת 12 מקרי מסד: **83 עברו ב-16:55:19 +03:00**, אפס כישלונות, שגיאות ודילוגים. החלוקה: B2 — 23 יחידה, 4 MockMvc, 19 PostgreSQL; רגרסיות B1 — 20 יחידה, 4 MockMvc, 13 PostgreSQL. אין שינוי בקוד הייצור, במדיניות המכסה או בתלויות. השינוי בקוד בסבב זה הוא הרחבת LeaveApprovalPostgresTests בלבד, מעל שינויים מקומיים קודמים שנשמרו.

הקוד: HEAD מקומי 8da4450, מימוש B2 ב-934c168, בתוספת הבדיקות המקומיות; חתימות מקורות ושמות בדיקות ב-[B2-postgres-run.json](evidence/B2-postgres-run.json), המסומן R להלן. יומנים: `.local-run/b2-postgres-initial.log` ו-`.local-run/b2-postgres-final.log`. בדיקות המסד פעלו ב-Testcontainers מבודד; מסד הפיתוח לא אופס.

H: [B2-live-http.json](evidence/B2-live-http.json) — 11 בדיקות POST אמיתי, השוואת תמונת כל רשומות המסד לפני/אחרי וקריאות GET חדשות. UI: באותה ראיה — בקשה 17 אושרה בלחיצה במסך, נצפתה Approved לאחר רענון ואומתה ב-SQL וב-GET. רק נתוני עובדים ייעודיים 4 ו-5 נוספו; נתוני B1 ועובדים קיימים לא שונו.

דרישות מפורשות: endpoint לאישור, שגיאות למצבים סופיים/בקשה חסרה, ותיאור ולפחות טיפול חלקי במקביליות. בדיקות יתרה, התמדה, rollback, שנים, סינון ומזהה שגוי הן בדיקות איכות הנגזרות מהמימוש. B3, שיפור משוב Angular והבונוס האבטחתי אינם חלק מהסבב. אין חסם Docker פעיל בהיקף שאומת; אין להסיק שכל כשל commit אפשרי נבדק.

## תרחישים

תנאי פתיחה לבדיקות היחידה: עובד 1 עם מכסה 20, בקשה 7 ממתינה לחופשה של יומיים ב-2026; בכל שורה משתנה התנאי המתואר. U01 משתמש בניצול 18 ובקשה 2; U02 בניצול 18 ובקשה 3; U03 בניצול 20 ובקשה 1. U09 מתחיל ללא ניצול. הפעולה היא approve. בכל דחייה עסקית נבדקים גם סטטוס ללא שינוי ואי-קריאה לשמירה. מצב אימות המסד והמערכת מצוין בנפרד בשורות P/H/UI; אין להסיק מרמת בדיקה אחת שכל מקרה ברמה אחרת נבדק.

ב-MockMvc שירות האישור מדומה: תרחישים אלה מוכיחים מיפוי נתיב, JSON ושגיאות בלבד. את הלוגיקה מוכיחה חבילת היחידה הנפרדת.


| מזהה / סוג | תרחיש ותוצאה צפויה | מצב | ראיה / פעולה שנותרה |
|---|---|---|---|
| U01 יחידה | יתרה מדויקת: אישור ושמירה | עבר | LeaveApprovalTests.rechecksCalendarBalance[1]; R; יומן ההרצה |
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
| R01 רגרסיה | 37 בדיקות B1, כולל 13 PostgreSQL | עבר | R; רשימת תרחישי B1 נשארת ב-B1_QA |
| P01 PostgreSQL | APPROVED נשמר ונקרא מחדש | טרם הורץ | approvalIsPersisted R; הורץ מול PostgreSQL |
| P02 PostgreSQL | חריגה אינה משנה PENDING במסד | טרם הורץ | insufficientBalanceDoesNotChangeStoredStatus R; הורץ מול PostgreSQL |
| P03 PostgreSQL | חריגה לאחר flush מבטלת שינוי במסד | טרם הורץ | outerFailureRollsBackFlushedApproval R; הורץ מול PostgreSQL |
| P04 PostgreSQL | שתי בקשות שונות: רק אחת מאושרת כששתיהן חורגות יחד | טרם הורץ | concurrentDifferentRequestsCannotExceedQuota R; הורץ מול PostgreSQL |
| P05 PostgreSQL | שני אישורים לאותה בקשה: הצלחה אחת ו-409 אחד | טרם הורץ | concurrentSameRequestIsApprovedOnce R; הורץ מול PostgreSQL |
| P06 PostgreSQL | ניצול 16 ושתי בקשות של 2 ימים: שתי הצלחות, שתי רשומות APPROVED וסך ניצול 20 | טרם הורץ | concurrentRequestsWithinQuotaBothSucceed R; הורץ מול PostgreSQL |
| P07 PostgreSQL / B1 | אישור מוסיף ניצול פעם אחת; אישור חוזר נדחה; יצירה של 3 ימים נדחית ושל 2 מתקבלת; עובד אחר אינו משפיע | טרם הורץ | approvalUpdatesCreationBalanceWithoutDoubleChargeOrCrossEmployeeMixing R; הורץ מול PostgreSQL |
| H01 HTTP ברשת | עובד 4: 10+8 מאושרים; בקשה 11 של 2 ימים מאושרת; ניסיון חוזר 409; שינוי סטטוס בלבד | עבר | H; גוף הצלחה כולל id=11, employeeId=4, type=0, status=1, days=2; GET ו-SQL תואמים |
| UI01 ממשק | עובד 5, ניצול 18; בקשה 17 בת יומיים מאושרת בכפתור Approve | עבר | H/uiEvidence; Approved אחרי רענון, GET חדש ו-SQL; F2 לא בוצע |
| P08 PostgreSQL — שנים | 8 קלטי שנים/מכסה זהים ל-U01–U08; רק הסטטוס המותר משתנה, שאר פרטי הבקשה נשמרים | עבר | R; calendarBalanceIsRecheckedFromDatabase[1–8] |
| P09 PostgreSQL — סוגים | 20 ימים מאושרים; מחלה וחל״ת של 3 ימים מאושרות בלי לשנות ניצול חופשה 20 | עבר | R; nonVacationCanBeApprovedWithExhaustedQuota[1–2] |
| P10 PostgreSQL — סינון וצבירה | 10+8 מאושרים; עובד אחר 20, ממתינה 20, דחויה 20, מחלה וחל״ת מאושרות 20 אינן נכללות; 2 נוספים מותרים | עבר | R; approvalFiltersAndSumsPersistedHistory |
| P11 PostgreSQL — עובדים שונים במקביל | לכל עובד ניצול 18 ובקשה 2; עובד ראשון חסום בנעילה, השני מאושר לפני שחרורה; שניהם מסתיימים ב-20 וללא ערבוב בעלות | עבר | R; differentEmployeesCanProgressIndependently; pg_blocking_pids מאמת המתנה אמיתית |
| H02 HTTP + SQL | בקשה 10 בת 3 ימים עם יתרה 2: 409; בקשה 12 בת יום אחרי אישור 11: 409; שתיהן נשארות Pending | עבר | H; excess_one_day/exhausted; גוף Not enough vacation balance |
| H03 HTTP + SQL | בקשה 13 דחויה: 409; בקשה 11 כבר מאושרת: 409; כל הרשומות ללא שינוי | עבר | H; גוף Only pending requests can be approved |
| H04 HTTP + SQL | מזהה 9223372036854775807 או ‎-1: 404; abc או 9223372036854775808: 400; אין שינוי במסד | עבר | H; 404: Leave request not found; 400: גוף JSON שגיאה של Spring עם status/error/path/timestamp |
| H05 HTTP + SQL | מכסה מנוצלת 20; מחלה 14 וחל״ת 15, 3 ימים כל אחת: 200; APPROVED נשמר | עבר | H; success JSON, GET ו-SQL; יתר פרטי כל הרשומות לא השתנו |

## בדיקות המקביליות שאומתו

LeaveApprovalPostgresTests כולל כעת 19 בדיקות PostgreSQL. כל מבחן יוצר עובד נפרד. בדיקות המקביליות מחזיקות נעילת עובד בטרנזקציה שלישית ומפעילות שתי טרנזקציות worker נפרדות. כל worker מפרסם pg_backend_pid לפני האישור. לפני שחרור הנעילה נדרש ששני ה-PID שונים ושהפונקציה pg_blocking_pids מדווחת ששניהם ממתינים לנעילה. הבדיקה אינה מסתפקת בזמן שחלף או בהפעלת שני threads. לאחר שחרור נבדקות התשובות והמצב הסופי במסד, גם כששתי הבקשות יחד בתוך המכסה.

ההמתנה לחיווי מוגבלת לחמש שניות, עם בדיקה כל 25ms; לכל worker מוגדר lock_timeout מקומי לטרנזקציית הבדיקה בלבד (10s), וה-executor נסגר ב-finally. אין שינוי הגדרות ייצור. תיעוד הפונקציה ששימשה לתכנון: https://www.postgresql.org/docs/16/functions-info.html#FUNCTIONS-INFO-SESSION-TABLE.

קיימת ראיית הרצה R לכל הבדיקות הללו; תיאום החפיפה, תשובות הפעולות והמצב הסופי עברו בפועל. בפרט, בדיקת סדר קריאות ב-mocks אינה ראיה לנעילה אפקטיבית. בדיקת rollback מזריקה כשל אחרי flush בטרנזקציה חיצונית; היא אינה מדמה כל סוג כשל commit.

## סגירה והמשך

לא נמצא כשל יישום חדש בהרצה הראשונה או לאחר הרחבת הכיסוי. לא הוחלשו בדיקות, לא דולגו תרחישים ולא שונה חוזה API. 200 מסמן אישור, 404 משאב חסר, 409 התנגשות בסטטוס או ביתרה, 400 כשל המרת מזהה — בחירות חוזה ולא מספרים שקבע המעסיק. גוף ותוצאה בפועל ב-H.

טרנזקציית השירות מתחילה בכניסה דרך Spring ומסתיימת ב-commit/rollback לאחר החזרה. נשמר רק מזהה בעל הבקשה לפני הנעילה; Employee ננעל PESSIMISTIC_WRITE ב-READ_COMMITTED, ורק אז נקראים הבקשה והניצול. כך שני אישורים לאותו עובד מסודרים על אותה רשומה, גם לבקשות שונות. בדיקות נפרדות הוכיחו תוצאה נכונה ומניעת שימוש ביתרה ישנה. עובדים שונים אינם חולקים אותה נעילה. SQL חיצוני העוקף את המסלול, מדיניות retries וזמני תגובה תחת עומס אינם מוגנים/מאומתים במסגרת זו.

הרצה מאומתת משורש המאגר: טעינת scripts/Use-LocalTools.ps1, הגדרת DOCKER_HOST ל-npipe:////./pipe/dockerDesktopLinuxEngine לתהליך, ואז מתוך backend:

```powershell
mvn.cmd -o '-Dmaven.repo.local=../.local-tools/m2' '-Dapi.version=1.44' test
```

מדריך ידני מעודכן: [B2_MANUAL_CHECK](B2_MANUAL_CHECK.md). בעדכון ההמשך, באישור המשתמשת, נוסח B2 שולב ב-DECISIONS תוך שימור עריכת התיעוד המקבילה. הבדיקות והראיות נסקרו לקראת קומיט מורשה. לא בוצעו commit או push. אין מעבר ל-B3.

## היסטוריית האימות

בביקורת המוקדמת עברו 51 בדיקות מבודדות; בדיקות המסד טרם הורצו אז. ראיה היסטורית: [B2-review-run.json](evidence/B2-review-run.json). הבדיקה שהסתמכה על timeout בלבד חוזקה לחיווי נעילות PostgreSQL. לאחר חזרת הסביבה עברו 71 בדיקות, ולאחר הרחבת הכיסוי עברו 83. המצב הנוכחי הוא הטבלה למעלה; אין חסם סביבתי פעיל מוכח בהיקף זה.

## סקירת קבצים ו-Git לפני שמירה

ענף העבודה הנפרד הוא `codex/b2-verified`, שנוצר מ-8ceb793 ושומר את היסטוריית B1 ומימוש B2 הקודם 934c168. הרחבת בדיקות B2 והתיעוד נבדקו להכנסה לקומיט הפרסום באישור המשתמשת. הקומיט המעורב 3ecab8d כבר הוחלף בתיאום ב-8ceb793; הוא אינו HEAD ואין עוד צורך להפרידו כפעולה עתידית.

לשמירה: הרחבת LeaveApprovalPostgresTests, DECISIONS, QA_REPORT, B2_QA, B2_MANUAL_CHECK, TASK_TRACKER, עדכון B2 ב-BASELINE_RUN והוצאת אבחון Windows הישן לארכיון מקומי ושלוש ראיות B2 (live-http, postgres-run, review-run), והחרגת output ב-.gitignore. הטיוטה שכבר מוזגה הועברה לארכיון .local-run ואינה תוצר הגשה. output מכיל PDF של B1 שנשמר מקומית ואינו שייך לקומיט B2.

מקורות Java הושוו ל-SHA256 של הרצת 83 הבדיקות ונמצאו זהים; עדכוני תיעוד אינם שינוי במימוש. בדיקות Mock נשמרו כבדיקות יחידה וחוזה, לצד PostgreSQL. אין צורך ב-GitHub Discussions, PR או סרטון לפי README. חלק ג כולל prompts אמיתיים ודוגמה להצעת AI שתוקנה ב-DECISIONS; בונוס SQL injection עדיין לא תוקן ולא נטען שהושלם.

גבול האישור: B2 עומד בדרישות המימוש והאימות שנבדקו; אין הבטחת היעדר כל באג או השלמה של B3/F1–F3. יש לסקור את רשימת הקומיטים מול ענף היעד לפני push עתידי, משום שענף B2 כולל היסטוריה מקומית קודמת ולא רק את השינויים שטרם נשמרו.
