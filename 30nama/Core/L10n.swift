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
    case play, resume, details, episodes, season, seasonsCount, episode, nowPlaying, cast, filmography, born, died, readMore, readLess, imdbProfile, weeklySchedule, topLists, imdbTopMovies, imdbTopSeries, siteTopMovies, siteTopSeries, today, fullName, personAge, profession, checkForUpdate, upToDate, news, newsItems, announcements
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
    case effects, effectsAuto, effectsFull, effectsLight
    case exportRatings, exportDone, openIn
    case markWatched, markUnwatched, upcoming, allChoices, minScore, decade
    case downloads, download, downloadFolder, choose, showInFinder, noDownloads, allEpisodes
    case pause, remove, open, downloadFailed, downloadStarted, downloadDone, noDownload
    case fileQuality, saveTo, perEpisode, wholeSeason, getSubtitle, subtitleSaved, subtitleMissing, withSubtitle
    case incomplete, notInPack, ofTotal, removeFromList, moveToTrash, topFilms, topSeries, intro, recap, credits, aroundPicture, blackBars, sceneGlow, folderDenied, performance, back, close, back10, forward10, rowNext, rowPrevious, clearSearch, sound, removeFromContinue, newVersion, installUpdate, updating, relaunch, updateReady, updateFailed, downloadPage, later, openElsewhere, shortcuts, mute, volume, slower, faster, subtitleEarlier, subtitleLater, seen, episodeCount, sleepTimer, endOfEpisode, endOfFilm, airPlay, autoSkip, reportProblem, newEpisode, findingSubtitles, fromSubSource, subsourceKey, subsourceKeyHint, secondsShort, services, tmdbKey, tmdbKeyHint, trailer, trailerUnreachable, crew, reviews, jobComposer, jobPhotography, jobNovel, trendingWeek, seeAll
    case subFailed, playError
    case signOutTitle, signOutBody, cancel
    case director, writer, creator, network, country, originalLanguage, status, storyline, information
    case notifications, markAllRead, noNotifications, unread, earlier, downloadSpeed, unlimited, freeSpace, downloadHours, downloadHoursHint, anyTime, hoursTo, waitingForHours, advancedSearch, advancedHint, kind, ageRating, years, relevance, byYear, noMatches, directorName, signInHint, imdbTop250, siteTop100, votes, inWatchlists, inFavorites, budget, exclusiveSub, latestUpdate, otherLanguages, bufferTime, seconds, streamOnly, queued, downloadsAtOnce, titleUpdates
}

struct Loc: Equatable {
    let lang: Lang

    func callAsFunction(_ key: S) -> String {
        (lang == .fa ? Loc.fa[key] : lang == .ar ? Loc.ar[key] : Loc.en[key]) ?? Loc.en[key] ?? key.rawValue
    }

    /// Digits in the interface language.
    func num(_ s: String) -> String { lang == .fa ? Fmt.persianDigits(s) : s }
    func num(_ n: Int) -> String { num(String(n)) }

    /// Text held to the interface's direction wherever it is drawn, so mixed words and figures
    /// keep their order in a place laid out left to right.
    func isolated(_ text: String) -> String {
        (lang.isRTL ? "\u{2067}" : "\u{2066}") + text + "\u{2069}"
    }

