using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using Windows.Graphics.Effects;
using WinRT;

namespace Nama.App.UI;

/// <summary>
/// Direct2D's Gaussian blur, described to the compositor the way it asks to be told of an effect:
/// an object with IGraphicsEffect, IGraphicsEffectSource and IGraphicsEffectD2D1Interop. The
/// compositor reads the effect's id, its properties and its source from it, and draws the blur
/// itself. This is all Win2D's GaussianBlurEffect was used for, and it is done here so that no
/// library with a licence of its own goes into the installer.
/// </summary>
internal sealed unsafe class GaussianBlur
{
    /// <summary>CLSID_D2D1GaussianBlur.</summary>
    private static readonly Guid EffectId = new("1FEB6D69-2FE6-4AC9-8C58-1D7F93E7A6A5");

    public enum Borders : uint { Soft = 0, Hard = 1 }

    /// <summary>The name its properties are animated by: "<c>{Name}.BlurAmount</c>".</summary>
    public string Name { get; init; } = "Blur";
    /// <summary>The blur's standard deviation, as Win2D's BlurAmount is.</summary>
    public float Amount { get; init; } = 3;
    public Borders Border { get; init; } = Borders.Soft;
    /// <summary>The effect's input: a CompositionEffectSourceParameter.</summary>
    public required IGraphicsEffectSource Source { get; init; }

    /// <summary>The effect as the compositor takes it, in CreateEffectFactory.</summary>
    public IGraphicsEffect AsEffect()
    {
        var unknown = Wrappers.Instance.GetOrCreateComInterfaceForObject(this, CreateComInterfaceFlags.None);
        try { return MarshalInterface<IGraphicsEffect>.FromAbi(unknown); }
        finally { Marshal.Release(unknown); }
    }

    // MARK: COM

    private static readonly Guid IidInspectable = new("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90");
    private static readonly Guid IidGraphicsEffect = new("CB51C0CE-8FE6-4636-B202-861FAA07D8F3");
    private static readonly Guid IidGraphicsEffectSource = new("2D8F9DDC-4339-4EB9-9216-F9DEB75658A2");
    private static readonly Guid IidD2D1Interop = new("2FC57384-A068-44D7-A331-30982FCF7177");
    private static readonly Guid IidPropertyValue = new("4BD682DD-7554-40E9-9A9B-82654EDE7E62");

    private const int Ok = 0, NotImplemented = unchecked((int)0x80004001), InvalidArgument = unchecked((int)0x80070057), OutOfMemory = unchecked((int)0x8007000E);
    /// <summary>GRAPHICS_EFFECT_PROPERTY_MAPPING_DIRECT: the value is the D2D property as it is.</summary>
    private const int MappingDirect = 1;

    private static GaussianBlur Self(void* that) => ComWrappers.ComInterfaceDispatch.GetInstance<GaussianBlur>((ComWrappers.ComInterfaceDispatch*)that);

    private sealed class Wrappers : ComWrappers
    {
        public static readonly Wrappers Instance = new();
        private static readonly ComInterfaceEntry* Entries = Build();

        private static ComInterfaceEntry* Build()
        {
            GetIUnknownImpl(out var query, out var addRef, out var release);
            // IInspectable's three, after IUnknown's.
            nint* Inspectable(int extra)
            {
                var table = (nint*)RuntimeHelpers.AllocateTypeAssociatedMemory(typeof(GaussianBlur), sizeof(nint) * (6 + extra));
                table[0] = query; table[1] = addRef; table[2] = release;
                table[3] = (nint)(delegate* unmanaged<void*, uint*, Guid**, int>)&GetIids;
                table[4] = (nint)(delegate* unmanaged<void*, nint*, int>)&GetRuntimeClassName;
                table[5] = (nint)(delegate* unmanaged<void*, int*, int>)&GetTrustLevel;
                return table;
            }
            var effect = Inspectable(2);
            effect[6] = (nint)(delegate* unmanaged<void*, nint*, int>)&GetName;
            effect[7] = (nint)(delegate* unmanaged<void*, nint, int>)&PutName;
            var source = Inspectable(0);
            var interop = (nint*)RuntimeHelpers.AllocateTypeAssociatedMemory(typeof(GaussianBlur), sizeof(nint) * 9);
            interop[0] = query; interop[1] = addRef; interop[2] = release;
            interop[3] = (nint)(delegate* unmanaged<void*, Guid*, int>)&GetEffectId;
            interop[4] = (nint)(delegate* unmanaged<void*, char*, uint*, int*, int>)&GetNamedPropertyMapping;
            interop[5] = (nint)(delegate* unmanaged<void*, uint*, int>)&GetPropertyCount;
            interop[6] = (nint)(delegate* unmanaged<void*, uint, nint*, int>)&GetProperty;
            interop[7] = (nint)(delegate* unmanaged<void*, uint, nint*, int>)&GetSource;
            interop[8] = (nint)(delegate* unmanaged<void*, uint*, int>)&GetSourceCount;
            // IInspectable too: a WinRT object is asked for it first.
            var entries = (ComInterfaceEntry*)RuntimeHelpers.AllocateTypeAssociatedMemory(typeof(GaussianBlur), sizeof(ComInterfaceEntry) * 4);
            entries[0] = new ComInterfaceEntry { IID = IidGraphicsEffect, Vtable = (nint)effect };
            entries[1] = new ComInterfaceEntry { IID = IidGraphicsEffectSource, Vtable = (nint)source };
            entries[2] = new ComInterfaceEntry { IID = IidD2D1Interop, Vtable = (nint)interop };
            entries[3] = new ComInterfaceEntry { IID = IidInspectable, Vtable = (nint)source };
            return entries;
        }

