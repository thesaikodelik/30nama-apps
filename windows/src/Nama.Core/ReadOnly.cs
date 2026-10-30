using System.Text;

namespace Nama.Core;

/// <summary>
/// Debug builds only, with <c>NAMA_READONLY=1</c>: nothing the app sends changes the account.
/// Where playback was, lists, "continue watching", messages read and sign-out are answered here
/// with a plain success, so automated clicks during a test cannot move a real person's progress.
/// As ReadOnly.kt in the Android app.
/// </summary>
public static class ReadOnly
{
    private static readonly HashSet<string> Writes = ["observer", "user_list", "continue_watching_dismiss", "logout", "notification_read"];

    /// <summary>The network minus the account's writes.</summary>
    public static Transport Wrap(Transport inner) => async (request, cancel) =>
    {
        var path = request.RequestUri?.AbsolutePath ?? "";
        var action = path.Contains("/action/", StringComparison.Ordinal) ? path[(path.LastIndexOf("/action/", StringComparison.Ordinal) + 8)..] : "";
        // Lists are read with the same action that changes them; only a change is held back.
        var body = request.Content is null ? "" : await request.Content.ReadAsStringAsync(cancel);
        var write = Writes.Contains(action) && !(action == "user_list" && body.Contains("action=list", StringComparison.Ordinal));
        if (!write) return await inner(request, cancel);
        return new HttpResponseMessage(System.Net.HttpStatusCode.OK)
        {
            RequestMessage = request,
            Content = new ByteArrayContent(Encoding.UTF8.GetBytes("""{"success":true,"msg":"","result":{}}""")),
        };
    };
}
