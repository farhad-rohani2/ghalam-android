# تغییرات (Changelog)

نسخهٔ هر اپ در `build.gradle.kts` همان ماژول است. جدیدترین ورودی بالا قرار می‌گیرد.

| اپ | applicationId | نسخه (کد) |
|---|---|---|
| student | `ir.razzar.ghalam.student` | 2.0 (4) |
| teacher | `ir.razzar.ghalam.teacher` | 0.1 (1) |

## 2026-11-26 — ضبط صدا و بازخورد صوتی (student · teacher)

نسخهٔ اپ‌ها در `build.gradle.kts` تغییری نکرد.

### core:ui
- پوشهٔ `voice` (مشترک بین دو اپ): `VoiceConfig`، `VoiceRecorder` (+ `VoiceStorage`)، `VoiceRecorderSheet`، `VoicePlayerBar`، `VoiceIcons`
- فرمت AAC داخل M4A (`audio/mp4`)؛ تک‌کاناله، ۱۶ کیلوهرتز، ۳۲ kbps (≈ ۲٫۴ مگابایت برای ۱۰ دقیقه)
- همهٔ تنظیمات فقط در `VoiceConfig` است؛ سقف مدت `MAX_DURATION_MS` (فعلاً ۱۰ دقیقه)
- پنل ضبط: تایمر، موج زنده، مکث/ادامه، دور انداختن، پیش‌نمایش با سرعت ۱×/۱٫۵×/۲×؛ ضبط ناقص با بستن پنل پاک می‌شود
- وابستگی `activity-compose` به `core:ui` اضافه شد؛ مجوز `RECORD_AUDIO` در مانیفست هر دو اپ

### student
- دکمهٔ «صدا» کنار «دوربین» و «گالری» در ارسال پاسخ؛ ارسال از صف آفلاین همیشگی (`Outbox`)
- فایل M4A به‌جای ویدیو تشخیص داده می‌شود (`MediaManager`)
- رفع: برای تکلیفی که هنوز رکورد Answer ندارد، پیام «امکان ارسال پاسخ…» دیگر نمی‌آید
  - `AnswerSlot.answerId` می‌تواند null باشد؛ آپلود اول با `modelId=null` و `questionId` فرستاده می‌شود
  - در `pending_uploads` مقدار `answerId = 0` یعنی «رکورد هنوز نیست» (بدون تغییر schema)
  - فایل‌های بعدی پیش از آپلود شناسهٔ ساخته‌شده را از سرور می‌گیرند تا چند Answer جدا ساخته نشود
  - ⚠️ رفتار سرور (ساخت رکورد با اولین آپلود) حدسی است و باید روی سرور تست شود

### teacher
- ساخت و ویرایش تکلیف: دکمهٔ «🎙 ضبط صدا»؛ ضبط مثل پیوست عادی با ذخیرهٔ تکلیف ارسال می‌شود
- نمایشگر فایل: پخش صدا درون‌برنامه؛ M4A به‌جای ویدیو تشخیص داده می‌شود (`TeacherMedia`، `MediaKinds`)
- بازخورد صوتی برای هر دانش‌آموز و برای کل کلاس (endpointهای `feedback/media` و `feedback/general/media`)
  - ضبط در صف آفلاین Room ذخیره و با اتصال اینترنت توسط `SyncEngine` آپلود می‌شود (⏳ در صف)
  - خطای ۴۰۳/۴۰۴/۴۰۵/۴۱۳/۴۲۲ با دکمهٔ «ارسال دوباره» در صف می‌ماند؛ خطای شبکه خودکار تکرار می‌شود
  - `FeedbackMediaQueue`، `QueuedMediaEntity` و `MediaQueueDao`؛ دیتابیس نسخهٔ ۳ با `MIGRATION_2_3` (جدول `feedback_media_queue`)
  - پس از آپلود، مبنای نسخهٔ بازخورد متنی پیش‌بینی می‌شود (`advanceFeedbackBase`) تا «تعارض» کاذب پیش نیاید
  - خروج از حساب فایل‌های صف را هم پاک می‌کند
- فایل‌های غیرصوتی بازخورد (از پنل وب) نمایش، دانلود، باز کردن و حذف می‌شوند
- `FeedbackVoiceSection` و `FeedbackMediaHolder` مشترک بین صفحهٔ دانش‌آموز و صفحهٔ تکلیف

### کارهای باز
- هنوز Build و تست روی گوشی انجام نشده (ضبط، آپلود، صف، migration)
- سرور: routeهای `feedback/*/media` باید روی سرور اصلی دیپلوی باشند (۴۰۵ دیده شد)
- student: همگام‌سازی فهرست و جزئیات با وب (کش قدیمی باعث «ارسال نشده»ِ اشتباه می‌شود؛ پیشنهاد: تازه‌سازی هوشمند و سپس `changes?since`)
- student: نمایش و پخش بازخورد صوتی معلم (نیازمند endpoint سمت دانش‌آموز)
- teacher: حذف فایل بازخورد و فهرست فایل‌های سرور فقط آنلاین‌اند

## 2026-11-22 — teacher 0.1 (offline review)

