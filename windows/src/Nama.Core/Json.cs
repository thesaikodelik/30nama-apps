using System.Collections;
using System.Globalization;
using System.Text;
using System.Text.Json;

namespace Nama.Core;

/// <summary>
/// Loosely typed JSON value.
///
/// The 30nama API is not consistent between endpoints: numbers arrive as strings, a field is an
/// object in one response and an array in another, and optional fields are <c>null</c>, <c>""</c>
/// or missing. Models read through this type instead of strict deserialisation so one odd field
/// never fails a whole screen.
/// </summary>
public sealed class Json : IEquatable<Json>
{
    public enum Kind { Null, Bool, Number, String, Array, Object }

    public static readonly Json Null = new(Kind.Null);

    public Kind Type { get; }
    private readonly bool _bool;
    private readonly double _number;
    private readonly string? _string;
    private readonly IReadOnlyList<Json>? _array;
    private readonly IReadOnlyDictionary<string, Json>? _object;

    private Json(Kind type) => Type = type;
    public Json(bool b) : this(Kind.Bool) => _bool = b;
    public Json(double n) : this(Kind.Number) => _number = n;
    public Json(string s) : this(Kind.String) => _string = s;
    public Json(IReadOnlyList<Json> a) : this(Kind.Array) => _array = a;
    public Json(IReadOnlyDictionary<string, Json> o) : this(Kind.Object) => _object = o;

    /// <summary>From plain values: dictionaries, lists, strings, numbers, booleans and null.</summary>
    public static Json From(object? value) => value switch
    {
        null => Null,
        Json j => j,
        bool b => new Json(b),
        string s => new Json(s),
        IDictionary d => new Json(d.Keys.Cast<object>().ToDictionary(k => k.ToString()!, k => From(d[k]))),
        IEnumerable e => new Json(e.Cast<object?>().Select(From).ToList()),
        IConvertible c when c.GetTypeCode() is >= TypeCode.SByte and <= TypeCode.Decimal => new Json(Convert.ToDouble(c, CultureInfo.InvariantCulture)),
        _ => Null,
    };

    public static Json Parse(byte[] data) => Parse(Encoding.UTF8.GetString(data));

    public static Json Parse(string text)
    {
        using var doc = JsonDocument.Parse(text, new JsonDocumentOptions { AllowTrailingCommas = true });
        return From(doc.RootElement);
    }

    private static Json From(JsonElement e) => e.ValueKind switch
    {
        JsonValueKind.True => new Json(true),
        JsonValueKind.False => new Json(false),
        JsonValueKind.Number => new Json(e.GetDouble()),
        JsonValueKind.String => new Json(e.GetString()!),
        JsonValueKind.Array => new Json(e.EnumerateArray().Select(From).ToList()),
        JsonValueKind.Object => new Json(ObjectOf(e)),
        _ => Null,
    };

    private static Dictionary<string, Json> ObjectOf(JsonElement e)
    {
        var d = new Dictionary<string, Json>();
        // A repeated key keeps its last value, as the Foundation parser does.
        foreach (var p in e.EnumerateObject()) d[p.Name] = From(p.Value);
        return d;
    }

    /// <summary>Serialised again, for a value we want to persist.</summary>
    public string Serialize()
    {
        using var stream = new MemoryStream();
        using (var w = new Utf8JsonWriter(stream, new JsonWriterOptions { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping }))
            Write(w);
        return Encoding.UTF8.GetString(stream.ToArray());
    }

    private void Write(Utf8JsonWriter w)
    {
        switch (Type)
        {
            case Kind.Null: w.WriteNullValue(); break;
            case Kind.Bool: w.WriteBooleanValue(_bool); break;
            case Kind.Number: w.WriteNumberValue(_number); break;
            case Kind.String: w.WriteStringValue(_string); break;
            case Kind.Array:
                w.WriteStartArray();
                foreach (var x in _array!) x.Write(w);
                w.WriteEndArray();
                break;
            case Kind.Object:
                w.WriteStartObject();
                foreach (var (k, v) in _object!) { w.WritePropertyName(k); v.Write(w); }
                w.WriteEndObject();
                break;
        }
    }

    public Json this[string key] => _object is not null && _object.TryGetValue(key, out var v) ? v : Null;

    public Json this[int index] => _array is not null && index >= 0 && index < _array.Count ? _array[index] : Null;

    public bool IsNull => Type == Kind.Null;

    public IReadOnlyList<Json> Array => _array ?? [];

    public IReadOnlyDictionary<string, Json>? Object => _object;

    /// <summary>String value; numbers are rendered without a trailing <c>.0</c>.</summary>
    public string? String => Type switch
    {
        Kind.String => _string,
        Kind.Number => _number == Math.Round(_number) && Math.Abs(_number) < 1e15
            ? ((long)_number).ToString(CultureInfo.InvariantCulture)
            : _number.ToString("R", CultureInfo.InvariantCulture),
        _ => null,
    };

    /// <summary>Trimmed, non-empty string.</summary>
    public string? Text
    {
        get
        {
            var s = String?.Trim();
            return string.IsNullOrEmpty(s) ? null : s;
        }
    }

    public double? Double => Type switch
    {
        Kind.Number => _number,
        Kind.String => double.TryParse(_string!.Trim(' ', '\t'), NumberStyles.Float, CultureInfo.InvariantCulture, out var d) ? d : null,
        _ => null,
    };

    public int? Int
    {
        get
        {
            if (Double is not { } d || !double.IsFinite(d) || Math.Abs(d) >= int.MaxValue) return null;
            return (int)d;
        }
    }

    public long? Long
    {
        get
        {
            if (Double is not { } d || !double.IsFinite(d) || Math.Abs(d) >= 1e15) return null;
            return (long)d;
        }
    }

    public bool Bool => Type switch
    {
        Kind.Bool => _bool,
        Kind.Number => _number != 0,
        Kind.String => _string!.ToLowerInvariant() is "1" or "true" or "yes",
        _ => false,
    };

    public Uri? Url => Text is { } s && Uri.TryCreate(s, UriKind.Absolute, out var u) ? u : null;

    public bool Equals(Json? other)
    {
        if (other is null || other.Type != Type) return false;
        return Type switch
        {
            Kind.Null => true,
            Kind.Bool => _bool == other._bool,
            Kind.Number => _number.Equals(other._number),
            Kind.String => _string == other._string,
            Kind.Array => _array!.SequenceEqual(other._array!),
            Kind.Object => _object!.Count == other._object!.Count
                && _object.All(kv => other._object.TryGetValue(kv.Key, out var v) && kv.Value.Equals(v)),
            _ => false,
        };
    }

    public override bool Equals(object? obj) => Equals(obj as Json);

    public override int GetHashCode() => Type switch
    {
        Kind.Bool => _bool.GetHashCode(),
        Kind.Number => _number.GetHashCode(),
        Kind.String => _string!.GetHashCode(),
        Kind.Array => _array!.Count,
        Kind.Object => _object!.Count,
        _ => 0,
    };

    public override string ToString() => Serialize();
}

public static class UriExtensions
{
    /// <summary>The last path segment with its percent escapes undone, like Foundation's <c>lastPathComponent</c>.</summary>
    public static string LastPathComponent(this Uri url)
    {
        var path = url.AbsolutePath.TrimEnd('/');
        var name = path[(path.LastIndexOf('/') + 1)..];
        return Uri.UnescapeDataString(name);
    }
}
