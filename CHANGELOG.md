# تغییرات (Changelog)

نسخهٔ هر اپ در `build.gradle.kts` همان ماژول است. جدیدترین ورودی بالا قرار می‌گیرد.

| اپ | applicationId | نسخه (کد) |
|---|---|---|
| student | `ir.razzar.ghalam.student` | 2.0 (4) |
| teacher | `ir.razzar.ghalam.teacher` | 0.1 (1) |

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
- مسیر و بدنهٔ واقعی ورود معلم
- اپ مدیر (`manager`)
- استخراج `core:cache` برای جدول مشترک `cached_json` (هنگام نیاز معلم)
