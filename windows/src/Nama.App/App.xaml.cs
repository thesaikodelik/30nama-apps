using Microsoft.UI.Xaml;

namespace Nama.App;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
        RequestedTheme = ApplicationTheme.Dark;
        // What went wrong, kept where it can be read after the window is gone.
        UnhandledException += (_, e) => Log(e.Exception?.ToString() ?? e.Message);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log(e.ExceptionObject?.ToString() ?? "");
        TaskScheduler.UnobservedTaskException += (_, e) => Log(e.Exception.ToString());
    }

    public static void Log(string text)
    {
        try { File.AppendAllText(Path.Combine(Model.Storage.Folder, "errors.log"), $"{DateTime.Now:u} {text}{Environment.NewLine}{Environment.NewLine}"); }
        catch (Exception) { }
    }

    /// <summary>Another start of the program asked for this one: its window comes forward.</summary>
    public static void BringToFront() => MainWindow.Shared?.DispatcherQueue.TryEnqueue(MainWindow.Shared.ShowAgain);

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }
}
