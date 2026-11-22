# اپ معلم (teacher)

ماژول اپ معلم «قلم» برای بازبینی تکالیف دانش‌آموزان با پشتیبانی کامل از حالت آفلاین.

- `applicationId`: `ir.razzar.ghalam.teacher`
- نسخه: 0.1 (1) — مطابق `teacher/build.gradle.kts`

## قابلیت‌ها

- **ورود** با نام کاربری و رمز از API واقعی؛ پیام خطای سرور نمایش داده می‌شود.
- **همگام‌سازی پس‌زمینه** در چهار موقعیت:
  - هر ۳ ساعت (در صورت وجود اینترنت)
  - هنگام باز کردن اپ
  - با دکمهٔ 🔄 در رابط کاربری
  - وقتی اینترنت برمی‌گردد
- تکالیف **۳۰ روز اخیر**، وضعیت تحویل، پاسخ‌ها و بازخوردهای موجود را در Room ذخیره می‌کند.
- صفحه‌ها: فهرست تکالیف، فهرست دانش‌آموزان هر تکلیف (با فیلتر و جستجو)، صفحهٔ پاسخ + فرم بازخورد با «قبلی/بعدی». همه از Room خوانده می‌شوند و آفلاین کار می‌کنند.
- **بازخورد آفلاین**: «ذخیره» فقط روی گوشی می‌نویسد (⏳) و با اتصال اینترنت ارسال می‌شود. در صورت تغییر نسخهٔ سرور، معلم بین «نسخهٔ من» و «نسخهٔ سرور» انتخاب می‌کند. خروج از صفحه، تغییرات را خودکار ذخیره می‌کند.
- **فایل‌ها**: پاسخ‌ها فقط روی Wi‑Fi و تا ۱۰ مگابایت خودکار دانلود می‌شوند؛ فایل بزرگ‌تر با دکمهٔ دانلود. عکس‌ها درون صفحه و بقیه با برنامهٔ دیگر باز می‌شوند.

## تفاوت‌های مهم با اپ دانش‌آموز

- خطای 401 دیتابیس را پاک نمی‌کند تا بازخوردهای ارسال‌نشده از بین نروند. داده فقط با خروج دستی (با هشدار) یا ورود معلم دیگر پاک می‌شود.
- دیتابیس معلم `fallbackToDestructiveMigration` ندارد؛ هر تغییر schema بعدی باید `Migration` واقعی داشته باشد.

## هنوز پیاده نشده

- ساخت و ویرایش تکلیف
- گزارش‌ها و پیام‌ها
- بازخورد کلی تکلیف
- صفحهٔ تنظیمات فایل (مقادیر فعلاً در `SyncPrefs` ثابت‌اند)
- نمایشگر PDF داخل اپ

## ساختار

```
teacher/src/main/java/com/example/ghalam/teacher/
├── data/
│   ├── api/          # Dtos.kt, TeacherApi.kt
│   ├── local/        # TeacherDatabase.kt, Daos.kt, Entities.kt, Mappers.kt
│   ├── media/        # MediaKinds.kt, TeacherMedia.kt
│   ├── repository/   # TeacherRepository.kt, TeacherAuthService.kt, FeedbackModels.kt
│   ├── sync/         # SyncEngine.kt, SyncScheduler.kt, SyncStatus.kt
│   ├── Graph.kt
│   └── SyncPrefs.kt
├── navigation/       # TeacherNav.kt
├── ui/
│   ├── components/   # Common.kt
│   ├── screens/      # TeacherHomeScreen.kt, TaskScreen.kt, StudentScreen.kt
│   └── viewmodel/    # HomeViewModel.kt, TaskViewModel.kt, StudentViewModel.kt, TeacherSessionViewModel.kt
├── util/             # ApiErrors.kt, NetworkMonitor.kt, Persian.kt
├── MainActivity.kt
└── TeacherApp.kt
```

## تست آفلاین

1. یک بار همگام‌سازی کن.
2. حالت هواپیما را روشن کن.
3. بازخورد بده (وضعیت ⏳).
4. اینترنت را وصل کن و ببین ⏳ به ✅ تبدیل می‌شود.

## نکات ساخت

- وابستگی‌های Compose و Room در محیط توسعهٔ اولیه نبودند؛ پس ممکن است یک یا دو خطای کامپایل باقی مانده باشد. پس از Sync و Build، خروجی خطاها را ارسال کن.
- `MainActivity` دست‌نخورده باقی مانده است.
