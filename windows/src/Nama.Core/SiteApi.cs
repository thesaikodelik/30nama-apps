using System.Text.Json;
using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// The actions of 30nama's website API: some of the site is only there (a person's titles, the
/// weekly schedule, the top lists, the newest titles). Same account system and same
/// <c>{success, msg, result}</c> envelope as the app API, but its own host and key, the action's
/// parameters in the path, and posts that are flat where the app API's are nested. As SiteAPI.swift.
/// </summary>
public static class SiteQuery
{
    /// <summary>
    /// A person's titles, newest first: what the site's own "explore?cast=nm…" asks. Only the site
    /// API acts on <c>cast</c>, only with the IMDb id, and only in a form field: sent as JSON or in
    /// the address, the filter is dropped without a word and the whole site comes back.
    /// </summary>
    public static (string Path, Dictionary<string, string> Form) Filmography(string imdb, int page) =>
        ($"advanced_search/page/{page}/orderby/year/order/desc",
         new Dictionary<string, string> { ["parameters"] = JsonSerializer.Serialize(new SortedDictionary<string, string> { ["cast"] = imdb }) });
}

public static partial class SitePosts
{
    [GeneratedRegex(@"(?<=\s)\d{4}$")]
    private static partial Regex YearAtEnd();

    /// <summary>A post as the site API lists it, put in the app API's shape so it is read by the one parser.</summary>
    public static Post? Parse(Json j)
    {
        if (j["id"].Int is null) return null;
        static Json Images(Json v)
        {
            var webp = new Dictionary<string, object?>();
            var jpg = new Dictionary<string, object?>();
            foreach (var size in new[] { "small", "medium", "large", "big" })
            {
                webp[size] = v[size + "_webp"];
                jpg[size] = v[size];
            }
            return Json.From(new Dictionary<string, object?> { ["webp"] = webp, ["jpg"] = jpg });
        }
        var title = j["title"].Text ?? "";
        // The site has no year of its own on a list entry: it is the one at the end of the title.
        var year = YearAtEnd().Match(title) is { Success: true } m ? m.Value : null;
        var imdb = j["imdb_id"].Text is { } id ? $"https://www.imdb.com/title/{id}/" : null;
        return Post.Parse(Json.From(new Dictionary<string, object?>
        {
            ["id"] = j["id"],
            ["options"] = new Dictionary<string, object?>
            {
                ["title_type"] = j["title_type"], ["is_series"] = j["is_series"], ["stream"] = j["stream"],
                ["coming_soon"] = j["coming_soon"], ["free_stream"] = j["free_stream"], ["persian_subtitle"] = j["persian_subtitle"],
            },
            ["title"] = new Dictionary<string, object?> { ["english"] = j["title"], ["local"] = j["persian_title"] },
            ["plot"] = new Dictionary<string, object?> { ["english"] = j["english_plot"], ["local"] = j["persian_plot"] },
            ["info"] = new Dictionary<string, object?> { ["year"] = year, ["budget"] = j["budget"] },
            ["genre"] = j["genre"],
            ["image"] = new Dictionary<string, object?>
            {
                ["poster"] = Images(j["image"]["poster"]),
                ["cover"] = new Dictionary<string, object?> { ["webp"] = j["image"]["cover_webp"], ["jpg"] = j["image"]["cover"] },
            },
            ["score"] = new Dictionary<string, object?>
            {
                ["imdb"] = new Dictionary<string, object?> { ["score"] = j["imdb_score"], ["votes"] = j["imdb_votes"], ["link"] = imdb, ["top_250"] = j["imdb_250"] },
                ["30nama"] = new Dictionary<string, object?> { ["score"] = j["30nama_score"] },
                ["myanimelist"] = new Dictionary<string, object?> { ["score"] = j["myanimelist_score"] },
                ["mydramalist"] = new Dictionary<string, object?> { ["score"] = j["mydramalist_score"] },
            },
        }));
    }

    /// <summary>A page of the site API's lists: <c>{page, pages, posts}</c>, with no total.</summary>
    public static PostPage Page(Json j)
    {
        var posts = j["posts"].Array.Select(Parse).OfType<Post>().ToList();
        return new PostPage(posts, j["page"].Int ?? 1, j["pages"].Int ?? 1, posts.Count);
    }
}
