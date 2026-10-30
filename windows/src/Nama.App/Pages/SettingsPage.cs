using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>Settings in grouped cards, as on the Mac: the account, the language, playback and subtitles.</summary>
public sealed class SettingsPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private readonly StackPanel _column = new() { Spacing = 10, MaxWidth = 690, Padding = new Thickness(0, 110, 0, 60) };

    public SettingsPage()
    {
        Children.Add(Kit.Ambient());
        var layout = new Grid { HorizontalAlignment = HorizontalAlignment.Center, ColumnSpacing = 110 };
        layout.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(690) });
        // As wide as the logo and its line, not more: the room left over is shared on both sides of the page.
        layout.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto, MaxWidth = 320 });
        layout.Children.Add(_column);
        var side = new StackPanel { Spacing = 18, Padding = new Thickness(0, 140, 0, 0) };
        side.Children.Add(new Image { Source = new BitmapImage(new Uri("ms-appx:///Assets/Logo.png")), Height = 72, HorizontalAlignment = HorizontalAlignment.Left, FlowDirection = FlowDirection.LeftToRight });
        side.Children.Add(Theme.Text(App.Loc[S.unofficial], 15, color: Theme.Text2, lines: 3));
        SetColumn(side, 1);
        layout.Children.Add(side);
        var scroller = new ScrollViewer { Content = layout, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
        // Beside the logo where there is room for both; in a narrow window the settings alone,
        // narrower with it, so nothing is cut at the window's edges.
        scroller.SizeChanged += (_, e) =>
        {
            var room = e.NewSize.Width - 2 * Theme.Edge;
            var both = room >= 690 + 110 + 320;
            side.Visibility = both ? Visibility.Visible : Visibility.Collapsed;
            layout.ColumnSpacing = both ? 110 : 0;
            layout.ColumnDefinitions[1].Width = both ? GridLength.Auto : new GridLength(0);
            layout.ColumnDefinitions[0].Width = new GridLength(Math.Max(280, Math.Min(690, room)));
        };
        Children.Add(scroller);
        Render();
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
    }

    /// <summary>Set while a field saves what is typed in it: drawn again, it would lose the typing.</summary>
    private static bool _typing;

    private void OnChanged(string what)
    {
        if (what is "user" or "settings" && !_typing) Render();
    }

    /// <summary>Saves a setting as it is typed, without drawing the page again under the field.</summary>
    private static void SaveTyped(Func<Settings, Settings> change)
    {
        _typing = true;
        try { App.Update(change); }
        finally { _typing = false; }
    }

    private void Render()
    {
        var loc = App.Loc;
        var s = App.Settings;
        _column.Children.Clear();

        // The account (its name, its subscription, signing out, the ratings export) is on the profile, as on the Mac.
        // For a member whose profile on the site is private: the site will not give their name, so it can be written here.
        if (App.User is not null)
        {
            var name = new TextBox { Text = s.DisplayName ?? "", Width = 252, FontFamily = Theme.Font(FontWeights.Normal), FontSize = 14, CornerRadius = new CornerRadius(16) };
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(name, loc[S.yourName]);
            name.TextChanged += (_, _) => SaveTyped(x => x with { DisplayName = string.IsNullOrWhiteSpace(name.Text) ? null : name.Text.Trim() });
            var field = new StackPanel { Spacing = 2 };
            field.Children.Add(Row($"{loc[S.yourName]} ({loc[S.optional]})", name));
            var note = Theme.Text(loc[Extra.yourNameHintHere], 12, color: Theme.Text3, lines: 2);
            note.Margin = new Thickness(0, 0, 0, 8);
            field.Children.Add(note);
            Group(loc[S.profile], [field]);
        }

        Group(loc[S.language], [Row(loc[S.language], Choice(LangExtensions.All.Select(l => (l.Name(), (object)l)), s.Lang, v => App.Update(x => x with { Lang = (Lang)v })))]);

        Group(loc[S.playback],
        [
            // 1080p, 720p and 480p for a slow line, beside the best, adaptive and the original file.
            Row(loc[S.quality], Choice([(loc[S.qualityBest], "best"), (loc[S.qualityAuto], "auto"), (loc[Extra.qualityMax], "max"), ("1080p", "1080"), ("720p", "720"), ("480p", (object)"480")],
                s.MaxQuality ? "max" : s.QualityCap > 0 ? s.QualityCap.ToString() : s.PreferAdaptive ? "auto" : "best",
                v => App.Update(x => x with
                {
                    PreferAdaptive = (string)v == "auto", MaxQuality = (string)v == "max",
                    QualityCap = int.TryParse((string)v, out var cap) ? cap : 0,
                }))),
            Row(loc[S.audio], Choice([(loc[S.audioStereo], AudioMix.Stereo), (loc[S.audioSurround], AudioMix.Surround), (loc[S.audioOriginal], (object)AudioMix.Original)], s.AudioMix, v => App.Update(x => x with { AudioMix = (AudioMix)v }))),
            Row(loc[S.autoplay], Toggle(s.AutoplayNext, v => App.Update(x => x with { AutoplayNext = v }))),
            Row(loc[S.autoSkip], Toggle(s.AutoSkip, v => App.Update(x => x with { AutoSkip = v }))),
            Row(loc[S.aroundPicture], Choice([(loc[S.blackBars], false), (loc[S.sceneGlow], (object)true)], s.SceneGlow, v => App.Update(x => x with { SceneGlow = (bool)v }))),
            Row(loc[S.bufferTime], Choice(Settings.BufferChoices.Select(n => (n == 0 ? loc[S.qualityAuto] : $"{loc.Num(n)} {loc[S.seconds]}", (object)n)), s.BufferSeconds, v => App.Update(x => x with { BufferSeconds = (int)v }))),
            Row(loc[S.reduceMotion], Toggle(s.ReduceMotion, v => App.Update(x => x with { ReduceMotion = v }))),
            Hinted(Row(loc[Extra.dataSaver], Toggle(s.DataSaver, v => App.Update(x => x with { DataSaver = v }))), loc[Extra.dataSaverHint]),
            Row(loc[Extra.clearCaches], CacheButton()),
        ]);

        Group(loc[S.subtitles],
        [
            Row(loc[S.defaultSubs], Choice([(loc[S.persian], "fa"), (loc[S.english], "en"), (loc[S.off], (object)"off")], s.DefaultSubtitle, v => App.Update(x => x with { DefaultSubtitle = (string)v }))),
            Row(loc[S.subSize], Choice([(loc[S.small], 38), (loc[S.medium], 46), (loc[S.large], (object)54)], s.SubtitleSize, v => App.Update(x => x with { SubtitleSize = (int)v }))),
            Row(loc[S.subColor], Choice([(loc[S.white], SubtitleColor.White), (loc[S.yellow], SubtitleColor.Yellow), (loc[S.cyan], SubtitleColor.Cyan), (loc[S.green], (object)SubtitleColor.Green)], s.SubtitleColor, v => App.Update(x => x with { SubtitleColor = (SubtitleColor)v }))),
            Row(loc[S.subBackground], Choice([(loc[S.bgNone], SubtitleBackground.None), (loc[S.bgHalf], SubtitleBackground.Half), (loc[S.bgSolid], (object)SubtitleBackground.Solid)], s.SubtitleBackground, v => App.Update(x => x with { SubtitleBackground = (SubtitleBackground)v }))),
            // What these choices make, drawn as the player draws it.
            SubtitleLook.Sample(s, loc[S.sample]),
        ]);

        var folder = Value(Downloads.Shared.Folder, ltr: true);
        folder.MaxWidth = 300;
        var folderRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        folderRow.Children.Add(folder);
        folderRow.Children.Add(Kit.Glass(loc[S.choose], null, ChooseFolder, height: 34));
        Group(loc[S.downloads],
        [
            Row(loc[S.downloadFolder], folderRow),
            Row(loc[Extra.keepInTray], Toggle(s.KeepInTray, v => App.Update(x => x with { KeepInTray = v }))),
            Hours(s),
            Row(loc[S.downloadsAtOnce], Choice(Settings.AtOnceChoices.Select(n => (loc.Num(n), (object)n)), s.DownloadsAtOnce, v => App.Update(x => x with { DownloadsAtOnce = (int)v }))),
            Row(loc[S.downloadSpeed], Choice(Settings.LimitChoices.Select(k => (k == 0 ? loc[S.unlimited] : k >= 1024 ? $"{loc.Num(k / 1024)} MB/s" : $"{loc.Num(k)} KB/s", (object)k)), s.DownloadLimit, v => App.Update(x => x with { DownloadLimit = (int)v }))),
        ]);

        Group(loc[S.services],
        [
            KeyField("subsource", loc[S.subsourceKey], loc[S.subsourceKeyHint], s.SubsourceKey, v => SaveTyped(x => x with { SubsourceKey = v })),
            KeyField("tmdb", loc[S.tmdbKey], loc[S.tmdbKeyHint], s.TmdbKey, v => SaveTyped(x => x with { TmdbKey = v })),
            KeyField("gemini", loc[Extra.geminiKey], loc[Extra.geminiKeyHint], s.GeminiKey, v => SaveTyped(x => x with { GeminiKey = v })),
        ]);

        var version = typeof(SettingsPage).Assembly.GetName().Version;
        Group(loc[S.about],
        [
            // The app looks by itself every hour; this is for when a release was just announced.
            Row(loc[S.version], VersionRow(version is null ? "—" : $"{version.Major}.{version.Minor}.{version.Build}")),
            Row(loc[S.apiHost], Value(App.ApiHost is { } host && Uri.TryCreate(host, UriKind.Absolute, out var u) ? u.Host : "—", ltr: true)),
            Row(loc[S.reportProblem], Kit.Glass(loc[S.reportProblem], null, ReportProblem, height: 34)),
        ]);
    }

    private void Group(string title, IEnumerable<FrameworkElement> rows)
    {
        var heading = Theme.Text(title, 14, FontWeights.SemiBold, Theme.Text3);
        heading.Margin = new Thickness(18, 14, 18, 0);
        _column.Children.Add(heading);
        var card = new StackPanel { Spacing = 2, Padding = new Thickness(18, 8, 18, 8) };
        foreach (var row in rows) card.Children.Add(row);
        _column.Children.Add(new Border
        {
            CornerRadius = new CornerRadius(18), Background = Theme.White(0.05), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.08), Child = card,
        });
    }

    private static FrameworkElement Hinted(FrameworkElement row, string hint)
    {
        ToolTipService.SetToolTip(row, hint);
        return row;
    }

    /// <summary>How much the caches hold, and a button that clears them; the size is counted away from the UI thread.</summary>
    private static FrameworkElement CacheButton()
    {
        var loc = App.Loc;
        var size = Theme.Text("", 14, color: Theme.Text3);
        size.VerticalAlignment = VerticalAlignment.Center;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        row.Children.Add(size);
        async Task Count()
        {
            var bytes = await Task.Run(Storage.CacheBytes);
            size.Text = $"{loc.Num((int)(bytes >> 20))} {loc[Extra.megabytes]}";
        }
        row.Children.Add(Kit.Glass(loc[Extra.clear], null, async () =>
        {
            await Task.Run(Storage.ClearCaches);
            await Count();
            App.ShowToast(loc[Extra.cleared]);
        }, height: 34));
        _ = Count();
        return row;
    }

    private static FrameworkElement Row(string label, FrameworkElement control)
    {
        var row = new Grid { MinHeight = 50 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var text = Theme.Text(label, 16, FontWeights.SemiBold);
        text.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(text);
        control.VerticalAlignment = VerticalAlignment.Center;
        SetColumn(control, 1);
        row.Children.Add(control);
        return row;
    }

    private static FrameworkElement Action(Button button)
    {
        button.HorizontalAlignment = HorizontalAlignment.Left;
        button.Margin = new Thickness(0, 6, 0, 6);
        return button;
    }

    private static TextBlock Value(string text, bool ltr = false)
    {
        var t = Theme.Text(text, 15, color: Theme.Text2);
        if (ltr) t.FlowDirection = FlowDirection.LeftToRight;
        return t;
    }

    /// <summary>
    /// The hours downloads may run in: any time, or from one hour to another. The second choice
    /// shows only once the first is made.
    /// </summary>
    private static FrameworkElement Hours(Settings s)
    {
        var loc = App.Loc;
        string Hour(int h) => loc.Num($"{h:00}:00");
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        var from = Choice(new (string, object)[] { (loc[S.anyTime], -1) }.Concat(Enumerable.Range(0, 24).Select(h => (Hour(h), (object)h))),
            s.DownloadFrom ?? -1, v => App.Update(x => (int)v < 0
                ? x with { DownloadFrom = null, DownloadTo = null }
                : x with { DownloadFrom = (int)v, DownloadTo = x.DownloadTo ?? ((int)v + 6) % 24 }));
        from.MinWidth = 150;
        controls.Children.Add(from);
        if (s.DownloadFrom is not null)
        {
            var to = Theme.Text(loc[S.hoursTo], 15, color: Theme.Text2);
            to.VerticalAlignment = VerticalAlignment.Center;
            controls.Children.Add(to);
            var until = Choice(Enumerable.Range(0, 24).Select(h => (Hour(h), (object)h)), s.DownloadTo ?? 0, v => App.Update(x => x with { DownloadTo = (int)v }));
            until.MinWidth = 120;
            controls.Children.Add(until);
        }
        var column = new StackPanel { Spacing = 2 };
        column.Children.Add(Row(loc[S.downloadHours], controls));
        var note = Theme.Text(loc[S.downloadHoursHint], 12, color: Theme.Text3, lines: 2);
        note.Margin = new Thickness(0, 0, 0, 8);
        column.Children.Add(note);
        return column;
    }

    private static ComboBox Choice(IEnumerable<(string Label, object Value)> options, object current, Action<object> pick)
    {
        var list = options.ToList();
        var box = new ComboBox { MinWidth = 280, CornerRadius = new CornerRadius(18), FontFamily = Theme.Font(FontWeights.SemiBold) };
        foreach (var (label, _) in list) box.Items.Add(label);
        box.SelectedIndex = Math.Max(0, list.FindIndex(o => o.Value.Equals(current)));
        box.SelectionChanged += (_, _) =>
        {
            if (box.SelectedIndex >= 0 && !list[box.SelectedIndex].Value.Equals(current)) pick(list[box.SelectedIndex].Value);
        };
        return box;
    }

    private static ToggleSwitch Toggle(bool on, Action<bool> set)
    {
        var toggle = new ToggleSwitch { IsOn = on, OnContent = "", OffContent = "", MinWidth = 0 };
        toggle.Toggled += (_, _) => set(toggle.IsOn);
        return toggle;
    }

    private static FrameworkElement VersionRow(string version)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        var number = Value(App.Loc.Num(version), ltr: true);
        number.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(number);
        Button? check = null;
        check = Kit.Glass(App.Loc[S.checkForUpdate], null, async () =>
        {
            check!.IsEnabled = false;
            try
            {
                switch (await Updater.Shared.LookAsync(asked: true))
                {
                    case Updater.Looks.UpToDate: App.ShowToast(App.Loc[S.upToDate]); break;
                    case Updater.Looks.NoAnswer: App.ShowToast(App.Loc[S.error]); break;
                    // Found: the update's own card says so. Null: one is already on its way.
                }
            }
            finally { check.IsEnabled = true; }
        }, height: 34);
        row.Children.Add(check);
        return row;
    }

    /// <summary>
    /// The user's own key for a service, kept on this computer, saved as it is typed or pasted. With
    /// one kept, the field says so: the app's red on its edge and on a tick under it, a button to
    /// show or hide it, and one to remove it. As KeyField on the Mac.
    /// </summary>
    private static FrameworkElement KeyField(string service, string label, string hint, string? value, Action<string?> set)
    {
        var loc = App.Loc;
        var shown = false;
        var mono = new FontFamily("Cascadia Mono,Consolas");
        var secret = new PasswordBox { Password = value ?? "", FontFamily = mono, FontSize = 13 };
        var plain = new TextBox { Text = value ?? "", FontFamily = mono, FontSize = 13, Visibility = Visibility.Collapsed };
        foreach (var field in new Control[] { secret, plain })
        {
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(field, label);
            field.VerticalAlignment = VerticalAlignment.Center;
            field.BorderThickness = new Thickness(0);
            field.Background = Theme.White(0);
            field.MinWidth = 0;
            field.MinHeight = 0;
            // As tall as its line, in the middle of the capsule: the box's own least height (32, from the
            // theme, inside its template) kept the text at the top of a taller box.
            field.Padding = new Thickness(0);
            field.Resources["TextControlThemeMinHeight"] = 0.0;
            field.Resources["TextControlThemePadding"] = new Thickness(0);
            field.Resources["TextControlBackgroundFocused"] = Theme.White(0);
            field.Resources["TextControlBackgroundPointerOver"] = Theme.White(0);
            field.Resources["TextControlBorderBrushFocused"] = Theme.White(0);
            field.Resources["TextControlBorderBrushPointerOver"] = Theme.White(0);
        }
        var eye = Glyph.Icon("\uE7B3", 12, Theme.Text3);
        var reveal = Theme.Bare(eye, () => { });
        var remove = Theme.Bare(Glyph.Icon("\uE711", 11, Theme.Text3), () => { });
        reveal.VerticalAlignment = remove.VerticalAlignment = VerticalAlignment.Center;
        Kit.AutomationName(remove, loc[S.removeKey]);
        ToolTipService.SetToolTip(remove, loc[S.removeKey]);
        var inner = new Grid { ColumnSpacing = 8, Margin = new Thickness(12, 0, 12, 0) };
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        inner.Children.Add(secret);
        inner.Children.Add(plain);
        Grid.SetColumn(reveal, 1);
        Grid.SetColumn(remove, 2);
        inner.Children.Add(reveal);
        inner.Children.Add(remove);
        // Left to right in every language: a key is Latin letters and digits.
        var capsule = new Border { Width = 252, Height = 32, CornerRadius = new CornerRadius(16), Background = Theme.White(0.08), BorderThickness = new Thickness(1), Child = inner, FlowDirection = FlowDirection.LeftToRight };

        var note = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, Margin = new Thickness(0, 0, 0, 8) };
        var tick = Glyph.Icon("\uE73E", 12, Theme.Accent);
        tick.VerticalAlignment = VerticalAlignment.Center;
        var words = Theme.Text("", 12, color: Theme.Text3, lines: 2);

        var current = value?.Trim() ?? "";
        // Saved first, then tried a moment after the typing stops (Android's KeyCheck): an
        // unreachable service keeps the key, and only a refusal says it is wrong.
        var state = KeyState.Unset;
        CancellationTokenSource? trying = null;
        async void Try()
        {
            trying?.Cancel();
            if (current.Length == 0) { state = KeyState.Unset; return; }
            var mine = trying = new CancellationTokenSource();
            var key = current;
            state = KeyState.Checking;
            Show();
            try { await Task.Delay(800, mine.Token); } catch (TaskCanceledException) { return; }
            var found = await KeyCheck.CheckAsync(ApiClient.HttpTransport(), service, key);
            if (mine.IsCancellationRequested || key != current) return;
            state = found;
            Show();
        }
        void Show()
        {
            var saved = current.Length > 0;
            var rejected = saved && state == KeyState.Rejected;
            // The app's own red, on the edge and the tick only: the words in the usual colour, so it does not read as an error.
            capsule.BorderBrush = new SolidColorBrush(Theme.Accent.WithAlpha(saved ? 0.55 : 0));
            reveal.Visibility = remove.Visibility = saved ? Visibility.Visible : Visibility.Collapsed;
            note.Children.Clear();
            if (saved && !rejected && state != KeyState.Checking) note.Children.Add(tick);
            words.Text = !saved ? hint : state switch
            {
                KeyState.Checking => loc[Extra.keyChecking],
                KeyState.Works => loc[Extra.keyWorks] + " · " + loc[Extra.keySavedHere],
                KeyState.Rejected => loc[Extra.keyRejected],
                KeyState.Unreachable => loc[Extra.keyUnreachable],
                _ => loc[Extra.keySavedHere],
            };
            // A wrong key is the one thing said in the app's red.
            words.Foreground = rejected ? new SolidColorBrush(Theme.Accent) : Theme.Brush(saved ? Theme.Text2 : Theme.Text3);
            note.Children.Add(words);
            eye.Glyph = shown ? "\uED1A" : "\uE7B3";
            var name = shown ? loc[S.hideKey] : loc[S.showKey];
            Kit.AutomationName(reveal, name);
            ToolTipService.SetToolTip(reveal, name);
            secret.Visibility = shown ? Visibility.Collapsed : Visibility.Visible;
            plain.Visibility = shown ? Visibility.Visible : Visibility.Collapsed;
        }
        void Typed(string text)
        {
            if (text.Trim() == current) return;
            current = text.Trim();
            if (secret.Password != text) secret.Password = text;
            if (plain.Text != text) plain.Text = text;
            set(current.Length == 0 ? null : current);
            if (current.Length == 0) shown = false;
            state = KeyState.Unset;
            Show();
            Try();
        }
        secret.PasswordChanged += (_, _) => Typed(secret.Password);
        plain.TextChanged += (_, _) => Typed(plain.Text);
        reveal.Click += (_, _) => { shown = !shown; Show(); };
        remove.Click += (_, _) => Typed("");
        Show();

        var column = new StackPanel { Spacing = 2 };
        column.Children.Add(Row(label, capsule));
        column.Children.Add(note);
        return column;
    }

    /// <summary>The ratings as a file Letterboxd's importer reads: the app cannot send them to 30nama, so this is how they leave it.</summary>
    /// <summary>
    /// A new issue on the project's GitHub page, with what a report needs to be acted on already
    /// filled in. Nothing about the account goes with it.
    /// </summary>
    private void ReportProblem()
    {
        var version = typeof(SettingsPage).Assembly.GetName().Version?.ToString(3) ?? "?";
        var body = $"""
            **What happened / چه اتفاقی افتاد:**


            **What I expected / چه انتظاری داشتم:**


            ---
            30nama for Windows {version} · {Environment.OSVersion.VersionString} · {App.Settings.Lang.Code()}
            """;
        var url = "https://github.com/thesaikodelik/30nama-apps/issues/new?body=" + Uri.EscapeDataString(body);
        _ = Windows.System.Launcher.LaunchUriAsync(new Uri(url));
    }

    private async void ChooseFolder()
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        picker.FileTypeFilter.Add("*");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(MainWindow.Shared));
        if (await picker.PickSingleFolderAsync() is { } chosen) App.Update(x => x with { DownloadFolder = chosen.Path });
    }

}
