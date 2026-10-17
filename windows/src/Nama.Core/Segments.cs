namespace Nama.Core;

/// <summary>A stretch of a file: bytes <c>Start</c> to <c>End</c>, of which those before <c>Pos</c> have arrived.</summary>
public sealed record SegmentState(long Start, long Pos, long End)
{
    public long Remaining => Math.Max(0, End - Pos);
    public long Downloaded => Pos - Start;
}

/// <summary>
/// One file fetched over several connections at once. It starts as one segment covering the
/// whole file; whenever a connection is free it halves the segment with the most bytes left and
/// takes the second half, and the connection that had it stops at the new boundary. No
/// connection idles while another still has a long tail, and a slow one loses work to fast ones.
/// Ported from the Tachyon download manager's segment.rs (MIT). Thread-safe.
/// </summary>
public sealed class SegmentMap
{
    /// <summary>Split points are aligned to this many bytes when possible.</summary>
    private const long Align = 64 * 1024;

    public sealed class Segment
    {
        private readonly object _lock = new();
        private long _pos, _end;
        internal int Owned;

        public long Start { get; }

        internal Segment(SegmentState state)
        {
            Start = state.Start;
            _pos = state.Pos;
            _end = state.End;
        }

        public SegmentState State { get { lock (_lock) return new SegmentState(Start, _pos, _end); } }
        public long Pos { get { lock (_lock) return _pos; } }
        public long End { get { lock (_lock) return _end; } }
        public bool IsComplete { get { lock (_lock) return _pos >= _end; } }

        /// <summary>
        /// Writes as much of <paramref name="data"/> as fits before the segment's end, at its
        /// position, and returns how much that was. Held under the lock, so a split can never move
        /// the end below bytes being written.
        /// </summary>
        public int Write(Stream file, ReadOnlySpan<byte> data)
        {
            lock (_lock)
            {
                var n = (int)Math.Min(data.Length, Math.Max(0, _end - _pos));
                if (n > 0)
                {
                    file.Position = _pos;
                    file.Write(data[..n]);
                    _pos += n;
                }
                return n;
            }
        }

        /// <summary>Lets go of it, for another connection to take up.</summary>
        public void Release() => Interlocked.Exchange(ref Owned, 0);

        internal bool TryTake() => Interlocked.CompareExchange(ref Owned, 1, 0) == 0;

        internal Segment? SplitOff(long minimum)
        {
            lock (_lock)
            {
                var remaining = _end - _pos;
                if (remaining < 2 * minimum) return null;
                var middle = _pos + remaining / 2;
                var aligned = middle - middle % Align;
                if (aligned >= _pos + minimum) middle = aligned;
                var second = new Segment(new SegmentState(middle, middle, _end)) { Owned = 1 };
                _end = middle;
                return second;
            }
        }
    }

    private readonly object _lock = new();
    private readonly List<Segment> _segments;
    private readonly long _minimum;

    /// <summary>Whether the file can be fetched in parts: the server answers ranges and its size is known.</summary>
    public bool Splittable { get; }

    public SegmentMap(IEnumerable<SegmentState> states, bool splittable, long minimum = 1024 * 1024)
    {
        _segments = states.Select(s => new Segment(s)).OrderBy(s => s.Start).ToList();
        Splittable = splittable;
        _minimum = Math.Max(1, minimum);
    }

    /// <summary>One segment for a whole file of <paramref name="size"/> bytes, of which the first <paramref name="have"/> are here.</summary>
    public static SegmentMap Whole(long size, long have = 0) => new([new SegmentState(0, Math.Min(have, size), size)], splittable: true);

    public List<SegmentState> Snapshot() { lock (_lock) return _segments.Select(s => s.State).ToList(); }

    public long Downloaded { get { lock (_lock) return _segments.Sum(s => s.State.Downloaded); } }

    public bool IsComplete { get { lock (_lock) return _segments.All(s => s.IsComplete); } }

    /// <summary>The first unfinished segment no connection has: left from a last run, or one a connection gave up.</summary>
    public Segment? TakeUnowned()
    {
        lock (_lock) return _segments.FirstOrDefault(s => !s.IsComplete && s.TryTake());
    }

    /// <summary>Halves the segment with the most left and returns the second half, already taken.</summary>
    public Segment? SplitLargest()
    {
        if (!Splittable) return null;
        lock (_lock)
        {
            // On a tie the earliest, so the file fills from the front.
            var victim = _segments.Where(s => s.State.Remaining >= 2 * _minimum)
                .Select((s, i) => (s, i)).OrderByDescending(x => x.s.State.Remaining).ThenBy(x => x.i).Select(x => x.s).FirstOrDefault();
            if (victim?.SplitOff(_minimum) is not { } second) return null;
            var at = _segments.FindIndex(s => s.Start > second.Start);
            _segments.Insert(at < 0 ? _segments.Count : at, second);
            return second;
        }
    }

    /// <summary>Work for a free connection: what is left over first, then a fresh split.</summary>
    public Segment? NextWork() => TakeUnowned() ?? SplitLargest();
}
