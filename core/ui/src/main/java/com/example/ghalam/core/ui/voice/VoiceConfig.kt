package com.example.ghalam.core.ui.voice

/**
 * تنظیمات ضبط صدا برای هر دو اپ (دانش‌آموز و معلم) — فقط همین‌جا را تغییر دهید.
 *
 * فرمت: AAC داخل M4A (audio/mp4). روی همهٔ گوشی‌ها، مرورگرها، iOS و ویندوز پخش می‌شود.
 * حجم تقریبی: ۳۲ kbps ≈ ۲۴۰ کیلوبایت در دقیقه (۱۰ دقیقه ≈ ۲٫۴ مگابایت).
 */
object VoiceConfig {
    /** حداکثر مدت یک ضبط (میلی‌ثانیه). برای تغییر سقف، فقط این عدد را عوض کنید. */
    const val MAX_DURATION_MS: Int = 10 * 60 * 1000

    /** کوتاه‌تر از این مدت، ضبط نادیده گرفته می‌شود (لمس اشتباهی). */
    const val MIN_DURATION_MS: Long = 700L

    /** نرخ نمونه‌برداری مناسب گفتار. */
    const val SAMPLE_RATE_HZ: Int = 16_000

    /** بیت‌ریت: بیشتر = کیفیت و حجم بیشتر (مثلاً ۲۴_۰۰۰ کم‌حجم‌تر، ۴۸_۰۰۰ باکیفیت‌تر). */
    const val BITRATE_BPS: Int = 32_000

    /** تک‌کاناله (برای گفتار کافی است). */
    const val CHANNELS: Int = 1

    const val EXTENSION: String = "m4a"
    const val MIME: String = "audio/mp4"

    /** فایل‌های ضبط‌شده‌ای که بیشتر از این مدت مانده‌اند پاک می‌شوند. */
    const val KEEP_TEMP_FILES_MS: Long = 2L * 24 * 60 * 60 * 1000
}
