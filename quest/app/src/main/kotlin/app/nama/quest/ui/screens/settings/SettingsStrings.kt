package app.nama.quest.ui.screens.settings

import app.nama.core.Lang

/**
 * Settings strings only the headset needs, in the pattern of ui/Extra.kt. The shared table's
 * versions of some of these say "this Mac", which would be wrong here.
 */
enum class SX(val en: String, val fa: String, val ar: String) {
  keySaved("Saved on this headset", "روی همین هدست ذخیره شد", "حُفظ على هذه النظارة"),
  yourNameHint(
      "Shown on your profile when your 30nama profile is private. Stays on this headset.",
      "وقتی پروفایل شما در ۳۰نما خصوصی است، این نام در پروفایل می‌آید. فقط روی همین هدست می‌ماند.",
      "يظهر في ملفك الشخصي عندما يكون ملفك في 30nama خاصًا. يبقى على هذه النظارة فقط.",
  ),
  comfort("Comfort", "راحتی", "الراحة"),
  reduceMotionHint(
      "Keeps the pictures behind titles still and leaves their trailers off.",
      "تصویرهای پشت عنوان‌ها ثابت می‌مانند و تریلرشان پخش نمی‌شود.",
      "تبقى الصور خلف العناوين ثابتة ولا تُشغَّل إعلاناتها.",
  ),
  guestLine("You're browsing as a guest", "به‌عنوان مهمان وارد شده‌اید", "أنت تتصفح كضيف"),
  guestHint(
      "Sign in to watch and to keep your lists.",
      "برای تماشا و نگه داشتن فهرست‌هایتان وارد شوید.",
      "سجّل الدخول للمشاهدة وللاحتفاظ بقوائمك.",
  ),
  audioHint("The headset has two speakers", "هدست دو بلندگو دارد", "في النظارة سماعتان"),
  fromNextVideo("from the next video", "از ویدیوی بعدی", "من الفيديو التالي"),
  openingBrowser("Opening the browser…", "مرورگر باز می‌شود…", "جارٍ فتح المتصفح…"),
  ;

  operator fun invoke(lang: Lang): String =
      when (lang) {
        Lang.fa -> fa
        Lang.ar -> ar
        else -> en
      }
}
