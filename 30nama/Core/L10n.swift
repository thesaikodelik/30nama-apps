import Foundation

/// Interface strings. The language is an in-app setting (the catalogue itself is served in the
/// chosen language), so these live in code rather than in a `.strings` bundle that follows the
/// system locale.
enum S: String, CaseIterable {
    case home, search, browse, settings, movies, series, anime, movie
    case signIn, scanTitle, step1, step2, step3, orEnterCode, waiting, expired, codeFailed, guest, newCode, welcome
    case loading, error, tryAgain, retry, noConnection
    case continueWatching, newReleases, suggested, top10, featured
    case cloudRatings, cloudSynced, cloudSyncing, cloudSignedOut, cloudFailed
    case watchlist, favorites, yourTaste, howYouRate, subscriptionUntil, daysLeft
    case play, resume, details, episodes, season, seasonsCount, episode, nowPlaying, cast
    case persianSub, minLeft, min, free, soon
    case loginToStream, loginToWatch, noStream, comingSoonNote
    case popularNow, topResults, nothingFor, searchPrompt
    case categories, genres, newest, popular, updated, results
    case account, language, signOut, signInQR, guestAccount, expires, accountType
    case playback, display, about, defaultSubs, subSize, autoplay, quality, qualityBest, qualityAuto, reduceMotion
    case on, off, small, medium, large, sample
    case profile, yourRating, forYou, ratedCount, averageRating, noRatingsYet, rateHint, startWithThese
    case subColor, subBackground, white, yellow, cyan, green, bgNone, bgHalf, bgSolid, bgBlurHalf, bgBlur
    case version, unofficial, apiHost
    case skipIntro, skipRecap, nextEpisode, subtitles, english, persian
    case audio, audioStereo, audioSurround, audioOriginal
    case speed, subtitleTiming, subtitleFile, pictureInPicture, alwaysOnTop, fullScreen, reset
    case subFailed, playError
    case signOutTitle, signOutBody, cancel
    case director, writer, creator, network, country, originalLanguage, status, storyline, information
}

struct Loc: Equatable {
    let lang: Lang

    func callAsFunction(_ key: S) -> String {
        (lang == .fa ? Loc.fa[key] : lang == .ar ? Loc.ar[key] : Loc.en[key]) ?? Loc.en[key] ?? key.rawValue
    }

    /// Digits in the interface language.
    func num(_ s: String) -> String { lang == .fa ? Fmt.persianDigits(s) : s }
    func num(_ n: Int) -> String { num(String(n)) }

    func title(forSection key: String) -> String {
        switch key {
        case "continue": return self(.continueWatching)
        case "for_you": return self(.forYou)
        case "new_releases": return self(.newReleases)
        case "suggested": return self(.suggested)
        case "top_10": return self(.top10)
        case "movies": return self(.movies)
        case "series": return self(.series)
        case "anime": return self(.anime)
        default: return key
        }
    }

    func kind(_ kind: Post.Kind) -> String {
        switch kind {
        case .movie: return self(.movie)
        case .series: return self(.series)
        case .anime: return self(.anime)
        }
    }

    /// "Series · 4 seasons"
    func kindLine(_ post: Post) -> String {
        var s = kind(post.kind)
        if post.isSeries, let n = post.seasons, n > 0 {
            s += " · " + (lang == .en ? "\(n) \(n == 1 ? "season" : self(.seasonsCount))" : "\(num(n)) \(self(.seasonsCount))")
        }
        return s
    }

    /// The account's plan as 30nama names it: "golden" -> "طلایی".
    func accountType(_ raw: String) -> String {
        let fa = ["golden": "طلایی", "gold": "طلایی", "silver": "نقره‌ای", "bronze": "برنزی", "red": "قرمز", "free": "رایگان"]
        let ar = ["golden": "ذهبي", "gold": "ذهبي", "silver": "فضي", "bronze": "برونزي", "red": "أحمر", "free": "مجاني"]
        switch lang {
        case .fa: return fa[raw.lowercased()] ?? raw
        case .ar: return ar[raw.lowercased()] ?? raw
        case .en: return raw.prefix(1).uppercased() + raw.dropFirst()
        }
    }

