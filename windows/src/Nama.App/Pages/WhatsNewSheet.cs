using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Documents;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// What each version brought, over the window: the version sections of the Windows release's text
/// on GitHub. That text is written in Persian, so it reads right to left whatever the app's
/// language. As WhatsNewSheet.swift.
/// </summary>
public sealed class WhatsNewSheet : Grid
{
    private static Loc Loc => AppModel.Shared.Loc;

    private readonly Action _close;
    private readonly Grid _body = new() { Height = 520 };

    public WhatsNewSheet(Action close)
    {
        _close = close;
        var dim = new Grid { Background = Theme.Black(0.6) };
        dim.Tapped += (_, e) => { if (ReferenceEquals(e.OriginalSource, dim)) _close(); };
        Children.Add(dim);

        var pane = new StackPanel { Width = 640, Spacing = 14, Padding = new Thickness(24), VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
        var card = new Grid { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(40, 70, 40, 40) };
        card.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.12) });
        card.Children.Add(pane);

        var header = new Grid { ColumnSpacing = 12 };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var title = Theme.Text(Loc[S.whatsNew], 17, FontWeights.Bold);
        title.VerticalAlignment = VerticalAlignment.Center;
        header.Children.Add(title);
        var shut = Kit.Glass(null, Glyph.Close, _close, height: 30);
        Kit.AutomationName(shut, Loc[S.close]);
        SetColumn(shut, 1);
        header.Children.Add(shut);
        pane.Children.Add(header);
        if (Loc.Lang != Lang.Fa) pane.Children.Add(Theme.Text(Loc[S.notesInPersian], 13, color: Theme.Text3));

        _body.Children.Add(new ProgressRing { IsActive = true, Width = 28, Height = 28 });
        pane.Children.Add(_body);
        Children.Add(card);

        var escape = new Microsoft.UI.Xaml.Input.KeyboardAccelerator { Key = Windows.System.VirtualKey.Escape };
        escape.Invoked += (_, e) => { e.Handled = true; _close(); };
        KeyboardAccelerators.Add(escape);
        Loaded += async (_, _) => Show(await Updater.Shared.WhatsNewAsync());
    }

    private void Show(IReadOnlyList<ReleaseNotes.Block>? blocks)
    {
        _body.Children.Clear();
        if (blocks is null)
        {
            var failed = Theme.Text(Loc[S.error], 15, FontWeights.Medium, Theme.Text2);
            failed.HorizontalAlignment = HorizontalAlignment.Center;
            failed.VerticalAlignment = VerticalAlignment.Center;
            _body.Children.Add(failed);
            return;
        }
        var list = new StackPanel { Spacing = 8, FlowDirection = FlowDirection.RightToLeft, Padding = new Thickness(0, 0, 0, 12) };
        foreach (var block in blocks) list.Children.Add(Line(block));
        _body.Children.Add(new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden });
    }

    private static FrameworkElement Line(ReleaseNotes.Block block)
    {
        switch (block.Kind)
        {
            case ReleaseNotes.Kinds.Version:
                var version = Theme.Text(block.Text, 18, FontWeights.Bold);
                version.Margin = new Thickness(0, 14, 0, 0);
                return version;
            case ReleaseNotes.Kinds.Group:
                var group = Theme.Text(block.Text, 14, FontWeights.SemiBold, Theme.Text2);
                group.Margin = new Thickness(0, 6, 0, 0);
                return group;
            case ReleaseNotes.Kinds.Item:
                var row = new Grid { ColumnSpacing = 8 };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                row.Children.Add(Theme.Text("•", 14, color: Theme.Text3));
                var text = Inline(block.Text);
                SetColumn(text, 1);
                row.Children.Add(text);
                return row;
            default:
                return Inline(block.Text);
        }
    }

    /// <summary>Bold and code in a line, as GitHub shows them.</summary>
    private static TextBlock Inline(string text)
    {
        var block = Theme.Text("", 14, lines: 0);
        block.TextTrimming = TextTrimming.None;
        block.Inlines.Clear();
        var bold = false;
        var code = false;
        var run = new System.Text.StringBuilder();
        void Flush()
        {
            if (run.Length == 0) return;
            var piece = new Run { Text = run.ToString() };
            if (bold) { piece.FontWeight = FontWeights.Bold; piece.FontFamily = Theme.Font(FontWeights.Bold); }
            if (code) piece.FontFamily = new Microsoft.UI.Xaml.Media.FontFamily("Cascadia Mono,Consolas");
            block.Inlines.Add(piece);
            run.Clear();
        }
        for (var i = 0; i < text.Length; i++)
        {
            if (!code && i + 1 < text.Length && text[i] == '*' && text[i + 1] == '*') { Flush(); bold = !bold; i++; }
            else if (text[i] == '`') { Flush(); code = !code; }
            else run.Append(text[i]);
        }
        Flush();
        return block;
    }
}
