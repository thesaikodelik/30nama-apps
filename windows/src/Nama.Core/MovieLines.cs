namespace Nama.Core;

/// <summary>A line from a film, with the film it is from.</summary>
/// <param name="Directors">As the film's credits name them.</param>
public sealed record MovieLine(string Text, string Film, int Year, IReadOnlyList<string> Directors)
{
    /// <summary>What to search 30nama for: the film's name, then with its year, the way 30nama writes titles.</summary>
    public IReadOnlyList<string> SearchQueries => [Film, $"{Film} {Year}"];

    /// <summary>
    /// The film on 30nama among search results: a film, not a series, with a close enough name and
    /// the same year give or take one. One that can be streamed wins.
    /// </summary>
    public Post? Match(IEnumerable<Post> posts)
    {
        (Post Post, double Score)? best = null;
        foreach (var post in posts.Where(p => !p.IsSeries))
        {
            var raw = post.TitleEnglish ?? "";
            var name = Math.Max(TitleMatch.Similarity(TitleMatch.StripYear(raw), Film), TitleMatch.Similarity(raw, Film));
            if (name < 0.8 || post.Year is not { } found || Math.Abs(found - Year) > 1) continue;
            var score = name + (found == Year ? 0.25 : 0.1) + (post.HasStream ? 0.1 : 0);
            if (score > (best?.Score ?? 0)) best = (post, score);
        }
        return best?.Post;
    }

    public bool Equals(MovieLine? other) => other is not null && Text == other.Text && Film == other.Film && Year == other.Year && Directors.SequenceEqual(other.Directors);
    public override int GetHashCode() => HashCode.Combine(Text, Film, Year);
}

