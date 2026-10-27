// Written by kotlin-core/tools/gen-l10n.py from 30nama/Core/L10n.swift. Do not edit by hand.
@file:Suppress("EnumEntryName", "SpellCheckingInspection")

package app.nama.core

/** Interface string keys, named as in the Swift apps so every app reads the same tables. */
enum class S {
    home,
    search,
    browse,
    settings,
    movies,
    series,
    anime,
    movie,
    signIn,
    scanTitle,
    step1,
    step2,
    step3,
    orEnterCode,
    waiting,
    expired,
    codeFailed,
    guest,
    newCode,
    welcome,
    loading,
    error,
    tryAgain,
    retry,
    noConnection,
    siteOutOfReach,
    continueWatching,
    newReleases,
    suggested,
    top10,
    featured,
    cloudRatings,
    cloudSynced,
    cloudSyncing,
    cloudSignedOut,
    cloudFailed,
    watchlist,
    favorites,
    yourTaste,
    howYouRate,
    subscriptionUntil,
    daysLeft,
    play,
    resume,
    details,
    episodes,
    season,
    seasonsCount,
    episode,
    nowPlaying,
    cast,
    filmography,
    born,
    died,
    readMore,
    readLess,
    imdbProfile,
    weeklySchedule,
    topLists,
    imdbTopMovies,
    imdbTopSeries,
    siteTopMovies,
    siteTopSeries,
    today,
    fullName,
    personAge,
    profession,
    checkForUpdate,
    upToDate,
    news,
    newsItems,
    announcements,
    renewSubscription,
    subscriptionDays,
    yourName,
    yourNameHint,
    optional,
    keySaved,
    showKey,
    hideKey,
    removeKey,
    persianSub,
    minLeft,
    min,
    free,
    soon,
    loginToStream,
    loginToWatch,
    noStream,
    comingSoonNote,
    popularNow,
    topResults,
    nothingFor,
    searchPrompt,
    categories,
    genres,
    newest,
    popular,
    updated,
    results,
    account,
    language,
    signOut,
    signInQR,
    guestAccount,
    expires,
    accountType,
    playback,
    display,
    about,
    defaultSubs,
    subSize,
    autoplay,
    quality,
    qualityBest,
    qualityAuto,
    reduceMotion,
    on,
    off,
    small,
    medium,
    large,
    sample,
    profile,
    yourRating,
    forYou,
    ratedCount,
    averageRating,
    noRatingsYet,
    rateHint,
    startWithThese,
    subColor,
    subBackground,
    white,
    yellow,
    cyan,
    green,
    bgNone,
    bgHalf,
    bgSolid,
    bgBlurHalf,
    bgBlur,
    version,
    unofficial,
    apiHost,
    skipIntro,
    skipRecap,
    nextEpisode,
    subtitles,
    english,
    persian,
    audio,
    audioStereo,
    audioSurround,
    audioOriginal,
    speed,
    subtitleTiming,
    subtitleFile,
    pictureInPicture,
    alwaysOnTop,
    fullScreen,
    reset,
    effects,
    effectsAuto,
    effectsFull,
    effectsLight,
    exportRatings,
    exportDone,
    openIn,
    markWatched,
    markUnwatched,
    upcoming,
    allChoices,
    minScore,
    decade,
    downloads,
    download,
    downloadFolder,
    choose,
    showInFinder,
    noDownloads,
    allEpisodes,
    pause,
    remove,
    open,
    downloadFailed,
    downloadStarted,
    downloadDone,
    noDownload,
    fileQuality,
    saveTo,
    perEpisode,
    wholeSeason,
    getSubtitle,
    subtitleSaved,
    subtitleMissing,
    withSubtitle,
    incomplete,
    notInPack,
    ofTotal,
    removeFromList,
    moveToTrash,
    topFilms,
    topSeries,
    intro,
    recap,
    credits,
    aroundPicture,
    blackBars,
    sceneGlow,
    folderDenied,
    performance,
    back,
    close,
    back10,
    forward10,
    rowNext,
    rowPrevious,
    clearSearch,
    sound,
    removeFromContinue,
    newVersion,
    installUpdate,
    updating,
    relaunch,
    updateReady,
    updateFailed,
    downloadPage,
    later,
    openElsewhere,
    shortcuts,
    mute,
    volume,
    slower,
    faster,
    subtitleEarlier,
    subtitleLater,
    seen,
    episodeCount,
    sleepTimer,
    endOfEpisode,
    endOfFilm,
    airPlay,
    autoSkip,
    reportProblem,
    newEpisode,
    findingSubtitles,
    fromSubSource,
    subsourceKey,
    subsourceKeyHint,
    secondsShort,
    services,
    tmdbKey,
    tmdbKeyHint,
    trailer,
    trailerUnreachable,
    trailerNotLoaded,
    crew,
    reviews,
    jobComposer,
    jobPhotography,
    jobNovel,
    trendingWeek,
    seeAll,
    subFailed,
    playError,
    signOutTitle,
    signOutBody,
    cancel,
    director,
    writer,
    creator,
    network,
    country,
    originalLanguage,
    status,
    storyline,
    information,
    notifications,
    markAllRead,
    noNotifications,
    unread,
    earlier,
    downloadSpeed,
    unlimited,
    freeSpace,
    downloadHours,
    downloadHoursHint,
    anyTime,
    hoursTo,
    waitingForHours,
    advancedSearch,
    advancedHint,
    kind,
    ageRating,
    years,
    relevance,
    byYear,
    noMatches,
    directorName,
    signInHint,
    imdbTop250,
    siteTop100,
    votes,
    inWatchlists,
    inFavorites,
    budget,
    exclusiveSub,
    latestUpdate,
    otherLanguages,
    bufferTime,
    seconds,
    streamOnly,
    queued,
    downloadsAtOnce,
    titleUpdates,
}

internal object LocTables {
    val en: Map<S, String> by lazy { en() }
    val fa: Map<S, String> by lazy { fa() }
    val ar: Map<S, String> by lazy { ar() }

