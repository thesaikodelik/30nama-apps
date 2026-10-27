package app.nama.core

import kotlin.random.Random

/** A line from a film, with the film it is from. */
data class MovieLine(
    val text: String,
    val film: String,
    val year: Int,
    /** As the film's credits name them. */
    val directors: List<String>,
) {
    /** What to search 30nama for: the film's name, then with its year, the way 30nama writes titles. */
    val searchQueries: List<String> get() = listOf(film, "$film $year")

    /**
     * The film on 30nama among search results: a film, not a series, with a close enough name and
     * the same year give or take one. One that can be streamed wins.
     */
    fun match(posts: List<Post>): Post? {
        var best: Pair<Post, Double>? = null
        for (post in posts) {
            if (post.isSeries) continue
            val raw = post.titleEnglish ?: ""
            val name = maxOf(TitleMatch.similarity(TitleMatch.stripYear(raw), film), TitleMatch.similarity(raw, film))
            val found = post.year ?: continue
            if (name < 0.8 || Math.abs(found - year) > 1) continue
            val score = name + (if (found == year) 0.25 else 0.1) + (if (post.hasStream) 0.1 else 0.0)
            if (score > (best?.second ?: 0.0)) best = post to score
        }
        return best?.first
    }
}

/**
 * A line from the screen, said to whoever opens the app: the American Film Institute's hundred
 * most memorable American movie quotations (2005), as Wikipedia's list of them gives them, each
 * with its film and year from the same list and the directors the film's own article credits.
 * One is picked each time the app opens. Kept in the original English, which is how they are known.
 */