        protected override ComInterfaceEntry* ComputeVtables(object obj, CreateComInterfaceFlags flags, out int count)
        {
            count = 4;
            return Entries;
        }

        protected override object CreateObject(nint externalComObject, CreateObjectFlags flags) => throw new NotSupportedException();
        protected override void ReleaseObjects(System.Collections.IEnumerable objects) => throw new NotSupportedException();
    }

    [DllImport("combase.dll", ExactSpelling = true)]
    private static extern int WindowsCreateString(char* source, uint length, nint* hstring);

    // IInspectable

    [UnmanagedCallersOnly]
    private static int GetIids(void* that, uint* count, Guid** iids)
    {
        var list = (Guid*)Marshal.AllocCoTaskMem(sizeof(Guid) * 2);
        if (list is null) return OutOfMemory;
        list[0] = IidGraphicsEffect;
        list[1] = IidGraphicsEffectSource;
        *count = 2;
        *iids = list;
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetRuntimeClassName(void* that, nint* name)
    {
        // No class of its own: an empty name, so it is taken by its interfaces.
        *name = 0;
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetTrustLevel(void* that, int* level)
    {
        *level = 0; // BaseTrust
        return Ok;
    }

    // IGraphicsEffect

    [UnmanagedCallersOnly]
    private static int GetName(void* that, nint* name)
    {
        var text = Self(that).Name;
        fixed (char* chars = text) return WindowsCreateString(chars, (uint)text.Length, name);
    }

    [UnmanagedCallersOnly]
    private static int PutName(void* that, nint name) => NotImplemented;

    // IGraphicsEffectD2D1Interop

    [UnmanagedCallersOnly]
    private static int GetEffectId(void* that, Guid* id)
    {
        *id = EffectId;
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetNamedPropertyMapping(void* that, char* name, uint* index, int* mapping)
    {
        // Only the amount can be animated; the border and the optimization are fixed when made.
        if (new string(name) != "BlurAmount") return InvalidArgument;
        *index = 0; // D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION
        *mapping = MappingDirect;
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetPropertyCount(void* that, uint* count)
    {
        *count = 3;
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetProperty(void* that, uint index, nint* value)
    {
        var blur = Self(that);
        // Standard deviation, optimization (balanced), border mode: in D2D's order.
        object boxed = index switch
        {
            0 => Windows.Foundation.PropertyValue.CreateSingle(blur.Amount),
            1 => Windows.Foundation.PropertyValue.CreateUInt32(1),
            2 => Windows.Foundation.PropertyValue.CreateUInt32((uint)blur.Border),
            _ => null!,
        };
        if (boxed is null) return InvalidArgument;
        var inspectable = MarshalInspectable<object>.FromManaged(boxed);
        try
        {
            var iid = IidPropertyValue;
            return Marshal.QueryInterface(inspectable, in iid, out *value);
        }
        finally { Marshal.Release(inspectable); }
    }

    [UnmanagedCallersOnly]
    private static int GetSource(void* that, uint index, nint* source)
    {
        if (index != 0) return InvalidArgument;
        *source = MarshalInterface<IGraphicsEffectSource>.FromManaged(Self(that).Source);
        return Ok;
    }

    [UnmanagedCallersOnly]
    private static int GetSourceCount(void* that, uint* count)
    {
        *count = 1;
        return Ok;
    }
}