    private fun en(): Map<S, String> = java.util.EnumMap<S, String>(S::class.java).apply {
        put(S.home, "Home")
        put(S.search, "Search")
        put(S.browse, "Browse")
        put(S.settings, "Settings")
        put(S.movies, "Movies")
        put(S.series, "Series")
        put(S.anime, "Anime")
        put(S.movie, "Movie")
        put(S.signIn, "Sign in")
        put(S.scanTitle, "Scan with your phone")
        put(S.step1, "Open the camera on your phone")
        put(S.step2, "Scan the code")
        put(S.step3, "Confirm on 30nama.com")
        put(S.orEnterCode, "Or go to 30nama.com/auth and enter")
        put(S.waiting, "Waiting for scan…")
        put(S.expired, "Code expired")
        put(S.codeFailed, "Could not get a code")
        put(S.guest, "Continue as guest")
        put(S.newCode, "New code")
        put(S.welcome, "Welcome")
        put(S.loading, "Loading…")
        put(S.error, "Something went wrong")
        put(S.tryAgain, "Please try again in a moment.")
        put(S.retry, "Retry")
        put(S.noConnection, "Could not reach 30nama")
        put(S.siteOutOfReach, "The 30nama website can't be reached. Try again with a VPN.")
        put(S.continueWatching, "Continue watching")
        put(S.newReleases, "New releases")
        put(S.suggested, "Suggested")
        put(S.top10, "Top 10")
        put(S.featured, "Featured")
        put(S.cloudRatings, "Ratings in iCloud")
        put(S.cloudSynced, "Up to date")
        put(S.cloudSyncing, "Syncing…")
        put(S.cloudSignedOut, "Not signed in to iCloud")
        put(S.cloudFailed, "Could not sync")
        put(S.watchlist, "Watchlist")
        put(S.favorites, "Favourites")
        put(S.yourTaste, "Your taste")
        put(S.howYouRate, "How you rate")
        put(S.subscriptionUntil, "Subscription until")
        put(S.daysLeft, "days left")
        put(S.play, "Play")
        put(S.resume, "Resume")
        put(S.details, "Details")
        put(S.episodes, "Episodes")
        put(S.season, "Season")
        put(S.seasonsCount, "seasons")
        put(S.episode, "Episode")
        put(S.nowPlaying, "Now playing")
        put(S.cast, "Cast")
        put(S.filmography, "Filmography")
        put(S.born, "Born")
        put(S.died, "Died")
        put(S.readMore, "More")
        put(S.readLess, "Less")
        put(S.imdbProfile, "IMDb profile")
        put(S.weeklySchedule, "Weekly schedule")
        put(S.topLists, "Top lists")
        put(S.imdbTopMovies, "IMDb top 250 films")
        put(S.imdbTopSeries, "IMDb top 250 series")
        put(S.siteTopMovies, "30nama's top films")
        put(S.siteTopSeries, "30nama's top series")
        put(S.today, "Today")
        put(S.fullName, "Full name")
        put(S.personAge, "Age")
        put(S.profession, "Profession")
        put(S.checkForUpdate, "Check for updates")
        put(S.upToDate, "This is the newest version")
        put(S.news, "News")
        put(S.newsItems, "News")
        put(S.announcements, "From 30nama")
        put(S.renewSubscription, "Renew subscription")
        put(S.subscriptionDays, "days left")
        put(S.yourName, "Your name")
        put(S.optional, "optional")
        put(S.keySaved, "Saved on this Mac")
        put(S.showKey, "Show")
        put(S.hideKey, "Hide")
        put(S.removeKey, "Remove")
        put(S.yourNameHint, "Shown on your profile when your 30nama profile is private. Stays on this Mac.")
        put(S.persianSub, "Persian subtitles")
        put(S.minLeft, "min left")
        put(S.min, "min")
        put(S.free, "FREE")
        put(S.soon, "SOON")
        put(S.loginToStream, "Sign in to stream and keep your place")
        put(S.loginToWatch, "Sign in to watch")
        put(S.noStream, "No stream available for this title")
        put(S.comingSoonNote, "Coming soon")
        put(S.popularNow, "Popular now")
        put(S.topResults, "Results")
        put(S.nothingFor, "Nothing found for")
        put(S.searchPrompt, "Movies, series, anime")
        put(S.categories, "Categories")
        put(S.genres, "Genres")
        put(S.newest, "Newest")
        put(S.popular, "Popular")
        put(S.updated, "Updated")
        put(S.results, "titles")
        put(S.account, "Account")
        put(S.language, "Language")
        put(S.signOut, "Sign out")
        put(S.signInQR, "Sign in with QR code")
        put(S.guestAccount, "Guest")
        put(S.expires, "Subscription ends")
        put(S.accountType, "Account type")
        put(S.playback, "Playback")
        put(S.display, "Display")
        put(S.about, "About")
        put(S.defaultSubs, "Default subtitles")
        put(S.subSize, "Subtitle size")
        put(S.autoplay, "Play next episode automatically")
        put(S.quality, "Video quality")
        put(S.qualityBest, "Highest")
        put(S.qualityAuto, "Automatic")
        put(S.reduceMotion, "Reduce motion")
        put(S.on, "On")
        put(S.off, "Off")
        put(S.small, "Small")
        put(S.medium, "Medium")
        put(S.large, "Large")
        put(S.sample, "This is how subtitles will look.")
        put(S.profile, "Profile")
        put(S.yourRating, "Your rating")
        put(S.forYou, "Picked for you")
        put(S.ratedCount, "rated")
        put(S.averageRating, "average")
        put(S.noRatingsYet, "You have not rated anything yet")
        put(S.rateHint, "Open a title and give it stars. Your ratings shape what is picked for you.")
        put(S.startWithThese, "Start with these")
        put(S.subColor, "Subtitle colour")
        put(S.subBackground, "Subtitle background")
        put(S.white, "White")
        put(S.yellow, "Yellow")
        put(S.cyan, "Cyan")
        put(S.green, "Green")
        put(S.bgNone, "None")
        put(S.bgHalf, "Black 50%")
        put(S.bgSolid, "Black")
        put(S.bgBlurHalf, "Blur 50%")
        put(S.bgBlur, "Blur")
        put(S.version, "Version")
        put(S.unofficial, "Unofficial client for 30nama.com")
        put(S.apiHost, "Server")
        put(S.skipIntro, "Skip Intro")
        put(S.skipRecap, "Skip Recap")
        put(S.nextEpisode, "Next Episode")
        put(S.subtitles, "Subtitles")
        put(S.english, "English")
        put(S.persian, "Persian")
        put(S.audio, "Audio")
        put(S.audioStereo, "Stereo")
        put(S.audioSurround, "5.1")
        put(S.audioOriginal, "Original")
        put(S.speed, "Speed")
        put(S.subtitleTiming, "Subtitle timing")
        put(S.subtitleFile, "Subtitle file…")
        put(S.pictureInPicture, "Picture in Picture")
        put(S.alwaysOnTop, "Always on top")
        put(S.fullScreen, "Full screen")
        put(S.reset, "Reset")
        put(S.effects, "Motion effects")
        put(S.effectsAuto, "Automatic, for this Mac")
        put(S.effectsFull, "Full")
        put(S.effectsLight, "Light, easier on the Mac")
        put(S.performance, "Performance")
        put(S.back, "Back")
        put(S.close, "Close")
        put(S.back10, "Back 10 seconds")
        put(S.forward10, "Forward 10 seconds")
        put(S.rowNext, "Next titles")
        put(S.rowPrevious, "Previous titles")
        put(S.clearSearch, "Clear search")
        put(S.sound, "Sound on or off")
        put(S.exportRatings, "Export ratings…")
        put(S.exportDone, "Ratings saved")
        put(S.openIn, "Open in")
        put(S.openElsewhere, "Open in another player")
        put(S.shortcuts, "Keyboard shortcuts")
        put(S.mute, "Mute")
        put(S.volume, "Volume")
        put(S.slower, "Slower")
        put(S.faster, "Faster")
        put(S.subtitleEarlier, "Subtitles earlier")
        put(S.subtitleLater, "Subtitles later")
        put(S.seen, "Watched")
        put(S.episodeCount, "episodes")
        put(S.sleepTimer, "Sleep timer")
        put(S.endOfEpisode, "End of this episode")
        put(S.endOfFilm, "End of the film")
        put(S.airPlay, "AirPlay")
        put(S.autoSkip, "Skip intros and recaps")
        put(S.reportProblem, "Report a problem")
        put(S.newEpisode, "New episode")
        put(S.findingSubtitles, "Looking for Persian subtitles…")
        put(S.fromSubSource, "Persian, from SubSource")
        put(S.subsourceKey, "SubSource key")
        put(S.services, "Other services")
        put(S.trailer, "Trailer")
        put(S.trendingWeek, "Trending this week")
        put(S.seeAll, "See all")
        put(S.crew, "Crew")
        put(S.reviews, "Reviews")
        put(S.jobComposer, "Music")
        put(S.jobPhotography, "Cinematography")
        put(S.jobNovel, "Novel")
        put(S.trailerUnreachable, "YouTube cannot be reached from this connection.")
        put(S.trailerNotLoaded, "The trailer could not be loaded.")
        put(S.tmdbKey, "TMDB key")
        put(S.tmdbKeyHint, "Optional. Made in your TMDB account settings; adds title logos, trailers, crew and reviews.")
        put(S.secondsShort, "s")
        put(S.subsourceKeyHint, "Optional. Made in your SubSource profile; finds subtitles more reliably.")
        put(S.markWatched, "Mark as watched")
        put(S.markUnwatched, "Mark as not watched")
        put(S.upcoming, "New episodes on the way")
        put(S.allChoices, "All")
        put(S.minScore, "IMDb")
        put(S.decade, "Years")
        put(S.downloads, "Downloads")
        put(S.download, "Download")
        put(S.downloadFolder, "Download folder")
        put(S.choose, "Choose…")
        put(S.showInFinder, "Show in Finder")
        put(S.noDownloads, "Nothing downloaded yet")
        put(S.allEpisodes, "All episodes")
        put(S.pause, "Pause")
        put(S.remove, "Remove")
        put(S.open, "Open")
        put(S.downloadFailed, "Download failed")
        put(S.downloadStarted, "Download started")
        put(S.downloadDone, "Download finished")
        put(S.noDownload, "No download for this title")
        put(S.fileQuality, "Quality")
        put(S.saveTo, "Saves to")
        put(S.perEpisode, "per episode")
        put(S.wholeSeason, "Download the whole season")
        put(S.getSubtitle, "Get Persian subtitles")
        put(S.subtitleSaved, "Subtitles saved beside the file")
        put(S.subtitleMissing, "No matching subtitles found")
        put(S.withSubtitle, "Persian subtitles included")
        put(S.incomplete, "Incomplete")
        put(S.notInPack, "Not available in this quality")
        put(S.ofTotal, "of")
        put(S.removeFromList, "Remove from list")
        put(S.removeFromContinue, "Remove from Continue Watching")
        put(S.newVersion, "New version")
        put(S.installUpdate, "Update")
        put(S.updating, "Downloading")
        put(S.relaunch, "Relaunch")
        put(S.updateReady, "Update ready")
        put(S.updateFailed, "Update failed")
        put(S.downloadPage, "Download page")
        put(S.later, "Later")
        put(S.moveToTrash, "Remove from list and delete file")
        put(S.topFilms, "Top 10 Films")
        put(S.topSeries, "Top 10 Series")
        put(S.intro, "Intro")
        put(S.recap, "Recap")
        put(S.credits, "Credits")
        put(S.aroundPicture, "Around the picture")
        put(S.blackBars, "Black bars, as in a cinema")
        put(S.sceneGlow, "Scene glow, no black bars")
        put(S.folderDenied, "The app may not open the download folder. Allow it in System Settings, Privacy & Security, Files and Folders.")
        put(S.subFailed, "Could not load subtitles")
        put(S.playError, "Playback failed")
        put(S.signOutTitle, "Sign out?")
        put(S.signOutBody, "You will need to scan a new code to sign in again.")
        put(S.cancel, "Cancel")
        put(S.titleUpdates, "What changed")
        put(S.queued, "In queue")
        put(S.downloadsAtOnce, "Downloads at once")
        put(S.notifications, "Notifications")
        put(S.markAllRead, "Mark all as read")
        put(S.noNotifications, "No notifications")
        put(S.unread, "New")
        put(S.earlier, "Earlier")
        put(S.downloadSpeed, "Download speed")
        put(S.unlimited, "Unlimited")
        put(S.freeSpace, "free")
        put(S.downloadHours, "Download hours")
        put(S.downloadHoursHint, "Downloads run only between these hours, for a line that is cheaper at night. Outside them they wait, and go on from where they stopped.")
        put(S.anyTime, "Any time")
        put(S.hoursTo, "to")
        put(S.waitingForHours, "Waiting for the download hours")
        put(S.advancedSearch, "Advanced search")
        put(S.advancedHint, "Narrow 30nama's catalogue by genre, network, language, years and more.")
        put(S.kind, "Type")
        put(S.ageRating, "Age rating")
        put(S.years, "Years")
        put(S.relevance, "Best match")
        put(S.byYear, "Year")
        put(S.noMatches, "Nothing matches these filters.")
        put(S.directorName, "Director's name")
        put(S.signInHint, "Sign in to your 30nama account for your lists, suggestions and where you left off.")
        put(S.imdbTop250, "IMDb Top 250")
        put(S.siteTop100, "30nama Top 100")
        put(S.votes, "votes")
        put(S.inWatchlists, "on watchlists")
        put(S.inFavorites, "among favourites")
        put(S.budget, "Budget")
        put(S.exclusiveSub, "Exclusive Persian subtitles")
        put(S.latestUpdate, "Latest update")
        put(S.otherLanguages, "Other languages")
        put(S.bufferTime, "Buffer ahead")
        put(S.seconds, "seconds")
        put(S.streamOnly, "Streaming only")
        put(S.director, "Director")
        put(S.writer, "Writer")
        put(S.creator, "Creator")
        put(S.network, "Network")
        put(S.country, "Country")
        put(S.originalLanguage, "Language")
        put(S.status, "Status")
        put(S.storyline, "Storyline")
        put(S.information, "Information")
    }

