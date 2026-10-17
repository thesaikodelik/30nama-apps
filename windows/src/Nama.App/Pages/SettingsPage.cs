using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
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
        layout.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(320) });
        layout.Children.Add(_column);
        var side = new StackPanel { Spacing = 18, Padding = new Thickness(0, 140, 0, 0) };
        side.Children.Add(new Image { Source = new BitmapImage(new Uri("ms-appx:///Assets/Logo.png")), Height = 72, HorizontalAlignment = HorizontalAlignment.Left, FlowDirection = FlowDirection.LeftToRight });
        side.Children.Add(Theme.Text(App.Loc[S.unofficial], 15, color: Theme.Text2, lines: 3));
        SetColumn(side, 1);
        layout.Children.Add(side);
        Children.Add(new ScrollViewer { Content = layout, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollMode = ScrollMode.Disabled });
        Render();
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
    }

    private void OnChanged(string what)
    {
        if (what is "user" or "settings") Render();
    }

    private void Render()
    {
        var loc = App.Loc;
        var s = App.Settings;
        _column.Children.Clear();

        var account = new List<FrameworkElement>();
        if (App.User is { } user)
        {
            account.Add(Row(loc[S.account], Value(user.Name ?? "—")));
            if (user.Type is { } type) account.Add(Row(loc[S.accountType], Value(loc.AccountType(type))));
            if (loc.SubscriptionLine(user) is { } line) account.Add(Row(loc[S.expires], Value(line.Replace(loc[S.subscriptionUntil], "").Trim())));
            account.Add(Action(Kit.Glass(loc[S.signOut], null, ConfirmSignOut, height: 38)));
        }
        else
        {
            account.Add(Row(loc[S.account], Value(loc[S.guestAccount])));
            account.Add(Action(Kit.Glass(loc[S.signInQR], null, App.RequestSignIn, height: 38)));
        }
        Group(loc[S.account], account);

        Group(loc[S.language], [Row(loc[S.language], Choice(LangExtensions.All.Select(l => (l.Name(), (object)l)), s.Lang, v => App.Update(x => x with { Lang = (Lang)v })))]);

        Group(loc[S.playback],
        [
            Row(loc[S.quality], Choice([(loc[S.qualityBest], false), (loc[S.qualityAuto], (object)true)], s.PreferAdaptive, v => App.Update(x => x with { PreferAdaptive = (bool)v }))),
            Row(loc[S.audio], Choice([(loc[S.audioStereo], AudioMix.Stereo), (loc[S.audioSurround], AudioMix.Surround), (loc[S.audioOriginal], (object)AudioMix.Original)], s.AudioMix, v => App.Update(x => x with { AudioMix = (AudioMix)v }))),
            Row(loc[S.autoplay], Toggle(s.AutoplayNext, v => App.Update(x => x with { AutoplayNext = v }))),
            Row(loc[S.autoSkip], Toggle(s.AutoSkip, v => App.Update(x => x with { AutoSkip = v }))),
            Row(loc[Extra.bufferTime], Choice(Settings.BufferChoices.Select(n => (n == 0 ? loc[S.qualityAuto] : $"{loc.Num(n)} {loc[Extra.seconds]}", (object)n)), s.BufferSeconds, v => App.Update(x => x with { BufferSeconds = (int)v }))),
            Row(loc[S.reduceMotion], Toggle(s.ReduceMotion, v => App.Update(x => x with { ReduceMotion = v }))),
        ]);

        Group(loc[S.subtitles],
        [
            Row(loc[S.defaultSubs], Choice([(loc[S.persian], "fa"), (loc[S.english], "en"), (loc[S.off], (object)"off")], s.DefaultSubtitle, v => App.Update(x => x with { DefaultSubtitle = (string)v }))),
            Row(loc[S.subSize], Choice([(loc[S.small], 38), (loc[S.medium], 46), (loc[S.large], (object)54)], s.SubtitleSize, v => App.Update(x => x with { SubtitleSize = (int)v }))),
            Row(loc[S.subColor], Choice([(loc[S.white], SubtitleColor.White), (loc[S.yellow], SubtitleColor.Yellow), (loc[S.cyan], SubtitleColor.Cyan), (loc[S.green], (object)SubtitleColor.Green)], s.SubtitleColor, v => App.Update(x => x with { SubtitleColor = (SubtitleColor)v }))),
            Row(loc[S.subBackground], Choice([(loc[S.bgNone], SubtitleBackground.None), (loc[S.bgHalf], SubtitleBackground.Half), (loc[S.bgSolid], (object)SubtitleBackground.Solid)], s.SubtitleBackground, v => App.Update(x => x with { SubtitleBackground = (SubtitleBackground)v }))),
        ]);

        var folder = Value(Downloads.Shared.Folder, ltr: true);
        folder.MaxWidth = 300;
        var folderRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        folderRow.Children.Add(folder);
        folderRow.Children.Add(Kit.Glass(loc[S.choose], null, ChooseFolder, height: 34));
        Group(loc[S.downloads],
        [
            Row(loc[S.downloadFolder], folderRow),
            Row(loc[Extra.downloadSpeed], Choice(Settings.LimitChoices.Select(k => (k == 0 ? loc[Extra.unlimited] : k >= 1024 ? $"{loc.Num(k / 1024)} MB/s" : $"{loc.Num(k)} KB/s", (object)k)), s.DownloadLimit, v => App.Update(x => x with { DownloadLimit = (int)v }))),
        ]);

        var version = typeof(SettingsPage).Assembly.GetName().Version;
        Group(loc[S.about],
        [
            Row(loc[S.version], Value(version is null ? "—" : $"{version.Major}.{version.Minor}.{version.Build}", ltr: true)),
            Row(loc[S.apiHost], Value(App.ApiHost is { } host && Uri.TryCreate(host, UriKind.Absolute, out var u) ? u.Host : "—", ltr: true)),
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

    private async void ChooseFolder()
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        picker.FileTypeFilter.Add("*");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(MainWindow.Shared));
        if (await picker.PickSingleFolderAsync() is { } chosen) App.Update(x => x with { DownloadFolder = chosen.Path });
    }

    private async void ConfirmSignOut()
    {
        var loc = App.Loc;
        var dialog = new ContentDialog
        {
            Title = loc[S.signOutTitle], Content = loc[S.signOutBody], PrimaryButtonText = loc[S.signOut], CloseButtonText = loc[S.cancel],
            DefaultButton = ContentDialogButton.Close, XamlRoot = XamlRoot, FlowDirection = loc.Lang.IsRtl() ? FlowDirection.RightToLeft : FlowDirection.LeftToRight,
            RequestedTheme = ElementTheme.Dark,
        };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary) App.SignOut();
    }
}