    /// "Subscription until 2028-07-15 · 641 days left", in the calendar of the interface language.
    func subscriptionLine(_ user: User, now: Date = Date()) -> String? {
        guard let day = (lang == .fa ? user.expiryLocal.map { $0.replacingOccurrences(of: "-", with: "/") } : nil) ?? user.expiry else { return nil }
        var line = "\(self(.subscriptionUntil)) \(num(day))"
        if let days = user.daysLeft(now: now) { line += " · \(num(days)) \(self(.daysLeft))" }
        return line
    }

    /// "S2 · E5" / "فصل ۲ · قسمت ۵"
    func episodeTag(season: Int, episode: String) -> String {
        switch lang {
        case .fa: return "فصل \(num(season)) · قسمت \(num(episode))"
        case .ar: return "الموسم \(season) · الحلقة \(episode)"
        case .en: return "S\(season) · E\(episode)"
        }
    }

    static let en: [S: String] = [
        .home: "Home", .search: "Search", .browse: "Browse", .settings: "Settings",
        .movies: "Movies", .series: "Series", .anime: "Anime", .movie: "Movie",
        .signIn: "Sign in", .scanTitle: "Scan with your phone", .step1: "Open the camera on your phone",
        .step2: "Scan the code", .step3: "Confirm on 30nama.com", .orEnterCode: "Or go to 30nama.com/auth and enter",
        .waiting: "Waiting for scan…", .expired: "Code expired", .codeFailed: "Could not get a code",
        .guest: "Continue as guest", .newCode: "New code", .welcome: "Welcome",
        .loading: "Loading…", .error: "Something went wrong", .tryAgain: "Please try again in a moment.",
        .retry: "Retry", .noConnection: "Could not reach 30nama",
        .continueWatching: "Continue watching", .newReleases: "New releases", .suggested: "Suggested",
        .top10: "Top 10", .featured: "Featured",
        .cloudRatings: "Ratings in iCloud", .cloudSynced: "Up to date", .cloudSyncing: "Syncing…",
        .cloudSignedOut: "Not signed in to iCloud", .cloudFailed: "Could not sync",
        .watchlist: "Watchlist", .favorites: "Favourites", .yourTaste: "Your taste", .howYouRate: "How you rate",
        .subscriptionUntil: "Subscription until", .daysLeft: "days left",
        .play: "Play", .resume: "Resume", .details: "Details", .episodes: "Episodes", .season: "Season",
        .seasonsCount: "seasons", .episode: "Episode", .nowPlaying: "Now playing", .cast: "Cast",
        .persianSub: "Persian subtitles", .minLeft: "min left", .min: "min", .free: "FREE", .soon: "SOON",
        .loginToStream: "Sign in to stream and keep your place", .loginToWatch: "Sign in to watch",
        .noStream: "No stream available for this title", .comingSoonNote: "Coming soon",
        .popularNow: "Popular now", .topResults: "Results", .nothingFor: "Nothing found for",
        .searchPrompt: "Movies, series, anime",
        .categories: "Categories", .genres: "Genres", .newest: "Newest", .popular: "Popular",
        .updated: "Updated", .results: "titles",
        .account: "Account", .language: "Language", .signOut: "Sign out", .signInQR: "Sign in with QR code",
        .guestAccount: "Guest", .expires: "Subscription ends", .accountType: "Account type",
        .playback: "Playback", .display: "Display", .about: "About", .defaultSubs: "Default subtitles",
        .subSize: "Subtitle size", .autoplay: "Play next episode automatically", .quality: "Video quality",
        .qualityBest: "Highest", .qualityAuto: "Automatic", .reduceMotion: "Reduce motion",
        .on: "On", .off: "Off", .small: "Small", .medium: "Medium", .large: "Large",
        .sample: "This is how subtitles will look.",
        .profile: "Profile", .yourRating: "Your rating", .forYou: "Picked for you",
        .ratedCount: "rated", .averageRating: "average",
        .noRatingsYet: "You have not rated anything yet",
        .rateHint: "Open a title and give it stars. Your ratings shape what is picked for you.",
        .startWithThese: "Start with these",
        .subColor: "Subtitle colour", .subBackground: "Subtitle background",
        .white: "White", .yellow: "Yellow", .cyan: "Cyan", .green: "Green",
        .bgNone: "None", .bgHalf: "Black 50%", .bgSolid: "Black", .bgBlurHalf: "Blur 50%", .bgBlur: "Blur",
        .version: "Version", .unofficial: "Unofficial client for 30nama.com. Not affiliated with 30nama.",
        .apiHost: "Server",
        .skipIntro: "Skip Intro", .skipRecap: "Skip Recap", .nextEpisode: "Next Episode",
        .subtitles: "Subtitles", .english: "English", .persian: "Persian",
        .audio: "Audio", .audioStereo: "Stereo", .audioSurround: "5.1", .audioOriginal: "Original",
        .speed: "Speed", .subtitleTiming: "Subtitle timing", .subtitleFile: "Subtitle file…",
        .pictureInPicture: "Picture in Picture", .alwaysOnTop: "Always on top", .fullScreen: "Full screen", .reset: "Reset",
        .subFailed: "Could not load subtitles", .playError: "Playback failed",
        .signOutTitle: "Sign out?", .signOutBody: "You will need to scan a new code to sign in again.",
        .cancel: "Cancel",
        .director: "Director", .writer: "Writer", .creator: "Creator", .network: "Network",
        .country: "Country", .originalLanguage: "Language", .status: "Status", .storyline: "Storyline",
        .information: "Information",
    ]

