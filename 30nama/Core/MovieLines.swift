import Foundation

/// A line from a film, with the film it is from.
struct MovieLine: Hashable {
    var text: String
    var film: String
    var year: Int
    /// As the film's credits name them.
    var directors: [String]
}

/// A line from the screen, said to whoever opens the app: the American Film Institute's hundred
/// most memorable American movie quotations (2005), as Wikipedia's list of them gives them, each
/// with its film and year from the same list and the directors the film's own article credits.
/// One is picked each time the app opens. Kept in the original English, which is how they are known.
enum MovieLines {
    static let all: [MovieLine] = [
        MovieLine(text: "Frankly, my dear, I don't give a damn.", film: "Gone with the Wind", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "I'm gonna make him an offer he can't refuse.", film: "The Godfather", year: 1972, directors: ["Francis Ford Coppola"]),
        MovieLine(text: "You don't understand! I coulda had class. I coulda been a contender. I could've been somebody, instead of a bum, which is what I am.", film: "On the Waterfront", year: 1954, directors: ["Elia Kazan"]),
        MovieLine(text: "Toto, I've a feeling we're not in Kansas anymore.", film: "The Wizard of Oz", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "Here's looking at you, kid.", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "Go ahead, make my day.", film: "Sudden Impact", year: 1983, directors: ["Clint Eastwood"]),
        MovieLine(text: "All right, Mr. DeMille, I'm ready for my close-up.", film: "Sunset Boulevard", year: 1950, directors: ["Billy Wilder"]),
        MovieLine(text: "May the Force be with you.", film: "Star Wars", year: 1977, directors: ["George Lucas"]),
        MovieLine(text: "Fasten your seatbelts. It's going to be a bumpy night.", film: "All About Eve", year: 1950, directors: ["Joseph L. Mankiewicz"]),
        MovieLine(text: "You talkin' to me?", film: "Taxi Driver", year: 1976, directors: ["Martin Scorsese"]),
        MovieLine(text: "What we've got here is failure to communicate.", film: "Cool Hand Luke", year: 1967, directors: ["Stuart Rosenberg"]),
        MovieLine(text: "I love the smell of napalm in the morning.", film: "Apocalypse Now", year: 1979, directors: ["Francis Ford Coppola"]),
        MovieLine(text: "Love means never having to say you're sorry.", film: "Love Story", year: 1970, directors: ["Arthur Hiller"]),
        MovieLine(text: "The stuff that dreams are made of.", film: "The Maltese Falcon", year: 1941, directors: ["John Huston"]),
        MovieLine(text: "E.T. phone home.", film: "E.T. the Extra-Terrestrial", year: 1982, directors: ["Steven Spielberg"]),
        MovieLine(text: "They call me Mister Tibbs!", film: "In the Heat of the Night", year: 1967, directors: ["Norman Jewison"]),
        MovieLine(text: "Rosebud.", film: "Citizen Kane", year: 1941, directors: ["Orson Welles"]),
        MovieLine(text: "Made it, Ma! Top of the world!", film: "White Heat", year: 1949, directors: ["Raoul Walsh"]),
        MovieLine(text: "I'm as mad as hell, and I'm not going to take this anymore!", film: "Network", year: 1976, directors: ["Sidney Lumet"]),
        MovieLine(text: "Louis, I think this is the beginning of a beautiful friendship.", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "A census taker once tried to test me. I ate his liver with some fava beans and a nice Chianti.", film: "The Silence of the Lambs", year: 1991, directors: ["Jonathan Demme"]),
        MovieLine(text: "Bond. James Bond.", film: "Dr. No", year: 1962, directors: ["Terence Young"]),
        MovieLine(text: "There's no place like home.", film: "The Wizard of Oz", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "I am big! It's the pictures that got small.", film: "Sunset Boulevard", year: 1950, directors: ["Billy Wilder"]),
        MovieLine(text: "Show me the money!", film: "Jerry Maguire", year: 1996, directors: ["Cameron Crowe"]),
        MovieLine(text: "Why don't you come up sometime and see me?", film: "She Done Him Wrong", year: 1933, directors: ["Lowell Sherman"]),
        MovieLine(text: "I'm walkin' here! I'm walkin' here!", film: "Midnight Cowboy", year: 1969, directors: ["John Schlesinger"]),
        MovieLine(text: "Play it, Sam. Play 'As Time Goes By.'", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "You can't handle the truth!", film: "A Few Good Men", year: 1992, directors: ["Rob Reiner"]),
        MovieLine(text: "I want to be alone.", film: "Grand Hotel", year: 1932, directors: ["Edmund Goulding"]),
        MovieLine(text: "After all, tomorrow is another day!", film: "Gone with the Wind", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "Round up the usual suspects.", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "I'll have what she's having.", film: "When Harry Met Sally...", year: 1989, directors: ["Rob Reiner"]),
        MovieLine(text: "You know how to whistle, don't you, Steve? You just put your lips together and blow.", film: "To Have and Have Not", year: 1944, directors: ["Howard Hawks"]),
        MovieLine(text: "You're gonna need a bigger boat.", film: "Jaws", year: 1975, directors: ["Steven Spielberg"]),
        MovieLine(text: "Badges? We ain't got no badges! We don't need no badges! I don't have to show you any stinking badges!", film: "The Treasure of the Sierra Madre", year: 1948, directors: ["John Huston"]),
        MovieLine(text: "I'll be back.", film: "The Terminator", year: 1984, directors: ["James Cameron"]),
        MovieLine(text: "Today, I consider myself the luckiest man on the face of the Earth.", film: "The Pride of the Yankees", year: 1942, directors: ["Sam Wood"]),
        MovieLine(text: "If you build it, he will come.", film: "Field of Dreams", year: 1989, directors: ["Phil Alden Robinson"]),
        MovieLine(text: "My mama always said life was like a box of chocolates. You never know what you're gonna get.", film: "Forrest Gump", year: 1994, directors: ["Robert Zemeckis"]),
        MovieLine(text: "We rob banks.", film: "Bonnie and Clyde", year: 1967, directors: ["Arthur Penn"]),
        MovieLine(text: "Plastics.", film: "The Graduate", year: 1967, directors: ["Mike Nichols"]),
        MovieLine(text: "We'll always have Paris.", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "I see dead people.", film: "The Sixth Sense", year: 1999, directors: ["M. Night Shyamalan"]),
        MovieLine(text: "Stella! Hey, Stella!", film: "A Streetcar Named Desire", year: 1951, directors: ["Elia Kazan"]),
        MovieLine(text: "Oh, Jerry, don't let's ask for the moon. We have the stars.", film: "Now, Voyager", year: 1942, directors: ["Irving Rapper"]),
        MovieLine(text: "Shane. Shane. Come back!", film: "Shane", year: 1953, directors: ["George Stevens"]),
        MovieLine(text: "Well, nobody's perfect.", film: "Some Like It Hot", year: 1959, directors: ["Billy Wilder"]),
        MovieLine(text: "It's alive! It's alive!", film: "Frankenstein", year: 1931, directors: ["James Whale"]),
        MovieLine(text: "Houston, we have a problem.", film: "Apollo 13", year: 1995, directors: ["Ron Howard"]),
        MovieLine(text: "You've got to ask yourself one question: 'Do I feel lucky?' Well, do ya, punk?", film: "Dirty Harry", year: 1971, directors: ["Don Siegel"]),
        MovieLine(text: "You had me at 'hello.'", film: "Jerry Maguire", year: 1996, directors: ["Cameron Crowe"]),
        MovieLine(text: "One morning I shot an elephant in my pajamas. How he got in my pajamas, I don't know.", film: "Animal Crackers", year: 1930, directors: ["Victor Heerman"]),
        MovieLine(text: "There's no crying in baseball!", film: "A League of Their Own", year: 1992, directors: ["Penny Marshall"]),
        MovieLine(text: "La-dee-da, la-dee-da.", film: "Annie Hall", year: 1977, directors: ["Woody Allen"]),
        MovieLine(text: "A boy's best friend is his mother.", film: "Psycho", year: 1960, directors: ["Alfred Hitchcock"]),
        MovieLine(text: "Greed, for lack of a better word, is good.", film: "Wall Street", year: 1987, directors: ["Oliver Stone"]),
        MovieLine(text: "Keep your friends close, but your enemies closer.", film: "The Godfather Part II", year: 1974, directors: ["Francis Ford Coppola"]),
        MovieLine(text: "As God is my witness, I'll never be hungry again.", film: "Gone with the Wind", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "Well, here's another nice mess you've gotten me into!", film: "Sons of the Desert", year: 1933, directors: ["William A. Seiter"]),
        MovieLine(text: "Say 'hello' to my little friend!", film: "Scarface", year: 1983, directors: ["Brian De Palma"]),
        MovieLine(text: "What a dump.", film: "Beyond the Forest", year: 1949, directors: ["King Vidor"]),
        MovieLine(text: "Mrs. Robinson, you're trying to seduce me. Aren't you?", film: "The Graduate", year: 1967, directors: ["Mike Nichols"]),
        MovieLine(text: "Gentlemen, you can't fight in here! This is the War Room!", film: "Dr. Strangelove", year: 1964, directors: ["Stanley Kubrick"]),
        MovieLine(text: "Elementary, my dear Watson.", film: "The Adventures of Sherlock Holmes", year: 1939, directors: ["Alfred L. Werker"]),
        MovieLine(text: "Take your stinking paws off me, you damned dirty ape.", film: "Planet of the Apes", year: 1968, directors: ["Franklin J. Schaffner"]),
        MovieLine(text: "Of all the gin joints in all the towns in all the world, she walks into mine.", film: "Casablanca", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "Here's Johnny!", film: "The Shining", year: 1980, directors: ["Stanley Kubrick"]),
        MovieLine(text: "They're here!", film: "Poltergeist", year: 1982, directors: ["Tobe Hooper"]),
        MovieLine(text: "Is it safe?", film: "Marathon Man", year: 1976, directors: ["John Schlesinger"]),
        MovieLine(text: "Wait a minute, wait a minute. You ain't heard nothin' yet!", film: "The Jazz Singer", year: 1927, directors: ["Alan Crosland"]),
        MovieLine(text: "No wire hangers, ever!", film: "Mommie Dearest", year: 1981, directors: ["Frank Perry"]),
        MovieLine(text: "Mother of mercy, is this the end of Rico?", film: "Little Caesar", year: 1931, directors: ["Mervyn LeRoy"]),
        MovieLine(text: "Forget it, Jake, it's Chinatown.", film: "Chinatown", year: 1974, directors: ["Roman Polanski"]),
        MovieLine(text: "I have always depended on the kindness of strangers.", film: "A Streetcar Named Desire", year: 1951, directors: ["Elia Kazan"]),
        MovieLine(text: "Hasta la vista, baby.", film: "Terminator 2: Judgment Day", year: 1991, directors: ["James Cameron"]),
        MovieLine(text: "Soylent Green is people!", film: "Soylent Green", year: 1973, directors: ["Richard Fleischer"]),
        MovieLine(text: "Open the pod bay doors, HAL.", film: "2001: A Space Odyssey", year: 1968, directors: ["Stanley Kubrick"]),
        MovieLine(text: "Surely you can't be serious. I am serious ... and don't call me Shirley.", film: "Airplane!", year: 1980, directors: ["Jim Abrahams", "David Zucker", "Jerry Zucker"]),
        MovieLine(text: "Yo, Adrian!", film: "Rocky", year: 1976, directors: ["John G. Avildsen"]),
        MovieLine(text: "Hello, gorgeous.", film: "Funny Girl", year: 1968, directors: ["William Wyler"]),
        MovieLine(text: "Toga! Toga!", film: "National Lampoon's Animal House", year: 1978, directors: ["John Landis"]),
        MovieLine(text: "Listen to them. Children of the night. What music they make.", film: "Dracula", year: 1931, directors: ["Tod Browning"]),
        MovieLine(text: "Oh, no, it wasn't the airplanes. It was Beauty killed the Beast.", film: "King Kong", year: 1933, directors: ["Merian C. Cooper", "Ernest B. Schoedsack"]),
        MovieLine(text: "My precious.", film: "The Lord of the Rings: The Two Towers", year: 2002, directors: ["Peter Jackson"]),
        MovieLine(text: "Attica! Attica!", film: "Dog Day Afternoon", year: 1975, directors: ["Sidney Lumet"]),
        MovieLine(text: "Sawyer, you're going out a youngster, but you've got to come back a star!", film: "42nd Street", year: 1933, directors: ["Lloyd Bacon"]),
        MovieLine(text: "Listen to me, mister. You're my knight in shining armor. Don't you forget it. You're going to get back on that horse, and I'm going to be right behind you, holding on tight, and away we're gonna go, go, go!", film: "On Golden Pond", year: 1981, directors: ["Mark Rydell"]),
        MovieLine(text: "Tell 'em to go out there with all they got and win just one for the Gipper.", film: "Knute Rockne, All American", year: 1940, directors: ["Lloyd Bacon", "William K. Howard"]),
        MovieLine(text: "A martini. Shaken, not stirred.", film: "Goldfinger", year: 1964, directors: ["Guy Hamilton"]),
        MovieLine(text: "Who's on first?", film: "The Naughty Nineties", year: 1945, directors: ["Jean Yarbrough"]),
        MovieLine(text: "Cinderella story. Outta nowhere. A former greenskeeper, now, about to become the Masters champion. It looks like a mirac...It's in the hole! It's in the hole! It's in the hole!", film: "Caddyshack", year: 1980, directors: ["Harold Ramis"]),
        MovieLine(text: "Life is a banquet, and most poor suckers are starving to death!", film: "Auntie Mame", year: 1958, directors: ["Morton DaCosta"]),
        MovieLine(text: "I feel the need—the need for speed!", film: "Top Gun", year: 1986, directors: ["Tony Scott"]),
        MovieLine(text: "Carpe diem. Seize the day, boys. Make your lives extraordinary.", film: "Dead Poets Society", year: 1989, directors: ["Peter Weir"]),
        MovieLine(text: "Snap out of it!", film: "Moonstruck", year: 1987, directors: ["Norman Jewison"]),
        MovieLine(text: "My mother thanks you. My father thanks you. My sister thanks you. And I thank you.<ref>During the 'Gay Nineties' period, George M. coined [this as] his famous curtain speech{{nbsp}}... --", film: "Yankee Doodle Dandy", year: 1942, directors: ["Michael Curtiz"]),
        MovieLine(text: "Nobody puts Baby in a corner.", film: "Dirty Dancing", year: 1987, directors: ["Emile Ardolino"]),
        MovieLine(text: "I'll get you, my pretty, and your little dog too!", film: "The Wizard of Oz", year: 1939, directors: ["Victor Fleming"]),
        MovieLine(text: "I'm the king of the world!", film: "Titanic", year: 1997, directors: ["James Cameron"]),
    ]

