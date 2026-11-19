# تکالیف (GhalamTasks)

Android Studio -> Open -> پوشه‌ی GhalamTasks -> Sync -> Run.

## چه چیزی به دانش‌آموز نشان داده می‌شود
فقط چیزهایی که معلم در فرم «تکلیف» ثبت می‌کند:
درس، کلاس، عنوان/توضیح تکلیف، مهلت تحویل، پیش‌نیازها، «توضیح / پاسخ مورد انتظار»،
فایل‌های پیوست و لینک‌های اینترنتی. بقیه (شناسه‌ها، ستاره‌ها، تاریخ بروزرسانی، تصویر کارت، عنوان exam) پنهان است.

## فیلترها
از `filters-meta` خوانده می‌شوند و مثل Filter.vue بر اساس `filter_settings[0].type` رندر می‌شوند:
Select/Radio/TagInline = تک‌انتخابی؛ Checkbox/Multiselect/Tags/TagsInline = چندانتخابی.
Range و Colors پشتیبانی نمی‌شوند. جستجو با `filter[title]` ارسال می‌شود.

فونت: Vazirmatn (OFL).
