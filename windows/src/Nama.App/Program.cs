using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;

namespace Nama.App;

/// <summary>
/// Where the program starts. One copy runs at a time: opening it again brings the window that
/// is already there to the front, even from the notification area, instead of a second app that
/// would fetch the same downloads twice.
/// </summary>
public static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        WinRT.ComWrappersSupport.InitializeComWrappers();
        var instance = AppInstance.FindOrRegisterForKey("30nama-main");
        if (!instance.IsCurrent)
        {
            // Handed to the copy that is running, which shows its window; this one leaves.
            var handed = new ManualResetEventSlim();
            _ = Task.Run(async () =>
            {
                await instance.RedirectActivationToAsync(AppInstance.GetCurrent().GetActivatedEventArgs());
                handed.Set();
            });
            handed.Wait(TimeSpan.FromSeconds(5));
            return 0;
        }
        instance.Activated += (_, _) => App.BringToFront();
        Application.Start(callback =>
        {
            SynchronizationContext.SetSynchronizationContext(new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread()));
            _ = new App();
        });
        return 0;
    }
}