    /// The lines from `all` (by their place in it, from 1) that call someone by name or speak to
    /// them, with `{name}` where the name or the word for them was: a line is only said to
    /// someone when their name fits in it.
    static let addressed: [(rank: Int, text: String)] = [
        (1, "Frankly, {name}, I don't give a damn."),
        (4, "{name}, I've a feeling we're not in Kansas anymore."),
        (5, "Here's looking at you, {name}."),
        (6, "Go ahead, {name}, make my day."),
        (7, "All right, {name}, I'm ready for my close-up."),
        (8, "May the Force be with you, {name}."),
        (9, "{name}, fasten your seatbelts. It's going to be a bumpy night."),
        (15, "{name}, phone home."),
        (18, "Made it, {name}! Top of the world!"),
        (20, "{name}, I think this is the beginning of a beautiful friendship."),
        (25, "Show me the money, {name}!"),
        (26, "{name}, why don't you come up sometime and see me?"),
        (28, "Play it, {name}. Play 'As Time Goes By.'"),
        (29, "{name}, you can't handle the truth!"),
        (31, "After all, {name}, tomorrow is another day!"),
        (34, "You know how to whistle, don't you, {name}? You just put your lips together and blow."),
        (35, "{name}, you're gonna need a bigger boat."),
        (37, "I'll be back, {name}."),
        (39, "{name}, if you build it, he will come."),
        (43, "We'll always have Paris, {name}."),
        (45, "{name}! Hey, {name}!"),
        (46, "Oh, {name}, don't let's ask for the moon. We have the stars."),
        (47, "{name}. {name}. Come back!"),
        (48, "Well, {name}, nobody's perfect."),
        (50, "{name}, we have a problem."),
        (51, "You've got to ask yourself one question: 'Do I feel lucky?' Well, do ya, {name}?"),
        (54, "{name}, there's no crying in baseball!"),
        (58, "{name}, keep your friends close, but your enemies closer."),
        (60, "Well, {name}, here's another nice mess you've gotten me into!"),
        (61, "{name}, say 'hello' to my little friend!"),
        (64, "{name}, you can't fight in here! This is the War Room!"),
        (65, "Elementary, my dear {name}."),
        (68, "Here's {name}!"),
        (71, "Wait a minute, {name}. You ain't heard nothin' yet!"),
        (74, "Forget it, {name}, it's Chinatown."),
        (76, "Hasta la vista, {name}."),
        (78, "Open the pod bay doors, {name}."),
        (80, "Yo, {name}!"),
        (87, "{name}, you're going out a youngster, but you've got to come back a star!"),
        (88, "Listen to me, {name}. You're my knight in shining armor. Don't you forget it."),
        (89, "Tell 'em to go out there with all they got and win just one for {name}."),
        (98, "Nobody puts {name} in a corner."),
        (99, "I'll get you, {name}, and your little dog too!"),
    ]