### teacher
- لایهٔ آفلاین کامل شد: Room برای تکالیف ۳۰ روز اخیر، وضعیت تحویل، پاسخ‌ها و بازخوردها
- `SyncEngine` / `SyncScheduler` / `SyncStatus` / `SyncPrefs`: همگام‌سازی هر ۳ ساعت، هنگام باز شدن اپ، با دکمهٔ 🔄 و هنگام بازگشت اینترنت
- `TeacherRepository` و `TeacherAuthService`: ورود به API واقعی و نمایش پیام خطای سرور
- صف بازخورد آفلاین: ذخیرهٔ محلی (⏳) و ارسال خودکار با اتصال (✅)؛ حل تعارض نسخهٔ سرور/من
- ذخیرهٔ خودکار تغییرات هنگام خروج از صفحه
- دانلود خودکار فایل‌های پاسخ فقط روی Wi‑Fi تا ۱۰ مگابایت؛ بزرگ‌تر با دکمهٔ دانلود
- نمایش عکس درون صفحه و باز کردن بقیهٔ فایل‌ها با برنامهٔ خارجی
- `NetworkMonitor` برای تشخیص بازگشت اینترنت
- `ApiErrors` و `Persian` برای پیام‌های خطا و نمایش اعداد/متن فارسی
- `TeacherNav` و صفحه‌های `TeacherHomeScreen`، `TaskScreen`، `StudentScreen` با ViewModelهای مربوطه
- `MainActivity` و `TeacherApp` (بدون تغییر در `MainActivity` نسبت به اسکلت اولیه)

### تفاوت‌های عمدی با student
- خطای 401 دیتابیس را پاک نمی‌کند (حفظ بازخوردهای ارسال‌نشده)؛ پاک‌سازی فقط با خروج دستی (با هشدار) یا ورود معلم دیگر
- بدون `fallbackToDestructiveMigration`؛ هر تغییر schema بعدی نیازمند `Migration` واقعی است

### کارهای باز (teacher)
- ساخت و ویرایش تکلیف
- گزارش‌ها و پیام‌ها
- بازخورد کلی تکلیف
- صفحهٔ تنظیمات فایل (مقادیر فعلاً در `SyncPrefs` ثابت‌اند)
- نمایشگر PDF داخل اپ
- رفع خطاهای کامپایل احتمالی پس از اولین Build (وابستگی‌های Compose/Room در محیط اولیه نبودند)

## 2026-11-20 — student 2.0 · teacher 0.1

### ساختار پروژه
- پروژه چندماژوله شد: `core:network`، `core:ui`، `student`، `teacher`
- ماژول `app` به `student` منتقل شد و با لایه‌بندی یکسان مرتب شد: `data/api`، `data/model`، `data/local`، `data/paging`، `data/repository`، `ui/screens`، `ui/components`، `ui/viewmodel`، `util`
- ماژول قدیمی `app/` و اسکریپت‌های مهاجرت حذف شدند

### core:network
- `ApiClient` و `ApiConfig`: آدرس پایه (`https://ghalam.razzar.ir/`)، نام فایل prefs توکن و قاعدهٔ 401 پارامتری شدند
- `SessionManager` با callback `onSignedOut` (هر اپ دادهٔ خودش را پاک می‌کند)
- `TokenStore`، `Errors`، `tokenOrNull`
- احراز هویت برای دو روش: `Credentials.NationalCode` و `Credentials.UsernamePassword` با `AuthService` و `AuthRepository`

### core:ui
- تم و فونت وزیرمتن، `Components` مشترک، `OfflineBanner`
- `createAuthImageLoader` و `CredentialsLoginScreen` (ورود با نام کاربری و رمز)

### student 2.0
- `applicationId` از `com.example.ghalam` به `ir.razzar.ghalam.student` تغییر کرد
  - اپ جدید نصب می‌شود (نه آپدیت) و ورود دوباره لازم است
- دیتابیس Room همان `ghalam.db` است و در خود اپ می‌ماند
- ورود با کد ملی از مسیر `Graph.auth` انجام می‌شود
- `LocalDb.kt` و `GhalamApi.kt` به چند فایل تقسیم شدند و `TaskCard` به `ui/components` رفت

### teacher 0.1
- اسکلت اپ: ورود با نام کاربری و رمز و صفحهٔ خانهٔ خالی
- ⚠️ endpointهای ورود فعلاً placeholder هستند (`TeacherApi.kt`)، ورود واقعی کار نمی‌کند
- دیتابیس Room هنوز ندارد

### کارهای باز
- مسیر و بدنهٔ واقعی ورود معلم (اکنون به API واقعی وصل است؛ فقط تأیید نهایی پس از Build)
- ساخت/ویرایش تکلیف، گزارش‌ها و پیام‌ها، بازخورد کلی تکلیف، صفحهٔ تنظیمات فایل، نمایشگر PDF
- اپ مدیر (`manager`)
- استخراج `core:cache` برای جدول مشترک `cached_json` (هنگام نیاز معلم)
- نتیجهٔ اولین Build معلم و تنظیم بازهٔ ۳۰ روزه و سقف ۱۰ مگابایت بر اساس تعداد واقعی تکالیف/دانش‌آموزان
