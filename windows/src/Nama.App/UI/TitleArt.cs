using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.Core;

namespace Nama.App.UI;

/// <summary>
/// A title's name as its own artwork: the logo TMDB has for it, or else the name in large type with
/// its name in the other language under it. As TitleArt in MacKit.swift.
/// </summary>
public sealed class TitleArt : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Post _post;
    private readonly double _size;
    private readonly StackPanel _name;
    private bool _settled;

    public TitleArt(Post post, double size)
    {
        _post = post;
        _size = size;
        _name = NameArt();
        Children.Add(_name);
        // Known already, as on going back to a page: at once, with nothing to wait for.
        if (TmdbStore.Shared.Known(post.Id) is { } known)
        {
            if (known.Logo is { } url) ShowLogo(url, at: true);
            return;
        }
        // Nil while TMDB is being asked: the name is not shown yet, so it does not flash up and then
        // give way to the logo. It comes up out of a blur when it is known there is none.
        _name.Opacity = 0;
        _name.Translation = new System.Numerics.Vector3(0, 14, 0);
        _name.Springy(0.6);
        _ = LookAsync();
    }

    private async Task LookAsync()
    {
        // A slow answer is not waited on for long; the name shows, and a logo that comes later still replaces it.
        var ui = DispatcherQueue;
        _ = Task.Delay(2000).ContinueWith(_ => ui.TryEnqueue(Settle), TaskScheduler.Default);
        var info = await TmdbStore.Shared.InfoAsync(_post, App.Loc.Lang);
        if (info?.Logo is { } url) ShowLogo(url, at: false);
        else Settle();
    }

    /// <summary>Shows the name, there being no logo, or none in time.</summary>
    private void Settle()
    {
        if (_settled) return;
        _settled = true;
        // Behind the opening title, at once: fading, the title's blur missed it.
        if (SplashCurtain.IsUp) { _name.OpacityTransition = null; _name.TranslationTransition = null; }
        _name.Opacity = 1;
        _name.Translation = default;
    }

    private void ShowLogo(Uri url, bool at)
    {
        at |= SplashCurtain.IsUp;
        var loc = App.Loc;
        var column = new StackPanel { Spacing = 10, HorizontalAlignment = HorizontalAlignment.Left };
        // Held left to right, so a window laid out right to left does not show the logo mirrored.
        var frame = new Grid { FlowDirection = FlowDirection.LeftToRight, HorizontalAlignment = HorizontalAlignment.Left, MaxWidth = _size * 8, MaxHeight = _size * 2.4 };
        var image = new Image { Stretch = Stretch.Uniform, HorizontalAlignment = HorizontalAlignment.Left };
        frame.Children.Add(image);
        column.Children.Add(frame);
        // Only a name in another script: a series with no Persian name would repeat the logo.
        if (loc.Lang != Lang.En && SubSource.Simple(_post.Title(loc.Lang)) != SubSource.Simple(_post.Title(Lang.En)))
            column.Children.Add(Theme.Text(_post.Title(loc.Lang), _size * 0.38, FontWeights.SemiBold));
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(image, _post.Title(loc.Lang));
        column.Opacity = 0;
        if (!at)
        {
            column.Translation = new System.Numerics.Vector3(0, 14, 0);
            column.Springy(0.6);
        }
        var bitmap = new BitmapImage { DecodePixelWidth = (int)(_size * 8 * 1.5), DecodePixelType = DecodePixelType.Physical };
        bitmap.ImageOpened += (_, _) =>
        {
            _settled = true;
            _name.Visibility = Visibility.Collapsed;
            column.Opacity = 1;
            column.Translation = default;
        };
        bitmap.ImageFailed += (_, _) =>
        {
            Children.Remove(column);
            Settle();
        };
        // In the tree first: a picture outside it is never loaded.
        Children.Add(column);
        bitmap.UriSource = url;
        image.Source = bitmap;
    }

    private StackPanel NameArt()
    {
        var loc = App.Loc;
        var column = new StackPanel { Spacing = 14 };
        var name = Theme.Text(_post.Title(loc.Lang), _size, FontWeights.ExtraBold, lines: 2);
        name.LineHeight = _size * 1.15;
        name.Foreground = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
            GradientStops = { new GradientStop { Color = Colors.White, Offset = 0 }, new GradientStop { Color = Theme.Text1.WithAlpha(0.84), Offset = 1 } },
        };
        column.Children.Add(name);
        if (_post.AlternateTitle(loc.Lang) is { } other) column.Children.Add(Theme.Text(other, 18, FontWeights.Medium, Theme.Text2));
        return column;
    }
}