    /// Bond's own line goes by the family name, so it is said only when that is known.
    private static let bond = 22

    /// The line for this time the app is open: one said to them by their first name when it is
    /// known, otherwise one of the hundred as it is.
    static func pick<G: RandomNumberGenerator>(name: String?, familyName: String? = nil, using generator: inout G) -> MovieLine {
        guard let name = name?.trimmingCharacters(in: .whitespaces), !name.isEmpty else {
            return all.randomElement(using: &generator) ?? all[0]
        }
        var lines = addressed
        if let family = familyName?.trimmingCharacters(in: .whitespaces), !family.isEmpty {
            lines.append((bond, "\(family). \(name) \(family)."))
        }
        let chosen = lines.randomElement(using: &generator) ?? lines[0]
        var line = all[chosen.rank - 1]
        line.text = chosen.text.replacingOccurrences(of: "{name}", with: name)
        return line
    }
}

extension MovieLine {
    /// What to search 30nama for: the film's name, then with its year, the way 30nama writes titles.
    var searchQueries: [String] { [film, "\(film) \(year)"] }

    /// The film on 30nama among search results: a film, not a series, with a close enough name and
    /// the same year give or take one. One that can be streamed wins.
    func match(in posts: [Post]) -> Post? {
        var best: (post: Post, score: Double)?
        for post in posts where !post.isSeries {
            let raw = post.titleEnglish ?? ""
            let name = max(TitleMatch.similarity(TitleMatch.stripYear(raw), film), TitleMatch.similarity(raw, film))
            guard name >= 0.8, let found = post.year, abs(found - year) <= 1 else { continue }
            let score = name + (found == year ? 0.25 : 0.1) + (post.hasStream ? 0.1 : 0)
            if score > (best?.score ?? 0) { best = (post, score) }
        }
        return best?.post
    }
}