/// <summary>
/// A line from the screen, said to whoever opens the app: the American Film Institute's hundred
/// most memorable American movie quotations (2005), as Wikipedia's list of them gives them, each
/// with its film and year from the same list and the directors the film's own article credits.
/// One is picked each time the app opens. Kept in the original English, which is how they are known.
/// Written from MovieLines.swift, text for text.
/// </summary>
public static class MovieLines
{
    public static IReadOnlyList<MovieLine> All { get; } =
    [
        new("Frankly, my dear, I don't give a damn.", "Gone with the Wind", 1939, ["Victor Fleming"]),
        new("I'm gonna make him an offer he can't refuse.", "The Godfather", 1972, ["Francis Ford Coppola"]),
        new("You don't understand! I coulda had class. I coulda been a contender. I could've been somebody, instead of a bum, which is what I am.", "On the Waterfront", 1954, ["Elia Kazan"]),
        new("Toto, I've a feeling we're not in Kansas anymore.", "The Wizard of Oz", 1939, ["Victor Fleming"]),
        new("Here's looking at you, kid.", "Casablanca", 1942, ["Michael Curtiz"]),
        new("Go ahead, make my day.", "Sudden Impact", 1983, ["Clint Eastwood"]),
        new("All right, Mr. DeMille, I'm ready for my close-up.", "Sunset Boulevard", 1950, ["Billy Wilder"]),
        new("May the Force be with you.", "Star Wars", 1977, ["George Lucas"]),
        new("Fasten your seatbelts. It's going to be a bumpy night.", "All About Eve", 1950, ["Joseph L. Mankiewicz"]),
        new("You talkin' to me?", "Taxi Driver", 1976, ["Martin Scorsese"]),
        new("What we've got here is failure to communicate.", "Cool Hand Luke", 1967, ["Stuart Rosenberg"]),
        new("I love the smell of napalm in the morning.", "Apocalypse Now", 1979, ["Francis Ford Coppola"]),
        new("Love means never having to say you're sorry.", "Love Story", 1970, ["Arthur Hiller"]),
        new("The stuff that dreams are made of.", "The Maltese Falcon", 1941, ["John Huston"]),
        new("E.T. phone home.", "E.T. the Extra-Terrestrial", 1982, ["Steven Spielberg"]),
        new("They call me Mister Tibbs!", "In the Heat of the Night", 1967, ["Norman Jewison"]),
        new("Rosebud.", "Citizen Kane", 1941, ["Orson Welles"]),
        new("Made it, Ma! Top of the world!", "White Heat", 1949, ["Raoul Walsh"]),
        new("I'm as mad as hell, and I'm not going to take this anymore!", "Network", 1976, ["Sidney Lumet"]),
        new("Louis, I think this is the beginning of a beautiful friendship.", "Casablanca", 1942, ["Michael Curtiz"]),
        new("A census taker once tried to test me. I ate his liver with some fava beans and a nice Chianti.", "The Silence of the Lambs", 1991, ["Jonathan Demme"]),
        new("Bond. James Bond.", "Dr. No", 1962, ["Terence Young"]),
        new("There's no place like home.", "The Wizard of Oz", 1939, ["Victor Fleming"]),
        new("I am big! It's the pictures that got small.", "Sunset Boulevard", 1950, ["Billy Wilder"]),
        new("Show me the money!", "Jerry Maguire", 1996, ["Cameron Crowe"]),
        new("Why don't you come up sometime and see me?", "She Done Him Wrong", 1933, ["Lowell Sherman"]),
        new("I'm walkin' here! I'm walkin' here!", "Midnight Cowboy", 1969, ["John Schlesinger"]),
        new("Play it, Sam. Play 'As Time Goes By.'", "Casablanca", 1942, ["Michael Curtiz"]),
        new("You can't handle the truth!", "A Few Good Men", 1992, ["Rob Reiner"]),
        new("I want to be alone.", "Grand Hotel", 1932, ["Edmund Goulding"]),
        new("After all, tomorrow is another day!", "Gone with the Wind", 1939, ["Victor Fleming"]),
        new("Round up the usual suspects.", "Casablanca", 1942, ["Michael Curtiz"]),
        new("I'll have what she's having.", "When Harry Met Sally...", 1989, ["Rob Reiner"]),
        new("You know how to whistle, don't you, Steve? You just put your lips together and blow.", "To Have and Have Not", 1944, ["Howard Hawks"]),
        new("You're gonna need a bigger boat.", "Jaws", 1975, ["Steven Spielberg"]),
        new("Badges? We ain't got no badges! We don't need no badges! I don't have to show you any stinking badges!", "The Treasure of the Sierra Madre", 1948, ["John Huston"]),
        new("I'll be back.", "The Terminator", 1984, ["James Cameron"]),
        new("Today, I consider myself the luckiest man on the face of the Earth.", "The Pride of the Yankees", 1942, ["Sam Wood"]),
        new("If you build it, he will come.", "Field of Dreams", 1989, ["Phil Alden Robinson"]),
        new("My mama always said life was like a box of chocolates. You never know what you're gonna get.", "Forrest Gump", 1994, ["Robert Zemeckis"]),
        new("We rob banks.", "Bonnie and Clyde", 1967, ["Arthur Penn"]),
        new("Plastics.", "The Graduate", 1967, ["Mike Nichols"]),
        new("We'll always have Paris.", "Casablanca", 1942, ["Michael Curtiz"]),
        new("I see dead people.", "The Sixth Sense", 1999, ["M. Night Shyamalan"]),
        new("Stella! Hey, Stella!", "A Streetcar Named Desire", 1951, ["Elia Kazan"]),
        new("Oh, Jerry, don't let's ask for the moon. We have the stars.", "Now, Voyager", 1942, ["Irving Rapper"]),
        new("Shane. Shane. Come back!", "Shane", 1953, ["George Stevens"]),
        new("Well, nobody's perfect.", "Some Like It Hot", 1959, ["Billy Wilder"]),
        new("It's alive! It's alive!", "Frankenstein", 1931, ["James Whale"]),
        new("Houston, we have a problem.", "Apollo 13", 1995, ["Ron Howard"]),
        new("You've got to ask yourself one question: 'Do I feel lucky?' Well, do ya, punk?", "Dirty Harry", 1971, ["Don Siegel"]),
        new("You had me at 'hello.'", "Jerry Maguire", 1996, ["Cameron Crowe"]),
        new("One morning I shot an elephant in my pajamas. How he got in my pajamas, I don't know.", "Animal Crackers", 1930, ["Victor Heerman"]),
        new("There's no crying in baseball!", "A League of Their Own", 1992, ["Penny Marshall"]),
        new("La-dee-da, la-dee-da.", "Annie Hall", 1977, ["Woody Allen"]),
        new("A boy's best friend is his mother.", "Psycho", 1960, ["Alfred Hitchcock"]),
        new("Greed, for lack of a better word, is good.", "Wall Street", 1987, ["Oliver Stone"]),
        new("Keep your friends close, but your enemies closer.", "The Godfather Part II", 1974, ["Francis Ford Coppola"]),
        new("As God is my witness, I'll never be hungry again.", "Gone with the Wind", 1939, ["Victor Fleming"]),
        new("Well, here's another nice mess you've gotten me into!", "Sons of the Desert", 1933, ["William A. Seiter"]),
        new("Say 'hello' to my little friend!", "Scarface", 1983, ["Brian De Palma"]),
        new("What a dump.", "Beyond the Forest", 1949, ["King Vidor"]),
        new("Mrs. Robinson, you're trying to seduce me. Aren't you?", "The Graduate", 1967, ["Mike Nichols"]),
        new("Gentlemen, you can't fight in here! This is the War Room!", "Dr. Strangelove", 1964, ["Stanley Kubrick"]),
        new("Elementary, my dear Watson.", "The Adventures of Sherlock Holmes", 1939, ["Alfred L. Werker"]),
        new("Take your stinking paws off me, you damned dirty ape.", "Planet of the Apes", 1968, ["Franklin J. Schaffner"]),
        new("Of all the gin joints in all the towns in all the world, she walks into mine.", "Casablanca", 1942, ["Michael Curtiz"]),
        new("Here's Johnny!", "The Shining", 1980, ["Stanley Kubrick"]),
        new("They're here!", "Poltergeist", 1982, ["Tobe Hooper"]),
        new("Is it safe?", "Marathon Man", 1976, ["John Schlesinger"]),
        new("Wait a minute, wait a minute. You ain't heard nothin' yet!", "The Jazz Singer", 1927, ["Alan Crosland"]),
        new("No wire hangers, ever!", "Mommie Dearest", 1981, ["Frank Perry"]),
        new("Mother of mercy, is this the end of Rico?", "Little Caesar", 1931, ["Mervyn LeRoy"]),
        new("Forget it, Jake, it's Chinatown.", "Chinatown", 1974, ["Roman Polanski"]),
        new("I have always depended on the kindness of strangers.", "A Streetcar Named Desire", 1951, ["Elia Kazan"]),
        new("Hasta la vista, baby.", "Terminator 2: Judgment Day", 1991, ["James Cameron"]),
        new("Soylent Green is people!", "Soylent Green", 1973, ["Richard Fleischer"]),
        new("Open the pod bay doors, HAL.", "2001: A Space Odyssey", 1968, ["Stanley Kubrick"]),
        new("Surely you can't be serious. I am serious ... and don't call me Shirley.", "Airplane!", 1980, ["Jim Abrahams", "David Zucker", "Jerry Zucker"]),
        new("Yo, Adrian!", "Rocky", 1976, ["John G. Avildsen"]),
        new("Hello, gorgeous.", "Funny Girl", 1968, ["William Wyler"]),
        new("Toga! Toga!", "National Lampoon's Animal House", 1978, ["John Landis"]),
        new("Listen to them. Children of the night. What music they make.", "Dracula", 1931, ["Tod Browning"]),
        new("Oh, no, it wasn't the airplanes. It was Beauty killed the Beast.", "King Kong", 1933, ["Merian C. Cooper", "Ernest B. Schoedsack"]),
        new("My precious.", "The Lord of the Rings: The Two Towers", 2002, ["Peter Jackson"]),
        new("Attica! Attica!", "Dog Day Afternoon", 1975, ["Sidney Lumet"]),
        new("Sawyer, you're going out a youngster, but you've got to come back a star!", "42nd Street", 1933, ["Lloyd Bacon"]),
        new("Listen to me, mister. You're my knight in shining armor. Don't you forget it. You're going to get back on that horse, and I'm going to be right behind you, holding on tight, and away we're gonna go, go, go!", "On Golden Pond", 1981, ["Mark Rydell"]),
        new("Tell 'em to go out there with all they got and win just one for the Gipper.", "Knute Rockne, All American", 1940, ["Lloyd Bacon", "William K. Howard"]),
        new("A martini. Shaken, not stirred.", "Goldfinger", 1964, ["Guy Hamilton"]),
        new("Who's on first?", "The Naughty Nineties", 1945, ["Jean Yarbrough"]),
        new("Cinderella story. Outta nowhere. A former greenskeeper, now, about to become the Masters champion. It looks like a mirac...It's in the hole! It's in the hole! It's in the hole!", "Caddyshack", 1980, ["Harold Ramis"]),
        new("Life is a banquet, and most poor suckers are starving to death!", "Auntie Mame", 1958, ["Morton DaCosta"]),
        new("I feel the need—the need for speed!", "Top Gun", 1986, ["Tony Scott"]),
        new("Carpe diem. Seize the day, boys. Make your lives extraordinary.", "Dead Poets Society", 1989, ["Peter Weir"]),
        new("Snap out of it!", "Moonstruck", 1987, ["Norman Jewison"]),
        new("My mother thanks you. My father thanks you. My sister thanks you. And I thank you.<ref>During the 'Gay Nineties' period, George M. coined [this as] his famous curtain speech{{nbsp}}... --", "Yankee Doodle Dandy", 1942, ["Michael Curtiz"]),
        new("Nobody puts Baby in a corner.", "Dirty Dancing", 1987, ["Emile Ardolino"]),
        new("I'll get you, my pretty, and your little dog too!", "The Wizard of Oz", 1939, ["Victor Fleming"]),
        new("I'm the king of the world!", "Titanic", 1997, ["James Cameron"]),
    ];

    /// <summary>
    /// The lines from <see cref="All"/> (by their place in it, from 1) that call someone by name or
    /// speak to them, with <c>{name}</c> where the name or the word for them was: a line is only said
    /// to someone when their name fits in it.
    /// </summary>
    public static IReadOnlyList<(int Rank, string Text)> Addressed { get; } =
    [
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
    ];

    /// <summary>Bond's own line goes by the family name, so it is said only when that is known.</summary>
    private const int Bond = 22;

    /// <summary>
    /// The line for this time the app is open: one said to them by their first name when it is
    /// known, otherwise one of the hundred as it is.
    /// </summary>
    public static MovieLine Pick(string? name, string? familyName, Random random)
    {
        name = name?.Trim();
        if (string.IsNullOrEmpty(name)) return All[random.Next(All.Count)];
        var lines = Addressed.ToList();
        if (familyName?.Trim() is { Length: > 0 } family) lines.Add((Bond, $"{family}. {name} {family}."));
        var (rank, text) = lines[random.Next(lines.Count)];
        return All[rank - 1] with { Text = text.Replace("{name}", name) };
    }
}