    static let fa: [S: String] = [
        .home: "خانه", .search: "جستجو", .browse: "مرور", .settings: "تنظیمات",
        .movies: "فیلم", .series: "سریال", .anime: "انیمه", .movie: "فیلم",
        .signIn: "ورود", .scanTitle: "با گوشی اسکن کنید", .step1: "دوربین گوشی را باز کنید",
        .step2: "کد را اسکن کنید", .step3: "در ۳۰نما تأیید کنید", .orEnterCode: "یا در 30nama.com/auth این کد را وارد کنید",
        .waiting: "در انتظار اسکن…", .expired: "کد منقضی شد", .codeFailed: "دریافت کد ممکن نشد",
        .guest: "ادامه به‌عنوان مهمان", .newCode: "کد جدید", .welcome: "خوش آمدید",
        .loading: "در حال بارگذاری…", .error: "مشکلی پیش آمد", .tryAgain: "لطفاً کمی بعد دوباره تلاش کنید.",
        .retry: "تلاش دوباره", .noConnection: "اتصال به ۳۰نما برقرار نشد",
        .continueWatching: "ادامه تماشا", .newReleases: "تازه‌ها", .suggested: "پیشنهادی",
        .top10: "۱۰ برتر", .featured: "ویژه",
        .cloudRatings: "امتیازها در iCloud", .cloudSynced: "همگام", .cloudSyncing: "در حال همگام‌سازی…",
        .cloudSignedOut: "وارد iCloud نشده‌اید", .cloudFailed: "همگام نشد",
        .watchlist: "لیست تماشا", .favorites: "علاقه‌مندی‌ها", .yourTaste: "سلیقهٔ شما", .howYouRate: "امتیازهای شما در یک نگاه",
        .subscriptionUntil: "اعتبار اشتراک تا", .daysLeft: "روز مانده",
        .play: "پخش", .resume: "ادامه", .details: "جزئیات", .episodes: "قسمت‌ها", .season: "فصل",
        .seasonsCount: "فصل", .episode: "قسمت", .nowPlaying: "در حال پخش", .cast: "بازیگران",
        .persianSub: "زیرنویس فارسی", .minLeft: "دقیقه مانده", .min: "دقیقه", .free: "رایگان", .soon: "به‌زودی",
        .loginToStream: "برای پخش و ادامهٔ تماشا وارد شوید", .loginToWatch: "برای تماشا وارد شوید",
        .noStream: "پخش این عنوان در دسترس نیست", .comingSoonNote: "به‌زودی",
        .popularNow: "محبوب این روزها", .topResults: "نتیجه‌ها", .nothingFor: "چیزی پیدا نشد برای",
        .searchPrompt: "فیلم، سریال، انیمه",
        .categories: "دسته‌ها", .genres: "ژانرها", .newest: "جدیدترین", .popular: "محبوب‌ترین",
        .updated: "به‌روزشده", .results: "عنوان",
        .account: "حساب کاربری", .language: "زبان", .signOut: "خروج از حساب", .signInQR: "ورود با کد QR",
        .guestAccount: "مهمان", .expires: "پایان اشتراک", .accountType: "نوع حساب",
        .playback: "پخش", .display: "نمایش", .about: "درباره", .defaultSubs: "زیرنویس پیش‌فرض",
        .subSize: "اندازهٔ زیرنویس", .autoplay: "پخش خودکار قسمت بعد", .quality: "کیفیت پخش",
        .qualityBest: "بالاترین", .qualityAuto: "خودکار", .reduceMotion: "کاهش حرکت",
        .on: "روشن", .off: "خاموش", .small: "کوچک", .medium: "متوسط", .large: "بزرگ",
        .sample: "زیرنویس به این شکل نمایش داده می‌شود.",
        .profile: "پروفایل", .yourRating: "امتیاز شما", .forYou: "پیشنهاد برای شما",
        .ratedCount: "امتیاز", .averageRating: "میانگین",
        .noRatingsYet: "هنوز به عنوانی امتیاز نداده‌اید",
        .rateHint: "صفحهٔ هر عنوان را باز کنید و به آن ستاره بدهید. پیشنهادها از روی همین امتیازها ساخته می‌شوند.",
        .startWithThese: "از این‌ها شروع کنید",
        .subColor: "رنگ زیرنویس", .subBackground: "پس‌زمینهٔ زیرنویس",
        .white: "سفید", .yellow: "زرد", .cyan: "فیروزه‌ای", .green: "سبز",
        .bgNone: "بدون پس‌زمینه", .bgHalf: "مشکی ۵۰٪", .bgSolid: "مشکی ۱۰۰٪", .bgBlurHalf: "بلور ۵۰٪", .bgBlur: "بلور ۱۰۰٪",
        .version: "نسخه", .unofficial: "برنامهٔ غیررسمی برای 30nama.com؛ وابسته به ۳۰نما نیست.",
        .apiHost: "سرور",
        .skipIntro: "رد کردن تیتراژ", .skipRecap: "رد کردن خلاصه", .nextEpisode: "قسمت بعد",
        .subtitles: "زیرنویس", .english: "انگلیسی", .persian: "فارسی",
        .audio: "صدا", .audioStereo: "استریو", .audioSurround: "۵.۱", .audioOriginal: "اصلی",
        .speed: "سرعت پخش", .subtitleTiming: "زمان‌بندی زیرنویس", .subtitleFile: "فایل زیرنویس…",
        .pictureInPicture: "تصویر در تصویر", .alwaysOnTop: "همیشه روی پنجره‌ها", .fullScreen: "تمام‌صفحه", .reset: "بازنشانی",
        .subFailed: "بارگذاری زیرنویس ممکن نشد", .playError: "پخش با خطا روبه‌رو شد",
        .signOutTitle: "از حساب خارج می‌شوید؟", .signOutBody: "برای ورود دوباره باید کد جدیدی اسکن کنید.",
        .cancel: "انصراف",
        .director: "کارگردان", .writer: "نویسنده", .creator: "سازنده", .network: "شبکه",
        .country: "کشور", .originalLanguage: "زبان", .status: "وضعیت", .storyline: "داستان",
        .information: "اطلاعات",
    ]