    private fun fa(): Map<S, String> = java.util.EnumMap<S, String>(S::class.java).apply {
        put(S.home, "خانه")
        put(S.search, "جستجو")
        put(S.browse, "کاوش")
        put(S.settings, "تنظیمات")
        put(S.movies, "فیلم")
        put(S.series, "سریال")
        put(S.anime, "انیمه")
        put(S.movie, "فیلم")
        put(S.signIn, "ورود")
        put(S.scanTitle, "با گوشی اسکن کنید")
        put(S.step1, "دوربین گوشی را باز کنید")
        put(S.step2, "کد را اسکن کنید")
        put(S.step3, "در ۳۰نما تأیید کنید")
        put(S.orEnterCode, "یا در 30nama.com/auth این کد را وارد کنید")
        put(S.waiting, "در انتظار اسکن…")
        put(S.expired, "کد منقضی شد")
        put(S.codeFailed, "دریافت کد ممکن نشد")
        put(S.guest, "ادامه به‌عنوان مهمان")
        put(S.newCode, "کد جدید")
        put(S.welcome, "خوش آمدید")
        put(S.loading, "در حال بارگذاری…")
        put(S.error, "مشکلی پیش آمد")
        put(S.tryAgain, "لطفاً کمی بعد دوباره تلاش کنید.")
        put(S.retry, "تلاش دوباره")
        put(S.noConnection, "اتصال به ۳۰نما برقرار نشد")
        put(S.siteOutOfReach, "سایت ۳۰نما در دسترس نیست. با فیلترشکن دوباره امتحان کنید.")
        put(S.continueWatching, "ادامه تماشا")
        put(S.newReleases, "تازه‌ها")
        put(S.suggested, "پیشنهادی")
        put(S.top10, "۱۰ برتر")
        put(S.featured, "ویژه")
        put(S.cloudRatings, "امتیازها در iCloud")
        put(S.cloudSynced, "همگام")
        put(S.cloudSyncing, "در حال همگام‌سازی…")
        put(S.cloudSignedOut, "وارد iCloud نشده‌اید")
        put(S.cloudFailed, "همگام نشد")
        put(S.watchlist, "لیست تماشا")
        put(S.favorites, "علاقه‌مندی‌ها")
        put(S.yourTaste, "سلیقهٔ شما")
        put(S.howYouRate, "امتیازهای شما در یک نگاه")
        put(S.subscriptionUntil, "اعتبار اشتراک تا")
        put(S.daysLeft, "روز مانده")
        put(S.play, "پخش")
        put(S.resume, "ادامه")
        put(S.details, "جزئیات")
        put(S.episodes, "قسمت‌ها")
        put(S.season, "فصل")
        put(S.seasonsCount, "فصل")
        put(S.episode, "قسمت")
        put(S.nowPlaying, "در حال پخش")
        put(S.cast, "بازیگران")
        put(S.filmography, "آثار")
        put(S.born, "تولد")
        put(S.died, "درگذشت")
        put(S.readMore, "بیشتر")
        put(S.readLess, "کمتر")
        put(S.imdbProfile, "پروفایل در IMDb")
        put(S.weeklySchedule, "برنامهٔ هفتگی")
        put(S.topLists, "فهرست‌های برتر")
        put(S.imdbTopMovies, "۲۵۰ فیلم برتر IMDb")
        put(S.imdbTopSeries, "۲۵۰ سریال برتر IMDb")
        put(S.siteTopMovies, "فیلم‌های برتر ۳۰نما")
        put(S.siteTopSeries, "سریال‌های برتر ۳۰نما")
        put(S.today, "امروز")
        put(S.fullName, "نام کامل")
        put(S.personAge, "سن")
        put(S.profession, "حرفه")
        put(S.checkForUpdate, "بررسی نسخهٔ تازه")
        put(S.upToDate, "این تازه‌ترین نسخه است")
        put(S.news, "اخبار")
        put(S.newsItems, "خبرها")
        put(S.announcements, "اطلاعیه‌های ۳۰نما")
        put(S.renewSubscription, "تمدید اشتراک")
        put(S.subscriptionDays, "روز اشتراک")
        put(S.yourName, "نام شما")
        put(S.optional, "اختیاری")
        put(S.keySaved, "روی همین مک ذخیره شد")
        put(S.showKey, "نمایش")
        put(S.hideKey, "پنهان کردن")
        put(S.removeKey, "حذف")
        put(S.yourNameHint, "وقتی پروفایل شما در ۳۰نما خصوصی است، این نام در پروفایل می‌آید. فقط روی همین مک می‌ماند.")
        put(S.persianSub, "زیرنویس فارسی")
        put(S.minLeft, "دقیقه مانده")
        put(S.min, "دقیقه")
        put(S.free, "رایگان")
        put(S.soon, "به‌زودی")
        put(S.loginToStream, "برای پخش و ادامهٔ تماشا وارد شوید")
        put(S.loginToWatch, "برای تماشا وارد شوید")
        put(S.noStream, "پخش این عنوان در دسترس نیست")
        put(S.comingSoonNote, "به‌زودی")
        put(S.popularNow, "محبوب این روزها")
        put(S.topResults, "نتیجه‌ها")
        put(S.nothingFor, "چیزی پیدا نشد برای")
        put(S.searchPrompt, "فیلم، سریال، انیمه")
        put(S.categories, "دسته‌ها")
        put(S.genres, "ژانرها")
        put(S.newest, "جدیدترین")
        put(S.popular, "محبوب‌ترین")
        put(S.updated, "به‌روزشده")
        put(S.results, "عنوان")
        put(S.account, "حساب کاربری")
        put(S.language, "زبان")
        put(S.signOut, "خروج از حساب")
        put(S.signInQR, "ورود با کد QR")
        put(S.guestAccount, "مهمان")
        put(S.expires, "پایان اشتراک")
        put(S.accountType, "نوع حساب")
        put(S.playback, "پخش")
        put(S.display, "نمایش")
        put(S.about, "درباره")
        put(S.defaultSubs, "زیرنویس پیش‌فرض")
        put(S.subSize, "اندازهٔ زیرنویس")
        put(S.autoplay, "پخش خودکار قسمت بعد")
        put(S.quality, "کیفیت پخش")
        put(S.qualityBest, "بالاترین")
        put(S.qualityAuto, "خودکار")
        put(S.reduceMotion, "کاهش حرکت")
        put(S.on, "روشن")
        put(S.off, "خاموش")
        put(S.small, "کوچک")
        put(S.medium, "متوسط")
        put(S.large, "بزرگ")
        put(S.sample, "زیرنویس به این شکل نمایش داده می‌شود.")
        put(S.profile, "پروفایل")
        put(S.yourRating, "امتیاز شما")
        put(S.forYou, "پیشنهاد برای شما")
        put(S.ratedCount, "امتیاز")
        put(S.averageRating, "میانگین")
        put(S.noRatingsYet, "هنوز به عنوانی امتیاز نداده‌اید")
        put(S.rateHint, "صفحهٔ هر عنوان را باز کنید و به آن ستاره بدهید. پیشنهادها از روی همین امتیازها ساخته می‌شوند.")
        put(S.startWithThese, "از این‌ها شروع کنید")
        put(S.subColor, "رنگ زیرنویس")
        put(S.subBackground, "پس‌زمینهٔ زیرنویس")
        put(S.white, "سفید")
        put(S.yellow, "زرد")
        put(S.cyan, "فیروزه‌ای")
        put(S.green, "سبز")
        put(S.bgNone, "بدون پس‌زمینه")
        put(S.bgHalf, "مشکی ۵۰٪")
        put(S.bgSolid, "مشکی ۱۰۰٪")
        put(S.bgBlurHalf, "بلور ۵۰٪")
        put(S.bgBlur, "بلور ۱۰۰٪")
        put(S.version, "نسخه")
        put(S.unofficial, "برنامهٔ غیررسمی برای 30nama.com")
        put(S.apiHost, "سرور")
        put(S.skipIntro, "رد کردن تیتراژ")
        put(S.skipRecap, "رد کردن خلاصه")
        put(S.nextEpisode, "قسمت بعد")
        put(S.subtitles, "زیرنویس")
        put(S.english, "انگلیسی")
        put(S.persian, "فارسی")
        put(S.audio, "صدا")
        put(S.audioStereo, "استریو")
        put(S.audioSurround, "۵.۱")
        put(S.audioOriginal, "اصلی")
        put(S.speed, "سرعت پخش")
        put(S.subtitleTiming, "زمان‌بندی زیرنویس")
        put(S.subtitleFile, "فایل زیرنویس…")
        put(S.pictureInPicture, "تصویر در تصویر")
        put(S.alwaysOnTop, "همیشه روی سایر پنجره‌ها")
        put(S.fullScreen, "تمام‌صفحه")
        put(S.reset, "بازنشانی")
        put(S.effects, "جلوه‌های حرکتی")
        put(S.effectsAuto, "خودکار، بر اساس همین مک")
        put(S.effectsFull, "کامل")
        put(S.effectsLight, "سبک، کم‌مصرف‌تر")
        put(S.performance, "کارایی دستگاه")
        put(S.back, "برگشت")
        put(S.close, "بستن")
        put(S.back10, "۱۰ ثانیه عقب")
        put(S.forward10, "۱۰ ثانیه جلو")
        put(S.rowNext, "عنوان‌های بعدی")
        put(S.rowPrevious, "عنوان‌های قبلی")
        put(S.clearSearch, "پاک کردن جستجو")
        put(S.sound, "قطع و وصل صدا")
        put(S.exportRatings, "خروجی امتیازها…")
        put(S.exportDone, "امتیازها ذخیره شد")
        put(S.openIn, "باز کردن در")
        put(S.openElsewhere, "باز کردن در برنامهٔ دیگر")
        put(S.shortcuts, "میان‌برهای کیبورد")
        put(S.mute, "بی‌صدا")
        put(S.volume, "بلندی صدا")
        put(S.slower, "آهسته‌تر")
        put(S.faster, "تندتر")
        put(S.subtitleEarlier, "زیرنویس زودتر")
        put(S.subtitleLater, "زیرنویس دیرتر")
        put(S.seen, "دیده‌شده")
        put(S.episodeCount, "قسمت")
        put(S.sleepTimer, "تایمر خواب")
        put(S.endOfEpisode, "پایان همین قسمت")
        put(S.endOfFilm, "پایان فیلم")
        put(S.airPlay, "پخش روی دستگاه دیگر")
        put(S.autoSkip, "رد کردن خودکار تیتراژ و خلاصه")
        put(S.reportProblem, "گزارش مشکل")
        put(S.newEpisode, "قسمت تازه")
        put(S.findingSubtitles, "در جستجوی زیرنویس فارسی…")
        put(S.fromSubSource, "فارسی، از SubSource")
        put(S.subsourceKey, "کلید SubSource")
        put(S.services, "سرویس‌های دیگر")
        put(S.trailer, "تریلر")
        put(S.trendingWeek, "پرطرفدار این هفته")
        put(S.seeAll, "همه")
        put(S.crew, "عوامل")
        put(S.reviews, "نقدها")
        put(S.jobComposer, "آهنگساز")
        put(S.jobPhotography, "فیلمبردار")
        put(S.jobNovel, "نویسندهٔ رمان")
        put(S.trailerUnreachable, "یوتیوب از این اینترنت در دسترس نیست.")
        put(S.trailerNotLoaded, "تریلر بارگیری نشد.")
        put(S.tmdbKey, "کلید TMDB")
        put(S.tmdbKeyHint, "اختیاری. از تنظیمات حساب TMDB ساخته می‌شود و لوگوی فیلم‌ها، تریلر، عوامل و نقدها را اضافه می‌کند.")
        put(S.secondsShort, "ثانیه")
        put(S.subsourceKeyHint, "اختیاری. از پروفایل SubSource ساخته می‌شود و زیرنویس را مطمئن‌تر پیدا می‌کند.")
        put(S.markWatched, "علامت‌گذاری به‌عنوان دیده‌شده")
        put(S.markUnwatched, "علامت‌گذاری به‌عنوان دیده‌نشده")
        put(S.upcoming, "قسمت‌های آینده")
        put(S.allChoices, "همه")
        put(S.minScore, "IMDb")
        put(S.decade, "سال")
        put(S.downloads, "دانلودها")
        put(S.download, "دانلود")
        put(S.downloadFolder, "پوشهٔ دانلود")
        put(S.choose, "انتخاب…")
        put(S.showInFinder, "نمایش در Finder")
        put(S.noDownloads, "هنوز فایلی دانلود نشده است")
        put(S.allEpisodes, "همهٔ قسمت‌ها")
        put(S.pause, "توقف")
        put(S.remove, "حذف")
        put(S.open, "باز کردن")
        put(S.downloadFailed, "دانلود ناموفق بود")
        put(S.downloadStarted, "دانلود شروع شد")
        put(S.downloadDone, "دانلود تمام شد")
        put(S.noDownload, "این عنوان فایل دانلودی ندارد")
        put(S.fileQuality, "کیفیت")
        put(S.saveTo, "ذخیره در")
        put(S.perEpisode, "هر قسمت")
        put(S.wholeSeason, "دانلود کل فصل")
        put(S.getSubtitle, "دریافت زیرنویس فارسی")
        put(S.subtitleSaved, "زیرنویس کنار فایل ذخیره شد")
        put(S.subtitleMissing, "زیرنویس هماهنگ پیدا نشد")
        put(S.withSubtitle, "با زیرنویس فارسی")
        put(S.incomplete, "ناقص")
        put(S.notInPack, "در این کیفیت موجود نیست")
        put(S.ofTotal, "از")
        put(S.removeFromList, "حذف از فهرست")
        put(S.removeFromContinue, "حذف از ادامهٔ تماشا")
        put(S.newVersion, "نسخهٔ تازه")
        put(S.installUpdate, "به‌روزرسانی")
        put(S.updating, "در حال دریافت")
        put(S.relaunch, "اجرای دوباره")
        put(S.updateReady, "به‌روزرسانی آماده است")
        put(S.updateFailed, "به‌روزرسانی انجام نشد")
        put(S.downloadPage, "صفحهٔ دانلود")
        put(S.later, "بعداً")
        put(S.moveToTrash, "حذف از فهرست و فایل")
        put(S.topFilms, "۱۰ فیلم برتر")
        put(S.topSeries, "۱۰ سریال برتر")
        put(S.intro, "تیتراژ")
        put(S.recap, "خلاصهٔ قسمت قبل")
        put(S.credits, "تیتراژ پایانی")
        put(S.aroundPicture, "اطراف تصویر فیلم")
        put(S.blackBars, "نوار سیاه، مثل سینما")
        put(S.sceneGlow, "نور صحنه، بدون نوار سیاه")
        put(S.folderDenied, "برنامه اجازهٔ باز کردن پوشهٔ دانلود را ندارد. در تنظیمات سیستم، بخش «حریم خصوصی و امنیت»، قسمت «فایل‌ها و پوشه‌ها» اجازه دهید.")
        put(S.subFailed, "بارگذاری زیرنویس ممکن نشد")
        put(S.playError, "پخش با خطا روبه‌رو شد")
        put(S.signOutTitle, "از حساب خارج می‌شوید؟")
        put(S.signOutBody, "برای ورود دوباره باید کد جدیدی اسکن کنید.")
        put(S.cancel, "انصراف")
        put(S.titleUpdates, "تغییرات این عنوان")
        put(S.queued, "در صف")
        put(S.downloadsAtOnce, "دانلود هم‌زمان")
        put(S.notifications, "اعلان‌ها")
        put(S.markAllRead, "همه خوانده شد")
        put(S.noNotifications, "اعلانی نیست")
        put(S.unread, "تازه")
        put(S.earlier, "قبلی")
        put(S.downloadSpeed, "سرعت دانلود")
        put(S.unlimited, "بدون محدودیت")
        put(S.freeSpace, "فضای خالی")
        put(S.downloadHours, "ساعت دانلود")
        put(S.downloadHoursHint, "دانلودها فقط بین این ساعت‌ها انجام می‌شوند، برای اینترنتی که شب‌ها ارزان‌تر است. بیرون از این ساعت‌ها صبر می‌کنند و بعد از همان‌جا ادامه می‌دهند.")
        put(S.anyTime, "هر ساعتی")
        put(S.hoursTo, "تا")
        put(S.waitingForHours, "در انتظار ساعت دانلود")
        put(S.advancedSearch, "جستجوی پیشرفته")
        put(S.advancedHint, "فهرست ۳۰نما را با ژانر، شبکه، زبان، سال و چیزهای دیگر محدود کنید.")
        put(S.kind, "نوع")
        put(S.ageRating, "ردهٔ سنی")
        put(S.years, "سال ساخت")
        put(S.relevance, "مرتبط‌ترین")
        put(S.byYear, "سال")
        put(S.noMatches, "چیزی با این فیلترها پیدا نشد.")
        put(S.directorName, "نام کارگردان")
        put(S.signInHint, "برای دیدن لیست‌ها، پیشنهادها و ادامهٔ تماشا وارد حساب ۳۰نما شوید.")
        put(S.imdbTop250, "۲۵۰ فیلم برتر IMDb")
        put(S.siteTop100, "۱۰۰ عنوان برتر ۳۰نما")
        put(S.votes, "رأی")
        put(S.inWatchlists, "در لیست تماشای کاربران")
        put(S.inFavorites, "در علاقه‌مندی‌های کاربران")
        put(S.budget, "بودجه")
        put(S.exclusiveSub, "زیرنویس فارسی اختصاصی")
        put(S.latestUpdate, "آخرین به‌روزرسانی")
        put(S.otherLanguages, "زبان‌های دیگر")
        put(S.bufferTime, "پیش‌بارگذاری")
        put(S.seconds, "ثانیه")
        put(S.streamOnly, "فقط پخش آنلاین")
        put(S.director, "کارگردان")
        put(S.writer, "نویسنده")
        put(S.creator, "سازنده")
        put(S.network, "شبکه")
        put(S.country, "کشور")
        put(S.originalLanguage, "زبان")
        put(S.status, "وضعیت")
        put(S.storyline, "داستان")
        put(S.information, "اطلاعات")
    }