object MovieLines {
    val all: List<MovieLine> = listOf(
        MovieLine("Frankly, my dear, I don't give a damn.", "Gone with the Wind", 1939, listOf("Victor Fleming")),
        MovieLine("I'm gonna make him an offer he can't refuse.", "The Godfather", 1972, listOf("Francis Ford Coppola")),
        MovieLine("You don't understand! I coulda had class. I coulda been a contender. I could've been somebody, instead of a bum, which is what I am.", "On the Waterfront", 1954, listOf("Elia Kazan")),
        MovieLine("Toto, I've a feeling we're not in Kansas anymore.", "The Wizard of Oz", 1939, listOf("Victor Fleming")),
        MovieLine("Here's looking at you, kid.", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("Go ahead, make my day.", "Sudden Impact", 1983, listOf("Clint Eastwood")),
        MovieLine("All right, Mr. DeMille, I'm ready for my close-up.", "Sunset Boulevard", 1950, listOf("Billy Wilder")),
        MovieLine("May the Force be with you.", "Star Wars", 1977, listOf("George Lucas")),
        MovieLine("Fasten your seatbelts. It's going to be a bumpy night.", "All About Eve", 1950, listOf("Joseph L. Mankiewicz")),
        MovieLine("You talkin' to me?", "Taxi Driver", 1976, listOf("Martin Scorsese")),
        MovieLine("What we've got here is failure to communicate.", "Cool Hand Luke", 1967, listOf("Stuart Rosenberg")),
        MovieLine("I love the smell of napalm in the morning.", "Apocalypse Now", 1979, listOf("Francis Ford Coppola")),
        MovieLine("Love means never having to say you're sorry.", "Love Story", 1970, listOf("Arthur Hiller")),
        MovieLine("The stuff that dreams are made of.", "The Maltese Falcon", 1941, listOf("John Huston")),
        MovieLine("E.T. phone home.", "E.T. the Extra-Terrestrial", 1982, listOf("Steven Spielberg")),
        MovieLine("They call me Mister Tibbs!", "In the Heat of the Night", 1967, listOf("Norman Jewison")),
        MovieLine("Rosebud.", "Citizen Kane", 1941, listOf("Orson Welles")),
        MovieLine("Made it, Ma! Top of the world!", "White Heat", 1949, listOf("Raoul Walsh")),
        MovieLine("I'm as mad as hell, and I'm not going to take this anymore!", "Network", 1976, listOf("Sidney Lumet")),
        MovieLine("Louis, I think this is the beginning of a beautiful friendship.", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("A census taker once tried to test me. I ate his liver with some fava beans and a nice Chianti.", "The Silence of the Lambs", 1991, listOf("Jonathan Demme")),
        MovieLine("Bond. James Bond.", "Dr. No", 1962, listOf("Terence Young")),
        MovieLine("There's no place like home.", "The Wizard of Oz", 1939, listOf("Victor Fleming")),
        MovieLine("I am big! It's the pictures that got small.", "Sunset Boulevard", 1950, listOf("Billy Wilder")),
        MovieLine("Show me the money!", "Jerry Maguire", 1996, listOf("Cameron Crowe")),
        MovieLine("Why don't you come up sometime and see me?", "She Done Him Wrong", 1933, listOf("Lowell Sherman")),
        MovieLine("I'm walkin' here! I'm walkin' here!", "Midnight Cowboy", 1969, listOf("John Schlesinger")),
        MovieLine("Play it, Sam. Play 'As Time Goes By.'", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("You can't handle the truth!", "A Few Good Men", 1992, listOf("Rob Reiner")),
        MovieLine("I want to be alone.", "Grand Hotel", 1932, listOf("Edmund Goulding")),
        MovieLine("After all, tomorrow is another day!", "Gone with the Wind", 1939, listOf("Victor Fleming")),
        MovieLine("Round up the usual suspects.", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("I'll have what she's having.", "When Harry Met Sally...", 1989, listOf("Rob Reiner")),
        MovieLine("You know how to whistle, don't you, Steve? You just put your lips together and blow.", "To Have and Have Not", 1944, listOf("Howard Hawks")),
        MovieLine("You're gonna need a bigger boat.", "Jaws", 1975, listOf("Steven Spielberg")),
        MovieLine("Badges? We ain't got no badges! We don't need no badges! I don't have to show you any stinking badges!", "The Treasure of the Sierra Madre", 1948, listOf("John Huston")),
        MovieLine("I'll be back.", "The Terminator", 1984, listOf("James Cameron")),
        MovieLine("Today, I consider myself the luckiest man on the face of the Earth.", "The Pride of the Yankees", 1942, listOf("Sam Wood")),
        MovieLine("If you build it, he will come.", "Field of Dreams", 1989, listOf("Phil Alden Robinson")),
        MovieLine("My mama always said life was like a box of chocolates. You never know what you're gonna get.", "Forrest Gump", 1994, listOf("Robert Zemeckis")),
        MovieLine("We rob banks.", "Bonnie and Clyde", 1967, listOf("Arthur Penn")),
        MovieLine("Plastics.", "The Graduate", 1967, listOf("Mike Nichols")),
        MovieLine("We'll always have Paris.", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("I see dead people.", "The Sixth Sense", 1999, listOf("M. Night Shyamalan")),
        MovieLine("Stella! Hey, Stella!", "A Streetcar Named Desire", 1951, listOf("Elia Kazan")),
        MovieLine("Oh, Jerry, don't let's ask for the moon. We have the stars.", "Now, Voyager", 1942, listOf("Irving Rapper")),
        MovieLine("Shane. Shane. Come back!", "Shane", 1953, listOf("George Stevens")),
        MovieLine("Well, nobody's perfect.", "Some Like It Hot", 1959, listOf("Billy Wilder")),
        MovieLine("It's alive! It's alive!", "Frankenstein", 1931, listOf("James Whale")),
        MovieLine("Houston, we have a problem.", "Apollo 13", 1995, listOf("Ron Howard")),
        MovieLine("You've got to ask yourself one question: 'Do I feel lucky?' Well, do ya, punk?", "Dirty Harry", 1971, listOf("Don Siegel")),
        MovieLine("You had me at 'hello.'", "Jerry Maguire", 1996, listOf("Cameron Crowe")),
        MovieLine("One morning I shot an elephant in my pajamas. How he got in my pajamas, I don't know.", "Animal Crackers", 1930, listOf("Victor Heerman")),
        MovieLine("There's no crying in baseball!", "A League of Their Own", 1992, listOf("Penny Marshall")),
        MovieLine("La-dee-da, la-dee-da.", "Annie Hall", 1977, listOf("Woody Allen")),
        MovieLine("A boy's best friend is his mother.", "Psycho", 1960, listOf("Alfred Hitchcock")),
        MovieLine("Greed, for lack of a better word, is good.", "Wall Street", 1987, listOf("Oliver Stone")),
        MovieLine("Keep your friends close, but your enemies closer.", "The Godfather Part II", 1974, listOf("Francis Ford Coppola")),
        MovieLine("As God is my witness, I'll never be hungry again.", "Gone with the Wind", 1939, listOf("Victor Fleming")),
        MovieLine("Well, here's another nice mess you've gotten me into!", "Sons of the Desert", 1933, listOf("William A. Seiter")),
        MovieLine("Say 'hello' to my little friend!", "Scarface", 1983, listOf("Brian De Palma")),
        MovieLine("What a dump.", "Beyond the Forest", 1949, listOf("King Vidor")),
        MovieLine("Mrs. Robinson, you're trying to seduce me. Aren't you?", "The Graduate", 1967, listOf("Mike Nichols")),
        MovieLine("Gentlemen, you can't fight in here! This is the War Room!", "Dr. Strangelove", 1964, listOf("Stanley Kubrick")),
        MovieLine("Elementary, my dear Watson.", "The Adventures of Sherlock Holmes", 1939, listOf("Alfred L. Werker")),
        MovieLine("Take your stinking paws off me, you damned dirty ape.", "Planet of the Apes", 1968, listOf("Franklin J. Schaffner")),
        MovieLine("Of all the gin joints in all the towns in all the world, she walks into mine.", "Casablanca", 1942, listOf("Michael Curtiz")),
        MovieLine("Here's Johnny!", "The Shining", 1980, listOf("Stanley Kubrick")),
        MovieLine("They're here!", "Poltergeist", 1982, listOf("Tobe Hooper")),
        MovieLine("Is it safe?", "Marathon Man", 1976, listOf("John Schlesinger")),
        MovieLine("Wait a minute, wait a minute. You ain't heard nothin' yet!", "The Jazz Singer", 1927, listOf("Alan Crosland")),
        MovieLine("No wire hangers, ever!", "Mommie Dearest", 1981, listOf("Frank Perry")),
        MovieLine("Mother of mercy, is this the end of Rico?", "Little Caesar", 1931, listOf("Mervyn LeRoy")),
        MovieLine("Forget it, Jake, it's Chinatown.", "Chinatown", 1974, listOf("Roman Polanski")),
        MovieLine("I have always depended on the kindness of strangers.", "A Streetcar Named Desire", 1951, listOf("Elia Kazan")),
        MovieLine("Hasta la vista, baby.", "Terminator 2: Judgment Day", 1991, listOf("James Cameron")),
        MovieLine("Soylent Green is people!", "Soylent Green", 1973, listOf("Richard Fleischer")),
        MovieLine("Open the pod bay doors, HAL.", "2001: A Space Odyssey", 1968, listOf("Stanley Kubrick")),
        MovieLine("Surely you can't be serious. I am serious ... and don't call me Shirley.", "Airplane!", 1980, listOf("Jim Abrahams", "David Zucker", "Jerry Zucker")),
        MovieLine("Yo, Adrian!", "Rocky", 1976, listOf("John G. Avildsen")),
        MovieLine("Hello, gorgeous.", "Funny Girl", 1968, listOf("William Wyler")),
        MovieLine("Toga! Toga!", "National Lampoon's Animal House", 1978, listOf("John Landis")),
        MovieLine("Listen to them. Children of the night. What music they make.", "Dracula", 1931, listOf("Tod Browning")),
        MovieLine("Oh, no, it wasn't the airplanes. It was Beauty killed the Beast.", "King Kong", 1933, listOf("Merian C. Cooper", "Ernest B. Schoedsack")),
        MovieLine("My precious.", "The Lord of the Rings: The Two Towers", 2002, listOf("Peter Jackson")),
        MovieLine("Attica! Attica!", "Dog Day Afternoon", 1975, listOf("Sidney Lumet")),
        MovieLine("Sawyer, you're going out a youngster, but you've got to come back a star!", "42nd Street", 1933, listOf("Lloyd Bacon")),
        MovieLine("Listen to me, mister. You're my knight in shining armor. Don't you forget it. You're going to get back on that horse, and I'm going to be right behind you, holding on tight, and away we're gonna go, go, go!", "On Golden Pond", 1981, listOf("Mark Rydell")),
        MovieLine("Tell 'em to go out there with all they got and win just one for the Gipper.", "Knute Rockne, All American", 1940, listOf("Lloyd Bacon", "William K. Howard")),
        MovieLine("A martini. Shaken, not stirred.", "Goldfinger", 1964, listOf("Guy Hamilton")),
        MovieLine("Who's on first?", "The Naughty Nineties", 1945, listOf("Jean Yarbrough")),
        MovieLine("Cinderella story. Outta nowhere. A former greenskeeper, now, about to become the Masters champion. It looks like a mirac...It's in the hole! It's in the hole! It's in the hole!", "Caddyshack", 1980, listOf("Harold Ramis")),
        MovieLine("Life is a banquet, and most poor suckers are starving to death!", "Auntie Mame", 1958, listOf("Morton DaCosta")),
        MovieLine("I feel the need—the need for speed!", "Top Gun", 1986, listOf("Tony Scott")),
        MovieLine("Carpe diem. Seize the day, boys. Make your lives extraordinary.", "Dead Poets Society", 1989, listOf("Peter Weir")),
        MovieLine("Snap out of it!", "Moonstruck", 1987, listOf("Norman Jewison")),
        MovieLine("My mother thanks you. My father thanks you. My sister thanks you. And I thank you.<ref>During the 'Gay Nineties' period, George M. coined [this as] his famous curtain speech{{nbsp}}... --", "Yankee Doodle Dandy", 1942, listOf("Michael Curtiz")),
        MovieLine("Nobody puts Baby in a corner.", "Dirty Dancing", 1987, listOf("Emile Ardolino")),
        MovieLine("I'll get you, my pretty, and your little dog too!", "The Wizard of Oz", 1939, listOf("Victor Fleming")),
        MovieLine("I'm the king of the world!", "Titanic", 1997, listOf("James Cameron")),
    )

    /**
     * The lines from `all` (by their place in it, from 1) that call someone by name or speak to
     * them, with `{name}` where the name or the word for them was: a line is only said to
     * someone when their name fits in it.
     */
    val addressed: List<Pair<Int, String>> = listOf(
        1 to "Frankly, {name}, I don't give a damn.",
        4 to "{name}, I've a feeling we're not in Kansas anymore.",
        5 to "Here's looking at you, {name}.",
        6 to "Go ahead, {name}, make my day.",
        7 to "All right, {name}, I'm ready for my close-up.",
        8 to "May the Force be with you, {name}.",
        9 to "{name}, fasten your seatbelts. It's going to be a bumpy night.",
        15 to "{name}, phone home.",
        18 to "Made it, {name}! Top of the world!",
        20 to "{name}, I think this is the beginning of a beautiful friendship.",
        25 to "Show me the money, {name}!",
        26 to "{name}, why don't you come up sometime and see me?",
        28 to "Play it, {name}. Play 'As Time Goes By.'",
        29 to "{name}, you can't handle the truth!",
        31 to "After all, {name}, tomorrow is another day!",
        34 to "You know how to whistle, don't you, {name}? You just put your lips together and blow.",
        35 to "{name}, you're gonna need a bigger boat.",
        37 to "I'll be back, {name}.",
        39 to "{name}, if you build it, he will come.",
        43 to "We'll always have Paris, {name}.",
        45 to "{name}! Hey, {name}!",
        46 to "Oh, {name}, don't let's ask for the moon. We have the stars.",
        47 to "{name}. {name}. Come back!",
        48 to "Well, {name}, nobody's perfect.",
        50 to "{name}, we have a problem.",
        51 to "You've got to ask yourself one question: 'Do I feel lucky?' Well, do ya, {name}?",
        54 to "{name}, there's no crying in baseball!",
        58 to "{name}, keep your friends close, but your enemies closer.",
        60 to "Well, {name}, here's another nice mess you've gotten me into!",
        61 to "{name}, say 'hello' to my little friend!",
        64 to "{name}, you can't fight in here! This is the War Room!",
        65 to "Elementary, my dear {name}.",
        68 to "Here's {name}!",
        71 to "Wait a minute, {name}. You ain't heard nothin' yet!",
        74 to "Forget it, {name}, it's Chinatown.",
        76 to "Hasta la vista, {name}.",
        78 to "Open the pod bay doors, {name}.",
        80 to "Yo, {name}!",
        87 to "{name}, you're going out a youngster, but you've got to come back a star!",
        88 to "Listen to me, {name}. You're my knight in shining armor. Don't you forget it.",
        89 to "Tell 'em to go out there with all they got and win just one for {name}.",
        98 to "Nobody puts {name} in a corner.",
        99 to "I'll get you, {name}, and your little dog too!",
    )

    /** Bond's own line goes by the family name, so it is said only when that is known. */
    private const val BOND = 22

    /**
     * The line for this time the app is open: one said to them by their first name when it is
     * known, otherwise one of the hundred as it is.
     */
    fun pick(name: String?, familyName: String? = null, random: Random = Random.Default): MovieLine {
        val first = name?.trimWhitespace()
        if (first.isNullOrEmpty()) return all.random(random)
        val lines = addressed.toMutableList()
        val family = familyName?.trimWhitespace()
        if (!family.isNullOrEmpty()) lines.add(BOND to "$family. $first $family.")
        val (rank, text) = lines.random(random)
        return all[rank - 1].copy(text = text.replace("{name}", first))
    }
}