    static let ar: [S: String] = [
        .home: "الرئيسية", .search: "بحث", .browse: "تصفّح", .settings: "الإعدادات",
        .movies: "أفلام", .series: "مسلسلات", .anime: "أنمي", .movie: "فيلم",
        .signIn: "تسجيل الدخول", .scanTitle: "امسح الرمز بهاتفك", .step1: "افتح كاميرا الهاتف",
        .step2: "امسح الرمز", .step3: "أكّد الدخول في 30nama.com", .orEnterCode: "أو افتح 30nama.com/auth وأدخل الرمز",
        .waiting: "في انتظار المسح…", .expired: "انتهت صلاحية الرمز", .codeFailed: "تعذّر الحصول على رمز",
        .guest: "المتابعة كضيف", .newCode: "رمز جديد", .welcome: "مرحبًا",
        .loading: "جارٍ التحميل…", .error: "حدث خطأ ما", .tryAgain: "يُرجى المحاولة بعد قليل.",
        .retry: "إعادة المحاولة", .noConnection: "تعذّر الاتصال بـ 30nama",
        .continueWatching: "متابعة المشاهدة", .newReleases: "أحدث الإصدارات", .suggested: "مقترحات",
        .top10: "أفضل 10", .featured: "مميّز",
        .cloudRatings: "التقييمات في iCloud", .cloudSynced: "محدّثة", .cloudSyncing: "جارٍ المزامنة…",
        .cloudSignedOut: "لم يُسجَّل الدخول إلى iCloud", .cloudFailed: "تعذّرت المزامنة",
        .watchlist: "قائمة المشاهدة", .favorites: "المفضّلة", .yourTaste: "ذوقك", .howYouRate: "كيف تقيّم",
        .subscriptionUntil: "الاشتراك حتى", .daysLeft: "يومًا متبقيًا",
        .play: "تشغيل", .resume: "متابعة", .details: "التفاصيل", .episodes: "الحلقات", .season: "الموسم",
        .seasonsCount: "مواسم", .episode: "الحلقة", .nowPlaying: "قيد التشغيل", .cast: "طاقم التمثيل",
        .persianSub: "ترجمة فارسية", .minLeft: "دقيقة متبقية", .min: "دقيقة", .free: "مجاني", .soon: "قريبًا",
        .loginToStream: "سجّل الدخول للمشاهدة وحفظ موضعك", .loginToWatch: "سجّل الدخول للمشاهدة",
        .noStream: "لا يتوفر بث لهذا العنوان", .comingSoonNote: "قريبًا",
        .popularNow: "الرائج الآن", .topResults: "النتائج", .nothingFor: "لا نتائج عن",
        .searchPrompt: "أفلام، مسلسلات، أنمي",
        .categories: "الفئات", .genres: "الأنواع", .newest: "الأحدث", .popular: "الأكثر رواجًا",
        .updated: "المحدّثة", .results: "عنوانًا",
        .account: "الحساب", .language: "اللغة", .signOut: "تسجيل الخروج", .signInQR: "تسجيل الدخول برمز QR",
        .guestAccount: "ضيف", .expires: "نهاية الاشتراك", .accountType: "نوع الحساب",
        .playback: "التشغيل", .display: "العرض", .about: "حول", .defaultSubs: "الترجمة الافتراضية",
        .subSize: "حجم الترجمة", .autoplay: "تشغيل الحلقة التالية تلقائيًا", .quality: "جودة الفيديو",
        .qualityBest: "الأعلى", .qualityAuto: "تلقائية", .reduceMotion: "تقليل الحركة",
        .on: "تشغيل", .off: "إيقاف", .small: "صغير", .medium: "متوسط", .large: "كبير",
        .sample: "هكذا ستظهر الترجمة.",
        .profile: "ملفي", .yourRating: "تقييمك", .forYou: "مختارات لك",
        .ratedCount: "تم تقييمها", .averageRating: "المتوسط",
        .noRatingsYet: "لم تقيّم أي عنوان بعد",
        .rateHint: "افتح عنوانًا وامنحه نجومًا. تقييماتك تحدّد ما نختاره لك.",
        .startWithThese: "ابدأ بهذه",
        .subColor: "لون الترجمة", .subBackground: "خلفية الترجمة",
        .white: "أبيض", .yellow: "أصفر", .cyan: "سماوي", .green: "أخضر",
        .bgNone: "بدون", .bgHalf: "أسود 50%", .bgSolid: "أسود", .bgBlurHalf: "ضبابي 50%", .bgBlur: "ضبابي",
        .version: "الإصدار", .unofficial: "تطبيق غير رسمي لـ 30nama.com، وغير تابع لـ 30nama.",
        .apiHost: "الخادم",
        .skipIntro: "تخطّي المقدمة", .skipRecap: "تخطّي الملخص", .nextEpisode: "الحلقة التالية",
        .subtitles: "الترجمة", .english: "الإنجليزية", .persian: "الفارسية",
        .audio: "الصوت", .audioStereo: "ستيريو", .audioSurround: "5.1", .audioOriginal: "الأصلي",
        .speed: "سرعة التشغيل", .subtitleTiming: "توقيت الترجمة", .subtitleFile: "ملف ترجمة…",
        .pictureInPicture: "صورة داخل صورة", .alwaysOnTop: "دائمًا في المقدمة", .fullScreen: "ملء الشاشة", .reset: "إعادة الضبط",
        .subFailed: "تعذّر تحميل الترجمة", .playError: "تعذّر التشغيل",
        .signOutTitle: "تسجيل الخروج؟", .signOutBody: "ستحتاج إلى مسح رمز جديد لتسجيل الدخول مرة أخرى.",
        .cancel: "إلغاء",
        .director: "المخرج", .writer: "الكاتب", .creator: "المبتكر", .network: "الشبكة",
        .country: "البلد", .originalLanguage: "اللغة", .status: "الحالة", .storyline: "القصة",
        .information: "معلومات",
    ]
}
