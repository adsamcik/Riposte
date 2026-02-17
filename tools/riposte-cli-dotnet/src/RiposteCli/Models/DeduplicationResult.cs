namespace RiposteCli.Models;

/// <summary>
/// Result of image deduplication analysis.
/// </summary>
public sealed class DeduplicationResult
{
    /// <summary>Images that are unique (not duplicates).</summary>
    public required List<string> UniqueImages { get; init; }

    /// <summary>Pairs of (duplicate, original) for exact content matches.</summary>
    public required List<(string Duplicate, string Original)> ExactDuplicates { get; init; }

    /// <summary>Triples of (duplicate, original, distance) for perceptual matches.</summary>
    public required List<(string Duplicate, string Original, int Distance)> NearDuplicates { get; init; }
}
