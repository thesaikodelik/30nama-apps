namespace Nama.Core;

/// <summary>
/// A message 30nama sends to the account: news, a title that arrived, an offer.
///
/// The shape is read from what 30nama's own desktop app reads (`notification`, answered with
/// <c>{notifications, total_unread, page}</c>); no answer of the server's own has been seen yet,
/// so every field is optional and the date is taken in any of the forms the API uses elsewhere.
/// </summary>
public sealed record AccountNotification
{
    public enum Kinds { Plain, Link, Post, Pricing, Stream }

    public required string Id { get; init; }
    public string? Title { get; init; }
    /// <summary>The text, with the HTML it is sent with taken out.</summary>
    public string? Content { get; init; }
    public bool Read { get; init; }
    public Uri? Image { get; init; }
    public Kinds Kind { get; init; }
    public Uri? Link { get; init; }
    public Post? Post { get; init; }
    public (string? Gregorian, string? Jalali) Date { get; init; }

    public static AccountNotification? Parse(Json j)
    {
        if (j["id"].String is not { } id) return null;
        var date = j["date"];
        return new AccountNotification
        {
            Id = id,
            Title = j["title"].Text,
            Content = StripHtml(j["content"].Text),
            Read = j["read"].Bool,
            Image = j["image"].Url ?? j["image"]["webp"].Url ?? j["image"]["jpg"].Url,
            Kind = j["notification_type"].Text switch { "link" => Kinds.Link, "post" => Kinds.Post, "pricing" => Kinds.Pricing, "stream" => Kinds.Stream, _ => Kinds.Plain },
            Link = j["link"].Url,
            Post = j["post"].Object is not null ? Core.Post.Parse(j["post"]) : null,
            Date = date.Object is not null ? (date["g"].Text, date["j"].Text) : (date.Text, null),
        };
    }

    private static string? StripHtml(string? html)
    {
        if (html is null) return null;
        var text = System.Text.RegularExpressions.Regex.Replace(html, @"<br\s*/?>", "\n", System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        text = System.Text.RegularExpressions.Regex.Replace(text, "<[^>]+>", "");
        text = System.Net.WebUtility.HtmlDecode(text).Trim();
        return text.Length == 0 ? null : text;
    }
}

public sealed class NotificationPage
{
    public IReadOnlyList<AccountNotification> Items { get; }
    public int Unread { get; }
    public int Page { get; }
    public int Pages { get; }

    /// <summary>The same page with its messages changed on this side, as when they are read.</summary>
    public NotificationPage(Json j, IReadOnlyList<AccountNotification> items) : this(j)
    {
        Items = items;
        Unread = items.Count(n => !n.Read);
    }

    public NotificationPage(Json j)
    {
        var list = j["notifications"].Type == Json.Kind.Array ? j["notifications"] : j;
        Items = list.Array.Select(AccountNotification.Parse).OfType<AccountNotification>().ToList();
        Unread = j["total_unread"].Int ?? Items.Count(n => !n.Read);
        Page = j["page"].Int ?? 1;
        Pages = j["pages"].Int ?? Page;
    }
}
