GhalamStudent/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── example/
│   │       │           └── ghalam/
│   │       │               ├── data/
│   │       │               │   ├── GhalamApi.kt
│   │       │               │   ├── Graph.kt
│   │       │               │   ├── TaskDetail.kt
│   │       │               │   ├── TaskPagingSource.kt
│   │       │               │   └── TokenStore.kt
│   │       │               ├── ui/
│   │       │               │   ├── DetailViewModel.kt
│   │       │               │   ├── LoginScreen.kt
│   │       │               │   ├── TaskDetailScreen.kt
│   │       │               │   ├── TaskListScreen.kt
│   │       │               │   └── TasksViewModel.kt
│   │       │               ├── util/
│   │       │               │   ├── Errors.kt
│   │       │               │   └── ImageUtil.kt
│   │       │               ├── GhalamApp.kt
│   │       │               └── MainActivity.kt
│   │       ├── res/
│   │       │   └── xml/
│   │       │       └── file_paths.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── README.md
├── build.gradle.kts
├── gradle.properties
├── local.properties
└── settings.gradle.kts


# تکالیف (GhalamTasks)

1. پوشه را در Android Studio (Koala یا جدیدتر) باز کنید و Gradle Sync بزنید.
2. اجرا روی گوشی/شبیه‌ساز.

## امکانات
- ورود با کد ملی (`sections/task/login`) و خروج (`auth/logout`)؛ ورود با توکن دستی هم هست.
- لیست تکالیف با فیلتر درس و مهلت، صفحه‌بندی.
- صفحه‌ی جزئیات تکلیف (`GET sections/task/{uuid}`).
- ارسال عکس پاسخ از دوربین یا گالری (`upload-media`)؛ عکس قبل از ارسال کوچک و فشرده می‌شود (حداکثر ۱۶۰۰ پیکسل).
- حذف عکس (`remove-media/{uuid}`) و نمایش تمام‌صفحه با زوم.
- توکن به‌صورت رمزنگاری‌شده ذخیره می‌شود؛ اگر سرور 401 بدهد خودکار به صفحه‌ی ورود برمی‌گردد.
