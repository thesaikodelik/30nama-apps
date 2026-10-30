using Nama.Core;

namespace Nama.App.Model;

/// <summary>
/// Trailers found on Aparat, for titles TMDB gave none for: in Iran, where TMDB and YouTube are
/// filtered, that is every title. Each title is looked up once a run. As AparatStore.swift. Used
/// from the UI thread only.
/// </summary>
public sealed class AparatStore
{
    public static AparatStore Shared { get; } = new();

    /// <summary>
    /// How an Aparat video is named where a trailer is passed around as one string (a YouTube id
    /// otherwise): <c>aparat:</c> and its uid.
    /// </summary>
    public const string Prefix = "aparat:";

    private static readonly Transport Net = ApiClient.HttpTransport();

    /// <summary>What was found, by 30nama post id; null when the search found no trailer.</summary>
    private readonly Dictionary<int, Aparat.Video?> _found = [];
    private readonly HashSet<int> _asked = [];

    public Aparat.Video? Found(int post) => _found.GetValueOrDefault(post);

    /// <summary>Looks for the title's trailer, if it has not been looked for already.</summary>
    public async Task LookAsync(Post post)
    {
        if (!_asked.Add(post.Id)) return;
        var title = post.TitleEnglish ?? post.Title(Lang.En);
        if (await JsonAsync(Aparat.SearchRequest(title)) is not { } answer)
        {
            // No answer: tried again next time the title is opened.
            _asked.Remove(post.Id);
            return;
        }
        _found[post.Id] = Aparat.Trailer(Aparat.VideosInSearch(answer), title, post.Year);
    }

    /// <summary>The file to play. Asked for when it is played: Aparat's addresses are signed and do not last.</summary>
    public async Task<Uri?> FileAsync(string uid) =>
        await JsonAsync(Aparat.VideoRequest(uid)) is { } answer ? Aparat.FileInVideo(answer) : null;

    private static async Task<Json?> JsonAsync(HttpRequestMessage request)
    {
        using (request)
        {
            try
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
                using var response = await Net(request, timeout.Token);
                if ((int)response.StatusCode != 200) return null;
                return Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
            }
            catch (Exception) { return null; }
        }
    }
}
