using System.Runtime.InteropServices;

namespace Nama.App.Player;

/// <summary>The parts of libmpv's client API the player uses, as declared in mpv/client.h.</summary>
internal static partial class Mpv
{
    private const string Lib = "libmpv-2.dll";

    public const int FormatNone = 0, FormatString = 1, FormatFlag = 3, FormatInt64 = 4, FormatDouble = 5;

    public enum EventId
    {
        None = 0, Shutdown = 1, LogMessage = 2, CommandReply = 5, StartFile = 6, EndFile = 7,
        FileLoaded = 8, VideoReconfig = 17, AudioReconfig = 18, Seek = 20, PlaybackRestart = 21, PropertyChange = 22,
    }

    public enum EndFileReason { Eof = 0, Stop = 2, Quit = 3, Error = 4, Redirect = 5 }

    [StructLayout(LayoutKind.Sequential)]
    public struct Event
    {
        public EventId Id;
        public int Error;
        public ulong ReplyUserdata;
        public IntPtr Data;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct EventProperty
    {
        public IntPtr Name;
        public int Format;
        public IntPtr Data;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct EventEndFile
    {
        public EndFileReason Reason;
        public int Error;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct EventLogMessage
    {
        public IntPtr Prefix;
        public IntPtr Level;
        public IntPtr Text;
    }

    [LibraryImport(Lib, EntryPoint = "mpv_create")]
    public static partial IntPtr Create();

    [LibraryImport(Lib, EntryPoint = "mpv_initialize")]
    public static partial int Initialize(IntPtr handle);

    [LibraryImport(Lib, EntryPoint = "mpv_terminate_destroy")]
    public static partial void TerminateDestroy(IntPtr handle);

    [LibraryImport(Lib, EntryPoint = "mpv_set_option_string", StringMarshalling = StringMarshalling.Utf8)]
    public static partial int SetOptionString(IntPtr handle, string name, string value);

    [LibraryImport(Lib, EntryPoint = "mpv_request_log_messages", StringMarshalling = StringMarshalling.Utf8)]
    public static partial int RequestLogMessages(IntPtr handle, string level);

    [LibraryImport(Lib, EntryPoint = "mpv_observe_property", StringMarshalling = StringMarshalling.Utf8)]
    public static partial int ObserveProperty(IntPtr handle, ulong replyUserdata, string name, int format);

    [LibraryImport(Lib, EntryPoint = "mpv_command_async")]
    public static partial int CommandAsync(IntPtr handle, ulong replyUserdata, IntPtr[] args);

    [LibraryImport(Lib, EntryPoint = "mpv_wait_event")]
    public static partial IntPtr WaitEvent(IntPtr handle, double timeout);

    [LibraryImport(Lib, EntryPoint = "mpv_wakeup")]
    public static partial void Wakeup(IntPtr handle);

    [LibraryImport(Lib, EntryPoint = "mpv_get_property_string", StringMarshalling = StringMarshalling.Utf8)]
    public static partial IntPtr GetPropertyString(IntPtr handle, string name);

    [LibraryImport(Lib, EntryPoint = "mpv_get_property", StringMarshalling = StringMarshalling.Utf8)]
    public static partial int GetProperty(IntPtr handle, string name, int format, out long data);

    [LibraryImport(Lib, EntryPoint = "mpv_free")]
    public static partial void Free(IntPtr data);

    /// <summary>Hands mpv a command without waiting for it to be carried out.</summary>
    public static void Command(IntPtr handle, params string[] words)
    {
        var args = new IntPtr[words.Length + 1];
        try
        {
            for (var i = 0; i < words.Length; i++) args[i] = Marshal.StringToCoTaskMemUTF8(words[i]);
            CommandAsync(handle, 0, args);
        }
        finally
        {
            foreach (var p in args) if (p != IntPtr.Zero) Marshal.FreeCoTaskMem(p);
        }
    }

    public static string? Utf8(IntPtr p) => p == IntPtr.Zero ? null : Marshal.PtrToStringUTF8(p);
}
