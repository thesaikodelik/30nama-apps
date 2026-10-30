using System.Globalization;

namespace Nama.Core;

/// <summary>
/// Where the window was when it was last closed, in screen pixels, and whether it filled the screen,
/// so it opens again as it was left (a user on Twitter: closed full, it opened small). Full screen,
/// which only the player asks for, comes back as filling the screen without being full screen.
/// </summary>
public sealed record WindowPlace(int X, int Y, int Width, int Height, bool Maximized)
{
    /// <summary>How much of the window has to be on a screen for the place to be kept: enough to take hold of.</summary>
    private const int Grip = 120;

    public string Text => string.Join(',', new[] { X, Y, Width, Height, Maximized ? 1 : 0 }.Select(n => n.ToString(CultureInfo.InvariantCulture)));

    public static WindowPlace? Parse(string? text)
    {
        var parts = text?.Split(',');
        if (parts is not { Length: 5 }) return null;
        var n = new int[5];
        for (var i = 0; i < 5; i++)
        {
            if (!int.TryParse(parts[i], NumberStyles.Integer, CultureInfo.InvariantCulture, out n[i])) return null;
        }
        if (n[2] < 200 || n[3] < 200) return null;
        return new WindowPlace(n[0], n[1], n[2], n[3], n[4] == 1);
    }

    /// <summary>
    /// Whether the place is still on one of the screens' work areas, its top (where it is taken hold
    /// of) included: a monitor unplugged since would leave it out of reach.
    /// </summary>
    public bool IsOn(IEnumerable<(int X, int Y, int Width, int Height)> areas) => areas.Any(a =>
    {
        var left = Math.Max(X, a.X);
        var right = Math.Min(X + Width, a.X + a.Width);
        var top = Math.Max(Y, a.Y);
        return right - left >= Grip && Y >= a.Y - 8 && Y < a.Y + a.Height - Grip && top < a.Y + a.Height;
    });
}
