# הכנת סביבת המקור — 2026-09-09

## גבולות השלב
קליטת קוד המקור, הכנת כלים, בנייה והרצת הבדיקות הקיימות בלבד. אין תיקון באגים של המטלה.

## קליטה
- הקבצים נמצאו כבר בתיקייה בתחילת השלב. 37 קובצי המטלה שאינם `.gitignore` הושוו ב-SHA256 לארכיון ונמצאו זהים; `.gitignore` מכיל מיזוג החרגות.
- `.git` שבארכיון לא הועתק מעל המאגר הקיים. הארכיון המקורי נשאר ב-`D:\Downloads\java-home-assignment-main.zip`.
- README המטלה נשמר ללא שינוי. README ההכנה הקודם שוחזר מהיסטוריית Git אל `docs/WORKSPACE_SETUP.md`.
- נקודת מקור מקומית: `455571fc8e3b6f99f31fd2011ac3ee0966b6ea90`.
- נקראו הוראות AI ושיתוף; נמצאו פרטי חיבור דוגמתיים למסד המקומי. לא נמצאו סודות נוספים בבדיקת התוכן שבוצעה. לא בוצעה העלאה מרוחקת בשלב זה.

## כלים מקומיים לפרויקט
נשמרו תחת `.local-tools` המוחרגת מ-Git, ללא שינוי PATH קבוע של Windows:
- Temurin JDK 21.0.12.1+1
- Maven 3.9.16
- Node.js 20.20.2

ארכיוני הכלים אומתו מול checksums מהמקורות הרשמיים. חתימת מתקין Docker אומתה, אך התקנתו נכשלה.

Java משתמש בתעודות האמון של Windows. עבור Node יוצאו תעודות ציבוריות ממאגרי Root של Windows לקובץ מקומי מוחרג; לא בוטל אימות TLS.

## שימוש בכלים
מתוך שורש הפרויקט, ב-PowerShell:

```powershell
. .\scripts\Use-LocalTools.ps1
cd backend
mvn.cmd '-Dmaven.repo.local=../.local-tools/m2' test
```

במסוף נוסף, מתוך שורש הפרויקט:

```powershell
. .\scripts\Use-LocalTools.ps1
cd frontend
npm.cmd ci --no-audit --no-fund
npm.cmd run build
npm.cmd test -- --watch=false --browsers=ChromeHeadless
npm.cmd start
```

אלה פקודות העבודה; תוצאות האימות מסוכמות להלן, ואין להסיק מרשימת פקודות שכולן הצליחו.

## חסמי מערכת שאותרו
- Docker Installer דיווח על 16 MiB פנויים בכונן C, כאשר הפריסה הראשונית בלבד דורשת 576 MiB. בבדיקה נוספת הכונן כמעט ללא מקום פנוי. אין זו דרישת המקום הכוללת ל-Docker ולמסד הנתונים.
- WSL אינו מותקן. ניסיון בקשת הרשאת מנהל הסתיים בהודעת Windows שהפעולה בוטלה על ידי המשתמש. לא הופעל מחדש ללא תשובה לשאלת ההמשך.
- לא בוצעה מחיקה של קובצי משתמש או הפעלה מחדש של המחשב.
- נדרש מקום פנוי והשלמת WSL/Docker לפני הפעלת PostgreSQL ובדיקות Testcontainers.

## תוצאות
| בדיקה | תוצאה | ראיה מקומית |
|---|---|---|
| התקנת תלויות Angular לפי קובץ הנעילה | עבר: 870 חבילות; קובץ הנעילה לא שונה | `.local-run/frontend-install.log` |
| בניית Angular | עבר | `.local-run/frontend-build.log` |
| בדיקת Angular הקיימת | עבר: 1 מתוך 1, exit code 0 | `.local-run/frontend-test.log` |
| קומפילציית Java וקוד הבדיקות | עבר במסגרת `mvn test` | `.local-run/backend-test.log` |
| בדיקת השרת הקיימת | שגיאת הכנה: `Could not find a valid Docker environment`; לא אומתה ההתנהגות העסקית | `.local-run/backend-test.log`, `backend/target/surefire-reports` |
| יצירת JAR עם `-DskipTests` | עבר; אינו מעיד שהבדיקות עברו | `.local-run/backend-package.log` |
| שרת פיתוח Angular | הופעל; HTTP 200 ב-`http://127.0.0.1:4200`, מסמך מכיל `app-root` | `.local-run/frontend-server.log` |
| PostgreSQL, API ובדיקה מלאה דרך המסך | חסום: Docker/WSL ומקום פנוי | יומן מתקין Docker |

בדיקת Angular דיווחה גם אזהרת סגירה של Chrome לאחר הצלחת הבדיקה. אין בכך כישלון הבדיקה, אך זו מגבלת ניקוי התהליך שנצפתה.

## המשך נדרש לפני סיום שלב ההכנה
1. לפנות מקום בכונן C בהסכמת המשתמשת; לא בוצעה מחיקה.
2. להשלים WSL בהרשאת מנהל ו-Docker. אם Windows דורש הפעלה מחדש, המשתמשת תבצע אותה בזמן מתאים.
3. לאמת `docker info`, להפעיל `docker compose up --build -d`, ואז לבדוק PostgreSQL, API ו-Swagger.
4. להריץ מחדש רק את בדיקת השרת החסומה ולבדוק את המסך עם נתוני המקור.
5. לעדכן דוח זה. אין לעבור לתיקון B1 לפני סיכום שלב ההכנה עם המשתמשת.

לא שונו קובצי Java/Angular, הגדרות הבנייה או קובץ הנעילה המקוריים בשלב זה.