    /// A job as TMDB names it, in the interface's language.
    func job(_ name: String) -> String {
        switch name {
        case "Director": return self(.director)
        case "Creator": return self(.creator)
        case "Screenplay", "Writer": return self(.writer)
        case "Novel": return self(.jobNovel)
        case "Original Music Composer": return self(.jobComposer)
        case "Director of Photography": return self(.jobPhotography)
        default: return name
        }
    }

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
        .seasonsCount: "seasons", .episode: "Episode", .nowPlaying: "Now playing", .cast: "Cast", .filmography: "Filmography", .born: "Born", .died: "Died", .readMore: "More", .readLess: "Less", .imdbProfile: "IMDb profile", .weeklySchedule: "Weekly schedule", .topLists: "Top lists", .imdbTopMovies: "IMDb top 250 films", .imdbTopSeries: "IMDb top 250 series", .siteTopMovies: "30nama's top films", .siteTopSeries: "30nama's top series", .today: "Today", .fullName: "Full name", .personAge: "Age", .profession: "Profession", .checkForUpdate: "Check for updates", .upToDate: "This is the newest version", .news: "News", .newsItems: "News", .announcements: "From 30nama",
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
        .effects: "Motion effects", .effectsAuto: "Automatic, for this Mac", .effectsFull: "Full", .effectsLight: "Light, easier on the Mac", .performance: "Performance",
        .back: "Back", .close: "Close", .back10: "Back 10 seconds", .forward10: "Forward 10 seconds",
        .rowNext: "Next titles", .rowPrevious: "Previous titles", .clearSearch: "Clear search", .sound: "Sound on or off",
        .exportRatings: "Export ratings…", .exportDone: "Ratings saved", .openIn: "Open in", .openElsewhere: "Open in another player", .shortcuts: "Keyboard shortcuts", .mute: "Mute", .volume: "Volume", .slower: "Slower", .faster: "Faster", .subtitleEarlier: "Subtitles earlier", .subtitleLater: "Subtitles later", .seen: "Watched", .episodeCount: "episodes", .sleepTimer: "Sleep timer", .endOfEpisode: "End of this episode", .endOfFilm: "End of the film", .airPlay: "AirPlay", .autoSkip: "Skip intros and recaps", .reportProblem: "Report a problem", .newEpisode: "New episode", .findingSubtitles: "Looking for Persian subtitles…", .fromSubSource: "Persian, from SubSource", .subsourceKey: "SubSource key", .services: "Other services", .trailer: "Trailer", .trendingWeek: "Trending this week", .seeAll: "See all", .crew: "Crew", .reviews: "Reviews", .jobComposer: "Music", .jobPhotography: "Cinematography", .jobNovel: "Novel", .trailerUnreachable: "YouTube cannot be reached from this connection.", .tmdbKey: "TMDB key", .tmdbKeyHint: "Optional. Made in your TMDB account settings; adds title logos, trailers, crew and reviews.", .secondsShort: "s", .subsourceKeyHint: "Optional. Made in your SubSource profile; finds subtitles more reliably.",
        .markWatched: "Mark as watched", .markUnwatched: "Mark as not watched", .upcoming: "New episodes on the way",
        .allChoices: "All", .minScore: "IMDb", .decade: "Years",
        .downloads: "Downloads", .download: "Download", .downloadFolder: "Download folder", .choose: "Choose…",
        .showInFinder: "Show in Finder", .noDownloads: "Nothing downloaded yet", .allEpisodes: "All episodes",
        .pause: "Pause", .remove: "Remove", .open: "Open", .downloadFailed: "Download failed",
        .downloadStarted: "Download started", .downloadDone: "Download finished", .noDownload: "No download for this title",
        .fileQuality: "Quality", .saveTo: "Saves to", .perEpisode: "per episode", .wholeSeason: "Download the whole season",
        .getSubtitle: "Get Persian subtitles", .subtitleSaved: "Subtitles saved beside the file",
        .subtitleMissing: "No matching subtitles found", .withSubtitle: "Persian subtitles included",
        .incomplete: "Incomplete", .notInPack: "Not available in this quality", .ofTotal: "of",
        .removeFromList: "Remove from list", .removeFromContinue: "Remove from Continue Watching", .newVersion: "New version", .installUpdate: "Update", .updating: "Downloading", .relaunch: "Relaunch", .updateReady: "Update ready", .updateFailed: "Update failed", .downloadPage: "Download page", .later: "Later", .moveToTrash: "Remove from list and delete file",
        .topFilms: "Top 10 Films", .topSeries: "Top 10 Series",
        .intro: "Intro", .recap: "Recap", .credits: "Credits", .aroundPicture: "Around the picture", .blackBars: "Black bars, as in a cinema", .sceneGlow: "Scene glow, no black bars",
        .folderDenied: "The app may not open the download folder. Allow it in System Settings, Privacy & Security, Files and Folders.",
        .subFailed: "Could not load subtitles", .playError: "Playback failed",
        .signOutTitle: "Sign out?", .signOutBody: "You will need to scan a new code to sign in again.",
        .cancel: "Cancel",
        .titleUpdates: "What changed",
        .queued: "In queue", .downloadsAtOnce: "Downloads at once",
        .notifications: "Notifications", .markAllRead: "Mark all as read", .noNotifications: "No notifications", .unread: "New", .earlier: "Earlier", .downloadSpeed: "Download speed", .unlimited: "Unlimited", .freeSpace: "free", .downloadHours: "Download hours", .downloadHoursHint: "Downloads run only between these hours, for a line that is cheaper at night. Outside them they wait, and go on from where they stopped.", .anyTime: "Any time", .hoursTo: "to", .waitingForHours: "Waiting for the download hours", .advancedSearch: "Advanced search", .advancedHint: "Narrow 30nama's catalogue by genre, network, language, years and more.", .kind: "Type", .ageRating: "Age rating", .years: "Years", .relevance: "Best match", .byYear: "Year", .noMatches: "Nothing matches these filters.", .directorName: "Director's name", .signInHint: "Sign in to your 30nama account for your lists, suggestions and where you left off.", .imdbTop250: "IMDb Top 250", .siteTop100: "30nama Top 100", .votes: "votes", .inWatchlists: "on watchlists", .inFavorites: "among favourites", .budget: "Budget", .exclusiveSub: "Exclusive Persian subtitles", .latestUpdate: "Latest update", .otherLanguages: "Other languages", .bufferTime: "Buffer ahead", .seconds: "seconds", .streamOnly: "Streaming only",
        .director: "Director", .writer: "Writer", .creator: "Creator", .network: "Network",
        .country: "Country", .originalLanguage: "Language", .status: "Status", .storyline: "Storyline",
        .information: "Information",
    ]

    static let fa: [S: String] = [
        .home: "خانه", .search: "جستجو", .browse: "کاوش", .settings: "تنظیمات",
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
        .seasonsCount: "فصل", .episode: "قسمت", .nowPlaying: "در حال پخش", .cast: "بازیگران", .filmography: "آثار", .born: "تولد", .died: "درگذشت", .readMore: "بیشتر", .readLess: "کمتر", .imdbProfile: "پروفایل در IMDb", .weeklySchedule: "برنامهٔ هفتگی", .topLists: "فهرست‌های برتر", .imdbTopMovies: "۲۵۰ فیلم برتر IMDb", .imdbTopSeries: "۲۵۰ سریال برتر IMDb", .siteTopMovies: "فیلم‌های برتر ۳۰نما", .siteTopSeries: "سریال‌های برتر ۳۰نما", .today: "امروز", .fullName: "نام کامل", .personAge: "سن", .profession: "حرفه", .checkForUpdate: "بررسی نسخهٔ تازه", .upToDate: "این تازه‌ترین نسخه است", .news: "اخبار", .newsItems: "خبرها", .announcements: "اطلاعیه‌های ۳۰نما",
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
        .pictureInPicture: "تصویر در تصویر", .alwaysOnTop: "همیشه روی سایر پنجره‌ها", .fullScreen: "تمام‌صفحه", .reset: "بازنشانی",
        .effects: "جلوه‌های حرکتی", .effectsAuto: "خودکار، بر اساس همین مک", .effectsFull: "کامل", .effectsLight: "سبک، کم‌مصرف‌تر", .performance: "کارایی دستگاه",
        .back: "برگشت", .close: "بستن", .back10: "۱۰ ثانیه عقب", .forward10: "۱۰ ثانیه جلو",
        .rowNext: "عنوان‌های بعدی", .rowPrevious: "عنوان‌های قبلی", .clearSearch: "پاک کردن جستجو", .sound: "قطع و وصل صدا",
        .exportRatings: "خروجی امتیازها…", .exportDone: "امتیازها ذخیره شد", .openIn: "باز کردن در", .openElsewhere: "باز کردن در برنامهٔ دیگر", .shortcuts: "میان‌برهای کیبورد", .mute: "بی‌صدا", .volume: "بلندی صدا", .slower: "آهسته‌تر", .faster: "تندتر", .subtitleEarlier: "زیرنویس زودتر", .subtitleLater: "زیرنویس دیرتر", .seen: "دیده‌شده", .episodeCount: "قسمت", .sleepTimer: "تایمر خواب", .endOfEpisode: "پایان همین قسمت", .endOfFilm: "پایان فیلم", .airPlay: "پخش روی دستگاه دیگر", .autoSkip: "رد کردن خودکار تیتراژ و خلاصه", .reportProblem: "گزارش مشکل", .newEpisode: "قسمت تازه", .findingSubtitles: "در جستجوی زیرنویس فارسی…", .fromSubSource: "فارسی، از SubSource", .subsourceKey: "کلید SubSource", .services: "سرویس‌های دیگر", .trailer: "تریلر", .trendingWeek: "پرطرفدار این هفته", .seeAll: "همه", .crew: "عوامل", .reviews: "نقدها", .jobComposer: "آهنگساز", .jobPhotography: "فیلمبردار", .jobNovel: "نویسندهٔ رمان", .trailerUnreachable: "یوتیوب از این اینترنت در دسترس نیست.", .tmdbKey: "کلید TMDB", .tmdbKeyHint: "اختیاری. از تنظیمات حساب TMDB ساخته می‌شود و لوگوی فیلم‌ها، تریلر، عوامل و نقدها را اضافه می‌کند.", .secondsShort: "ثانیه", .subsourceKeyHint: "اختیاری. از پروفایل SubSource ساخته می‌شود و زیرنویس را مطمئن‌تر پیدا می‌کند.",
        .markWatched: "علامت‌گذاری به‌عنوان دیده‌شده", .markUnwatched: "علامت‌گذاری به‌عنوان دیده‌نشده", .upcoming: "قسمت‌های آینده",
        .allChoices: "همه", .minScore: "IMDb", .decade: "سال",
        .downloads: "دانلودها", .download: "دانلود", .downloadFolder: "پوشهٔ دانلود", .choose: "انتخاب…",
        .showInFinder: "نمایش در Finder", .noDownloads: "هنوز فایلی دانلود نشده است", .allEpisodes: "همهٔ قسمت‌ها",
        .pause: "توقف", .remove: "حذف", .open: "باز کردن", .downloadFailed: "دانلود ناموفق بود",
        .downloadStarted: "دانلود شروع شد", .downloadDone: "دانلود تمام شد", .noDownload: "این عنوان فایل دانلودی ندارد",
        .fileQuality: "کیفیت", .saveTo: "ذخیره در", .perEpisode: "هر قسمت", .wholeSeason: "دانلود کل فصل",
        .getSubtitle: "دریافت زیرنویس فارسی", .subtitleSaved: "زیرنویس کنار فایل ذخیره شد",
        .subtitleMissing: "زیرنویس هماهنگ پیدا نشد", .withSubtitle: "با زیرنویس فارسی",
        .incomplete: "ناقص", .notInPack: "در این کیفیت موجود نیست", .ofTotal: "از",
        .removeFromList: "حذف از فهرست", .removeFromContinue: "حذف از ادامهٔ تماشا", .newVersion: "نسخهٔ تازه", .installUpdate: "به‌روزرسانی", .updating: "در حال دریافت", .relaunch: "اجرای دوباره", .updateReady: "به‌روزرسانی آماده است", .updateFailed: "به‌روزرسانی انجام نشد", .downloadPage: "صفحهٔ دانلود", .later: "بعداً", .moveToTrash: "حذف از فهرست و فایل",
        .topFilms: "۱۰ فیلم برتر", .topSeries: "۱۰ سریال برتر",
        .intro: "تیتراژ", .recap: "خلاصهٔ قسمت قبل", .credits: "تیتراژ پایانی", .aroundPicture: "اطراف تصویر فیلم", .blackBars: "نوار سیاه، مثل سینما", .sceneGlow: "نور صحنه، بدون نوار سیاه",
        .folderDenied: "برنامه اجازهٔ باز کردن پوشهٔ دانلود را ندارد. در تنظیمات سیستم، بخش «حریم خصوصی و امنیت»، قسمت «فایل‌ها و پوشه‌ها» اجازه دهید.",
        .subFailed: "بارگذاری زیرنویس ممکن نشد", .playError: "پخش با خطا روبه‌رو شد",
        .signOutTitle: "از حساب خارج می‌شوید؟", .signOutBody: "برای ورود دوباره باید کد جدیدی اسکن کنید.",
        .cancel: "انصراف",
        .titleUpdates: "تغییرات این عنوان",
        .queued: "در صف", .downloadsAtOnce: "دانلود هم‌زمان",
        .notifications: "اعلان‌ها", .markAllRead: "همه خوانده شد", .noNotifications: "اعلانی نیست", .unread: "تازه", .earlier: "قبلی", .downloadSpeed: "سرعت دانلود", .unlimited: "بدون محدودیت", .freeSpace: "فضای خالی", .downloadHours: "ساعت دانلود", .downloadHoursHint: "دانلودها فقط بین این ساعت‌ها انجام می‌شوند، برای اینترنتی که شب‌ها ارزان‌تر است. بیرون از این ساعت‌ها صبر می‌کنند و بعد از همان‌جا ادامه می‌دهند.", .anyTime: "هر ساعتی", .hoursTo: "تا", .waitingForHours: "در انتظار ساعت دانلود", .advancedSearch: "جستجوی پیشرفته", .advancedHint: "فهرست ۳۰نما را با ژانر، شبکه، زبان، سال و چیزهای دیگر محدود کنید.", .kind: "نوع", .ageRating: "ردهٔ سنی", .years: "سال ساخت", .relevance: "مرتبط‌ترین", .byYear: "سال", .noMatches: "چیزی با این فیلترها پیدا نشد.", .directorName: "نام کارگردان", .signInHint: "برای دیدن لیست‌ها، پیشنهادها و ادامهٔ تماشا وارد حساب ۳۰نما شوید.", .imdbTop250: "۲۵۰ فیلم برتر IMDb", .siteTop100: "۱۰۰ عنوان برتر ۳۰نما", .votes: "رأی", .inWatchlists: "در لیست تماشای کاربران", .inFavorites: "در علاقه‌مندی‌های کاربران", .budget: "بودجه", .exclusiveSub: "زیرنویس فارسی اختصاصی", .latestUpdate: "آخرین به‌روزرسانی", .otherLanguages: "زبان‌های دیگر", .bufferTime: "پیش‌بارگذاری", .seconds: "ثانیه", .streamOnly: "فقط پخش آنلاین",
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
        .seasonsCount: "مواسم", .episode: "الحلقة", .nowPlaying: "قيد التشغيل", .cast: "طاقم التمثيل", .filmography: "الأعمال", .born: "الميلاد", .died: "الوفاة", .readMore: "المزيد", .readLess: "أقل", .imdbProfile: "الملف في IMDb", .weeklySchedule: "الجدول الأسبوعي", .topLists: "القوائم الأفضل", .imdbTopMovies: "أفضل 250 فيلمًا في IMDb", .imdbTopSeries: "أفضل 250 مسلسلًا في IMDb", .siteTopMovies: "أفضل أفلام 30nama", .siteTopSeries: "أفضل مسلسلات 30nama", .today: "اليوم", .fullName: "الاسم الكامل", .personAge: "العمر", .profession: "المهنة", .checkForUpdate: "البحث عن تحديث", .upToDate: "هذا أحدث إصدار", .news: "الأخبار", .newsItems: "الأخبار", .announcements: "إعلانات 30nama",
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
        .effects: "تأثيرات الحركة", .effectsAuto: "تلقائي حسب هذا الجهاز", .effectsFull: "كاملة", .effectsLight: "خفيفة وأقل استهلاكًا", .performance: "أداء الجهاز",
        .back: "رجوع", .close: "إغلاق", .back10: "رجوع ١٠ ثوانٍ", .forward10: "تقدّم ١٠ ثوانٍ",
        .rowNext: "العناوين التالية", .rowPrevious: "العناوين السابقة", .clearSearch: "مسح البحث", .sound: "تشغيل الصوت أو كتمه",
        .exportRatings: "تصدير التقييمات…", .exportDone: "تم حفظ التقييمات", .openIn: "فتح في", .openElsewhere: "فتح في مشغّل آخر", .shortcuts: "اختصارات لوحة المفاتيح", .mute: "كتم الصوت", .volume: "مستوى الصوت", .slower: "أبطأ", .faster: "أسرع", .subtitleEarlier: "الترجمة أبكر", .subtitleLater: "الترجمة لاحقًا", .seen: "تمت مشاهدتها", .episodeCount: "حلقات", .sleepTimer: "مؤقت النوم", .endOfEpisode: "نهاية هذه الحلقة", .endOfFilm: "نهاية الفيلم", .airPlay: "التشغيل على جهاز آخر", .autoSkip: "تخطي المقدمة والملخص تلقائيًا", .reportProblem: "الإبلاغ عن مشكلة", .newEpisode: "حلقة جديدة", .findingSubtitles: "جارٍ البحث عن ترجمة فارسية…", .fromSubSource: "فارسية، من SubSource", .subsourceKey: "مفتاح SubSource", .services: "خدمات أخرى", .trailer: "الإعلان", .trendingWeek: "الأكثر رواجًا هذا الأسبوع", .seeAll: "عرض الكل", .crew: "طاقم العمل", .reviews: "المراجعات", .jobComposer: "الموسيقى", .jobPhotography: "التصوير السينمائي", .jobNovel: "الرواية", .trailerUnreachable: "لا يمكن الوصول إلى يوتيوب من هذا الاتصال.", .tmdbKey: "مفتاح TMDB", .tmdbKeyHint: "اختياري. يُنشأ من إعدادات حسابك على TMDB ويضيف شعارات العناوين والإعلانات وطاقم العمل والمراجعات.", .secondsShort: "ث", .subsourceKeyHint: "اختياري. يُنشأ من ملفك على SubSource ويجد الترجمات بدقة أكبر.",
        .markWatched: "وضع علامة «تمت المشاهدة»", .markUnwatched: "إزالة علامة «تمت المشاهدة»", .upcoming: "حلقات جديدة قادمة",
        .allChoices: "الكل", .minScore: "IMDb", .decade: "السنوات",
        .downloads: "التنزيلات", .download: "تنزيل", .downloadFolder: "مجلد التنزيل", .choose: "اختيار…",
        .showInFinder: "إظهار في Finder", .noDownloads: "لا توجد تنزيلات بعد", .allEpisodes: "كل الحلقات",
        .pause: "إيقاف مؤقت", .remove: "إزالة", .open: "فتح", .downloadFailed: "فشل التنزيل",
        .downloadStarted: "بدأ التنزيل", .downloadDone: "اكتمل التنزيل", .noDownload: "لا يوجد تنزيل لهذا العنوان",
        .fileQuality: "الجودة", .saveTo: "الحفظ في", .perEpisode: "لكل حلقة", .wholeSeason: "تنزيل الموسم كاملًا",
        .getSubtitle: "جلب الترجمة الفارسية", .subtitleSaved: "حُفظت الترجمة بجانب الملف",
        .subtitleMissing: "لم يُعثر على ترجمة مطابقة", .withSubtitle: "مع ترجمة فارسية",
        .incomplete: "غير مكتمل", .notInPack: "غير متوفرة بهذه الجودة", .ofTotal: "من",
        .removeFromList: "إزالة من القائمة", .removeFromContinue: "إزالة من متابعة المشاهدة", .newVersion: "إصدار جديد", .installUpdate: "تحديث", .updating: "جارٍ التنزيل", .relaunch: "إعادة التشغيل", .updateReady: "التحديث جاهز", .updateFailed: "تعذّر التحديث", .downloadPage: "صفحة التنزيل", .later: "لاحقًا", .moveToTrash: "إزالة من القائمة وحذف الملف",
        .topFilms: "أفضل 10 أفلام", .topSeries: "أفضل 10 مسلسلات",
        .intro: "المقدمة", .recap: "ملخص الحلقة السابقة", .credits: "شارة النهاية", .aroundPicture: "حول صورة الفيلم", .blackBars: "أشرطة سوداء كما في السينما", .sceneGlow: "توهج المشهد بلا أشرطة سوداء",
        .folderDenied: "لا يُسمح للتطبيق بفتح مجلد التنزيل. اسمح بذلك من إعدادات النظام، الخصوصية والأمان، الملفات والمجلدات.",
        .subFailed: "تعذّر تحميل الترجمة", .playError: "تعذّر التشغيل",
        .signOutTitle: "تسجيل الخروج؟", .signOutBody: "ستحتاج إلى مسح رمز جديد لتسجيل الدخول مرة أخرى.",
        .cancel: "إلغاء",
        .titleUpdates: "ما الذي تغيّر",
        .queued: "في قائمة الانتظار", .downloadsAtOnce: "التنزيلات المتزامنة",
        .notifications: "الإشعارات", .markAllRead: "تعليم الكل كمقروء", .noNotifications: "لا توجد إشعارات", .unread: "جديدة", .earlier: "سابقة", .downloadSpeed: "سرعة التنزيل", .unlimited: "بلا حد", .freeSpace: "مساحة فارغة", .downloadHours: "ساعات التنزيل", .downloadHoursHint: "تعمل التنزيلات بين هذه الساعات فقط، لخط أرخص ليلًا. خارجها تنتظر، ثم تكمل من حيث توقفت.", .anyTime: "في أي وقت", .hoursTo: "إلى", .waitingForHours: "بانتظار ساعات التنزيل", .advancedSearch: "البحث المتقدم", .advancedHint: "ضيّق فهرس 30nama حسب النوع والشبكة واللغة والسنوات وغير ذلك.", .kind: "النوع", .ageRating: "التصنيف العمري", .years: "سنوات الإنتاج", .relevance: "الأكثر صلة", .byYear: "السنة", .noMatches: "لا شيء يطابق هذه المرشحات.", .directorName: "اسم المخرج", .signInHint: "سجّل الدخول إلى حسابك في 30nama لقوائمك والاقتراحات ومتابعة المشاهدة.", .imdbTop250: "أفضل 250 في IMDb", .siteTop100: "أفضل 100 في 30nama", .votes: "صوتًا", .inWatchlists: "في قوائم المشاهدة", .inFavorites: "في المفضّلة", .budget: "الميزانية", .exclusiveSub: "ترجمة فارسية حصرية", .latestUpdate: "آخر تحديث", .otherLanguages: "لغات أخرى", .bufferTime: "التحميل المسبق", .seconds: "ثانية", .streamOnly: "البث فقط",
        .director: "المخرج", .writer: "الكاتب", .creator: "المبتكر", .network: "الشبكة",
        .country: "البلد", .originalLanguage: "اللغة", .status: "الحالة", .storyline: "القصة",
        .information: "معلومات",
    ]
}