    private fun ar(): Map<S, String> = java.util.EnumMap<S, String>(S::class.java).apply {
        put(S.home, "الرئيسية")
        put(S.search, "بحث")
        put(S.browse, "تصفّح")
        put(S.settings, "الإعدادات")
        put(S.movies, "أفلام")
        put(S.series, "مسلسلات")
        put(S.anime, "أنمي")
        put(S.movie, "فيلم")
        put(S.signIn, "تسجيل الدخول")
        put(S.scanTitle, "امسح الرمز بهاتفك")
        put(S.step1, "افتح كاميرا الهاتف")
        put(S.step2, "امسح الرمز")
        put(S.step3, "أكّد الدخول في 30nama.com")
        put(S.orEnterCode, "أو افتح 30nama.com/auth وأدخل الرمز")
        put(S.waiting, "في انتظار المسح…")
        put(S.expired, "انتهت صلاحية الرمز")
        put(S.codeFailed, "تعذّر الحصول على رمز")
        put(S.guest, "المتابعة كضيف")
        put(S.newCode, "رمز جديد")
        put(S.welcome, "مرحبًا")
        put(S.loading, "جارٍ التحميل…")
        put(S.error, "حدث خطأ ما")
        put(S.tryAgain, "يُرجى المحاولة بعد قليل.")
        put(S.retry, "إعادة المحاولة")
        put(S.noConnection, "تعذّر الاتصال بـ 30nama")
        put(S.siteOutOfReach, "تعذّر الوصول إلى موقع 30nama. أعد المحاولة عبر VPN.")
        put(S.continueWatching, "متابعة المشاهدة")
        put(S.newReleases, "أحدث الإصدارات")
        put(S.suggested, "مقترحات")
        put(S.top10, "أفضل 10")
        put(S.featured, "مميّز")
        put(S.cloudRatings, "التقييمات في iCloud")
        put(S.cloudSynced, "محدّثة")
        put(S.cloudSyncing, "جارٍ المزامنة…")
        put(S.cloudSignedOut, "لم يُسجَّل الدخول إلى iCloud")
        put(S.cloudFailed, "تعذّرت المزامنة")
        put(S.watchlist, "قائمة المشاهدة")
        put(S.favorites, "المفضّلة")
        put(S.yourTaste, "ذوقك")
        put(S.howYouRate, "كيف تقيّم")
        put(S.subscriptionUntil, "الاشتراك حتى")
        put(S.daysLeft, "يومًا متبقيًا")
        put(S.play, "تشغيل")
        put(S.resume, "متابعة")
        put(S.details, "التفاصيل")
        put(S.episodes, "الحلقات")
        put(S.season, "الموسم")
        put(S.seasonsCount, "مواسم")
        put(S.episode, "الحلقة")
        put(S.nowPlaying, "قيد التشغيل")
        put(S.cast, "طاقم التمثيل")
        put(S.filmography, "الأعمال")
        put(S.born, "الميلاد")
        put(S.died, "الوفاة")
        put(S.readMore, "المزيد")
        put(S.readLess, "أقل")
        put(S.imdbProfile, "الملف في IMDb")
        put(S.weeklySchedule, "الجدول الأسبوعي")
        put(S.topLists, "القوائم الأفضل")
        put(S.imdbTopMovies, "أفضل 250 فيلمًا في IMDb")
        put(S.imdbTopSeries, "أفضل 250 مسلسلًا في IMDb")
        put(S.siteTopMovies, "أفضل أفلام 30nama")
        put(S.siteTopSeries, "أفضل مسلسلات 30nama")
        put(S.today, "اليوم")
        put(S.fullName, "الاسم الكامل")
        put(S.personAge, "العمر")
        put(S.profession, "المهنة")
        put(S.checkForUpdate, "البحث عن تحديث")
        put(S.upToDate, "هذا أحدث إصدار")
        put(S.news, "الأخبار")
        put(S.newsItems, "الأخبار")
        put(S.announcements, "إعلانات 30nama")
        put(S.renewSubscription, "تجديد الاشتراك")
        put(S.subscriptionDays, "يومًا متبقيًا")
        put(S.yourName, "اسمك")
        put(S.optional, "اختياري")
        put(S.keySaved, "حُفظ على هذا الماك")
        put(S.showKey, "إظهار")
        put(S.hideKey, "إخفاء")
        put(S.removeKey, "إزالة")
        put(S.yourNameHint, "يظهر في ملفك الشخصي عندما يكون ملفك في 30nama خاصًا. يبقى على هذا الماك فقط.")
        put(S.persianSub, "ترجمة فارسية")
        put(S.minLeft, "دقيقة متبقية")
        put(S.min, "دقيقة")
        put(S.free, "مجاني")
        put(S.soon, "قريبًا")
        put(S.loginToStream, "سجّل الدخول للمشاهدة وحفظ موضعك")
        put(S.loginToWatch, "سجّل الدخول للمشاهدة")
        put(S.noStream, "لا يتوفر بث لهذا العنوان")
        put(S.comingSoonNote, "قريبًا")
        put(S.popularNow, "الرائج الآن")
        put(S.topResults, "النتائج")
        put(S.nothingFor, "لا نتائج عن")
        put(S.searchPrompt, "أفلام، مسلسلات، أنمي")
        put(S.categories, "الفئات")
        put(S.genres, "الأنواع")
        put(S.newest, "الأحدث")
        put(S.popular, "الأكثر رواجًا")
        put(S.updated, "المحدّثة")
        put(S.results, "عنوانًا")
        put(S.account, "الحساب")
        put(S.language, "اللغة")
        put(S.signOut, "تسجيل الخروج")
        put(S.signInQR, "تسجيل الدخول برمز QR")
        put(S.guestAccount, "ضيف")
        put(S.expires, "نهاية الاشتراك")
        put(S.accountType, "نوع الحساب")
        put(S.playback, "التشغيل")
        put(S.display, "العرض")
        put(S.about, "حول")
        put(S.defaultSubs, "الترجمة الافتراضية")
        put(S.subSize, "حجم الترجمة")
        put(S.autoplay, "تشغيل الحلقة التالية تلقائيًا")
        put(S.quality, "جودة الفيديو")
        put(S.qualityBest, "الأعلى")
        put(S.qualityAuto, "تلقائية")
        put(S.reduceMotion, "تقليل الحركة")
        put(S.on, "تشغيل")
        put(S.off, "إيقاف")
        put(S.small, "صغير")
        put(S.medium, "متوسط")
        put(S.large, "كبير")
        put(S.sample, "هكذا ستظهر الترجمة.")
        put(S.profile, "ملفي")
        put(S.yourRating, "تقييمك")
        put(S.forYou, "مختارات لك")
        put(S.ratedCount, "تم تقييمها")
        put(S.averageRating, "المتوسط")
        put(S.noRatingsYet, "لم تقيّم أي عنوان بعد")
        put(S.rateHint, "افتح عنوانًا وامنحه نجومًا. تقييماتك تحدّد ما نختاره لك.")
        put(S.startWithThese, "ابدأ بهذه")
        put(S.subColor, "لون الترجمة")
        put(S.subBackground, "خلفية الترجمة")
        put(S.white, "أبيض")
        put(S.yellow, "أصفر")
        put(S.cyan, "سماوي")
        put(S.green, "أخضر")
        put(S.bgNone, "بدون")
        put(S.bgHalf, "أسود 50%")
        put(S.bgSolid, "أسود")
        put(S.bgBlurHalf, "ضبابي 50%")
        put(S.bgBlur, "ضبابي")
        put(S.version, "الإصدار")
        put(S.unofficial, "تطبيق غير رسمي لـ 30nama.com")
        put(S.apiHost, "الخادم")
        put(S.skipIntro, "تخطّي المقدمة")
        put(S.skipRecap, "تخطّي الملخص")
        put(S.nextEpisode, "الحلقة التالية")
        put(S.subtitles, "الترجمة")
        put(S.english, "الإنجليزية")
        put(S.persian, "الفارسية")
        put(S.audio, "الصوت")
        put(S.audioStereo, "ستيريو")
        put(S.audioSurround, "5.1")
        put(S.audioOriginal, "الأصلي")
        put(S.speed, "سرعة التشغيل")
        put(S.subtitleTiming, "توقيت الترجمة")
        put(S.subtitleFile, "ملف ترجمة…")
        put(S.pictureInPicture, "صورة داخل صورة")
        put(S.alwaysOnTop, "دائمًا في المقدمة")
        put(S.fullScreen, "ملء الشاشة")
        put(S.reset, "إعادة الضبط")
        put(S.effects, "تأثيرات الحركة")
        put(S.effectsAuto, "تلقائي حسب هذا الجهاز")
        put(S.effectsFull, "كاملة")
        put(S.effectsLight, "خفيفة وأقل استهلاكًا")
        put(S.performance, "أداء الجهاز")
        put(S.back, "رجوع")
        put(S.close, "إغلاق")
        put(S.back10, "رجوع ١٠ ثوانٍ")
        put(S.forward10, "تقدّم ١٠ ثوانٍ")
        put(S.rowNext, "العناوين التالية")
        put(S.rowPrevious, "العناوين السابقة")
        put(S.clearSearch, "مسح البحث")
        put(S.sound, "تشغيل الصوت أو كتمه")
        put(S.exportRatings, "تصدير التقييمات…")
        put(S.exportDone, "تم حفظ التقييمات")
        put(S.openIn, "فتح في")
        put(S.openElsewhere, "فتح في مشغّل آخر")
        put(S.shortcuts, "اختصارات لوحة المفاتيح")
        put(S.mute, "كتم الصوت")
        put(S.volume, "مستوى الصوت")
        put(S.slower, "أبطأ")
        put(S.faster, "أسرع")
        put(S.subtitleEarlier, "الترجمة أبكر")
        put(S.subtitleLater, "الترجمة لاحقًا")
        put(S.seen, "تمت مشاهدتها")
        put(S.episodeCount, "حلقات")
        put(S.sleepTimer, "مؤقت النوم")
        put(S.endOfEpisode, "نهاية هذه الحلقة")
        put(S.endOfFilm, "نهاية الفيلم")
        put(S.airPlay, "التشغيل على جهاز آخر")
        put(S.autoSkip, "تخطي المقدمة والملخص تلقائيًا")
        put(S.reportProblem, "الإبلاغ عن مشكلة")
        put(S.newEpisode, "حلقة جديدة")
        put(S.findingSubtitles, "جارٍ البحث عن ترجمة فارسية…")
        put(S.fromSubSource, "فارسية، من SubSource")
        put(S.subsourceKey, "مفتاح SubSource")
        put(S.services, "خدمات أخرى")
        put(S.trailer, "الإعلان")
        put(S.trendingWeek, "الأكثر رواجًا هذا الأسبوع")
        put(S.seeAll, "عرض الكل")
        put(S.crew, "طاقم العمل")
        put(S.reviews, "المراجعات")
        put(S.jobComposer, "الموسيقى")
        put(S.jobPhotography, "التصوير السينمائي")
        put(S.jobNovel, "الرواية")
        put(S.trailerUnreachable, "لا يمكن الوصول إلى يوتيوب من هذا الاتصال.")
        put(S.trailerNotLoaded, "تعذّر تحميل الإعلان.")
        put(S.tmdbKey, "مفتاح TMDB")
        put(S.tmdbKeyHint, "اختياري. يُنشأ من إعدادات حسابك على TMDB ويضيف شعارات العناوين والإعلانات وطاقم العمل والمراجعات.")
        put(S.secondsShort, "ث")
        put(S.subsourceKeyHint, "اختياري. يُنشأ من ملفك على SubSource ويجد الترجمات بدقة أكبر.")
        put(S.markWatched, "وضع علامة «تمت المشاهدة»")
        put(S.markUnwatched, "إزالة علامة «تمت المشاهدة»")
        put(S.upcoming, "حلقات جديدة قادمة")
        put(S.allChoices, "الكل")
        put(S.minScore, "IMDb")
        put(S.decade, "السنوات")
        put(S.downloads, "التنزيلات")
        put(S.download, "تنزيل")
        put(S.downloadFolder, "مجلد التنزيل")
        put(S.choose, "اختيار…")
        put(S.showInFinder, "إظهار في Finder")
        put(S.noDownloads, "لا توجد تنزيلات بعد")
        put(S.allEpisodes, "كل الحلقات")
        put(S.pause, "إيقاف مؤقت")
        put(S.remove, "إزالة")
        put(S.open, "فتح")
        put(S.downloadFailed, "فشل التنزيل")
        put(S.downloadStarted, "بدأ التنزيل")
        put(S.downloadDone, "اكتمل التنزيل")
        put(S.noDownload, "لا يوجد تنزيل لهذا العنوان")
        put(S.fileQuality, "الجودة")
        put(S.saveTo, "الحفظ في")
        put(S.perEpisode, "لكل حلقة")
        put(S.wholeSeason, "تنزيل الموسم كاملًا")
        put(S.getSubtitle, "جلب الترجمة الفارسية")
        put(S.subtitleSaved, "حُفظت الترجمة بجانب الملف")
        put(S.subtitleMissing, "لم يُعثر على ترجمة مطابقة")
        put(S.withSubtitle, "مع ترجمة فارسية")
        put(S.incomplete, "غير مكتمل")
        put(S.notInPack, "غير متوفرة بهذه الجودة")
        put(S.ofTotal, "من")
        put(S.removeFromList, "إزالة من القائمة")
        put(S.removeFromContinue, "إزالة من متابعة المشاهدة")
        put(S.newVersion, "إصدار جديد")
        put(S.installUpdate, "تحديث")
        put(S.updating, "جارٍ التنزيل")
        put(S.relaunch, "إعادة التشغيل")
        put(S.updateReady, "التحديث جاهز")
        put(S.updateFailed, "تعذّر التحديث")
        put(S.downloadPage, "صفحة التنزيل")
        put(S.later, "لاحقًا")
        put(S.moveToTrash, "إزالة من القائمة وحذف الملف")
        put(S.topFilms, "أفضل 10 أفلام")
        put(S.topSeries, "أفضل 10 مسلسلات")
        put(S.intro, "المقدمة")
        put(S.recap, "ملخص الحلقة السابقة")
        put(S.credits, "شارة النهاية")
        put(S.aroundPicture, "حول صورة الفيلم")
        put(S.blackBars, "أشرطة سوداء كما في السينما")
        put(S.sceneGlow, "توهج المشهد بلا أشرطة سوداء")
        put(S.folderDenied, "لا يُسمح للتطبيق بفتح مجلد التنزيل. اسمح بذلك من إعدادات النظام، الخصوصية والأمان، الملفات والمجلدات.")
        put(S.subFailed, "تعذّر تحميل الترجمة")
        put(S.playError, "تعذّر التشغيل")
        put(S.signOutTitle, "تسجيل الخروج؟")
        put(S.signOutBody, "ستحتاج إلى مسح رمز جديد لتسجيل الدخول مرة أخرى.")
        put(S.cancel, "إلغاء")
        put(S.titleUpdates, "ما الذي تغيّر")
        put(S.queued, "في قائمة الانتظار")
        put(S.downloadsAtOnce, "التنزيلات المتزامنة")
        put(S.notifications, "الإشعارات")
        put(S.markAllRead, "تعليم الكل كمقروء")
        put(S.noNotifications, "لا توجد إشعارات")
        put(S.unread, "جديدة")
        put(S.earlier, "سابقة")
        put(S.downloadSpeed, "سرعة التنزيل")
        put(S.unlimited, "بلا حد")
        put(S.freeSpace, "مساحة فارغة")
        put(S.downloadHours, "ساعات التنزيل")
        put(S.downloadHoursHint, "تعمل التنزيلات بين هذه الساعات فقط، لخط أرخص ليلًا. خارجها تنتظر، ثم تكمل من حيث توقفت.")
        put(S.anyTime, "في أي وقت")
        put(S.hoursTo, "إلى")
        put(S.waitingForHours, "بانتظار ساعات التنزيل")
        put(S.advancedSearch, "البحث المتقدم")
        put(S.advancedHint, "ضيّق فهرس 30nama حسب النوع والشبكة واللغة والسنوات وغير ذلك.")
        put(S.kind, "النوع")
        put(S.ageRating, "التصنيف العمري")
        put(S.years, "سنوات الإنتاج")
        put(S.relevance, "الأكثر صلة")
        put(S.byYear, "السنة")
        put(S.noMatches, "لا شيء يطابق هذه المرشحات.")
        put(S.directorName, "اسم المخرج")
        put(S.signInHint, "سجّل الدخول إلى حسابك في 30nama لقوائمك والاقتراحات ومتابعة المشاهدة.")
        put(S.imdbTop250, "أفضل 250 في IMDb")
        put(S.siteTop100, "أفضل 100 في 30nama")
        put(S.votes, "صوتًا")
        put(S.inWatchlists, "في قوائم المشاهدة")
        put(S.inFavorites, "في المفضّلة")
        put(S.budget, "الميزانية")
        put(S.exclusiveSub, "ترجمة فارسية حصرية")
        put(S.latestUpdate, "آخر تحديث")
        put(S.otherLanguages, "لغات أخرى")
        put(S.bufferTime, "التحميل المسبق")
        put(S.seconds, "ثانية")
        put(S.streamOnly, "البث فقط")
        put(S.director, "المخرج")
        put(S.writer, "الكاتب")
        put(S.creator, "المبتكر")
        put(S.network, "الشبكة")
        put(S.country, "البلد")
        put(S.originalLanguage, "اللغة")
        put(S.status, "الحالة")
        put(S.storyline, "القصة")
        put(S.information, "معلومات")
    }
}
