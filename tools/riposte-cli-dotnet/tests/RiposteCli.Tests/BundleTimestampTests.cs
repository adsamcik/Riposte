using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

/// <summary>
/// Tests that bundle timestamps (LastFullBundleAt, LastPatchBundleAt)
/// are independently tracked through with-expressions and manifest persistence.
/// Mirrors the logic in AnnotateCommand.cs lines 502-507.
/// </summary>
public sealed class BundleTimestampTests : IDisposable
{
    private readonly string _tempDir;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    public BundleTimestampTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    // ── 1. Full bundle sets LastFullBundleAt, preserves LastPatchBundleAt ──

    [Fact]
    public void FullBundle_SetsLastFullBundleAt_PreservesExistingLastPatchBundleAt()
    {
        var existingPatchTimestamp = "2025-01-15T10:30:00.0000000+00:00";
        var manifest = new BuildManifest
        {
            LastPatchBundleAt = existingPatchTimestamp,
        };

        // Simulate AnnotateCommand bundle block (lines 502-506)
        var zipMode = ZipMode.Full;
        manifest = manifest with
        {
            LastFullBundleAt = zipMode == ZipMode.Full
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastFullBundleAt,
            LastPatchBundleAt = zipMode == ZipMode.Patch
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastPatchBundleAt,
        };

        Assert.NotNull(manifest.LastFullBundleAt);
        Assert.Equal(existingPatchTimestamp, manifest.LastPatchBundleAt);
    }

    [Fact]
    public void FullBundle_DoesNotClearPatchTimestamp_WhenPatchIsNull()
    {
        var manifest = new BuildManifest
        {
            LastPatchBundleAt = null,
        };

        var zipMode = ZipMode.Full;
        manifest = manifest with
        {
            LastFullBundleAt = zipMode == ZipMode.Full
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastFullBundleAt,
            LastPatchBundleAt = zipMode == ZipMode.Patch
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastPatchBundleAt,
        };

        Assert.NotNull(manifest.LastFullBundleAt);
        Assert.Null(manifest.LastPatchBundleAt);
    }

    // ── 2. Patch bundle sets LastPatchBundleAt, preserves LastFullBundleAt ──

    [Fact]
    public void PatchBundle_SetsLastPatchBundleAt_PreservesExistingLastFullBundleAt()
    {
        var existingFullTimestamp = "2025-01-10T08:00:00.0000000+00:00";
        var manifest = new BuildManifest
        {
            LastFullBundleAt = existingFullTimestamp,
        };

        var zipMode = ZipMode.Patch;
        manifest = manifest with
        {
            LastFullBundleAt = zipMode == ZipMode.Full
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastFullBundleAt,
            LastPatchBundleAt = zipMode == ZipMode.Patch
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastPatchBundleAt,
        };

        Assert.Equal(existingFullTimestamp, manifest.LastFullBundleAt);
        Assert.NotNull(manifest.LastPatchBundleAt);
    }

    [Fact]
    public void PatchBundle_DoesNotClearFullTimestamp_WhenFullIsNull()
    {
        var manifest = new BuildManifest
        {
            LastFullBundleAt = null,
        };

        var zipMode = ZipMode.Patch;
        manifest = manifest with
        {
            LastFullBundleAt = zipMode == ZipMode.Full
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastFullBundleAt,
            LastPatchBundleAt = zipMode == ZipMode.Patch
                ? DateTimeOffset.UtcNow.ToString("o")
                : manifest.LastPatchBundleAt,
        };

        Assert.Null(manifest.LastFullBundleAt);
        Assert.NotNull(manifest.LastPatchBundleAt);
    }

    // ── 3. Both timestamps survive manifest save/load roundtrip ──

    [Fact]
    public void BothTimestamps_SurviveManifestSaveLoadRoundtrip()
    {
        var fullTs = "2025-06-01T12:00:00.0000000+00:00";
        var patchTs = "2025-06-10T18:30:00.0000000+00:00";
        var manifest = new BuildManifest
        {
            LastFullBundleAt = fullTs,
            LastPatchBundleAt = patchTs,
        };

        ManifestService.Save(_tempDir, manifest);
        var loaded = ManifestService.Load(_tempDir);

        Assert.Equal(fullTs, loaded.LastFullBundleAt);
        Assert.Equal(patchTs, loaded.LastPatchBundleAt);
    }

    [Fact]
    public void NullTimestamps_SurviveManifestSaveLoadRoundtrip()
    {
        var manifest = new BuildManifest
        {
            LastFullBundleAt = null,
            LastPatchBundleAt = null,
        };

        ManifestService.Save(_tempDir, manifest);
        var loaded = ManifestService.Load(_tempDir);

        Assert.Null(loaded.LastFullBundleAt);
        Assert.Null(loaded.LastPatchBundleAt);
    }

    [Fact]
    public void MixedTimestamps_SurviveManifestSaveLoadRoundtrip()
    {
        var fullTs = "2025-03-20T09:15:00.0000000+00:00";
        var manifest = new BuildManifest
        {
            LastFullBundleAt = fullTs,
            LastPatchBundleAt = null,
        };

        ManifestService.Save(_tempDir, manifest);
        var loaded = ManifestService.Load(_tempDir);

        Assert.Equal(fullTs, loaded.LastFullBundleAt);
        Assert.Null(loaded.LastPatchBundleAt);
    }

    [Fact]
    public void Timestamps_SurviveMultipleConsecutiveSaves()
    {
        // Simulates AnnotateCommand's sequential saves (global, strip, bundle)
        var manifest = new BuildManifest();

        // First save — global state update (line 452)
        manifest = manifest with { Model = "gpt-5-mini", SchemaVersion = "1.4" };
        ManifestService.Save(_tempDir, manifest);

        // Second save — bundle block (line 507)
        manifest = manifest with
        {
            LastFullBundleAt = DateTimeOffset.UtcNow.ToString("o"),
        };
        ManifestService.Save(_tempDir, manifest);

        var loaded = ManifestService.Load(_tempDir);
        Assert.NotNull(loaded.LastFullBundleAt);
        Assert.Equal("gpt-5-mini", loaded.Model);
        Assert.Equal("1.4", loaded.SchemaVersion);
    }

    // ── 4. Timestamps are valid ISO 8601 format ──

    [Fact]
    public void FullBundleTimestamp_IsValidIso8601()
    {
        var manifest = new BuildManifest();
        manifest = manifest with
        {
            LastFullBundleAt = DateTimeOffset.UtcNow.ToString("o"),
        };

        var parsed = DateTimeOffset.TryParse(manifest.LastFullBundleAt, out var dto);
        Assert.True(parsed, $"Failed to parse '{manifest.LastFullBundleAt}' as ISO 8601");
        Assert.Equal(TimeSpan.Zero, dto.Offset);
    }

    [Fact]
    public void PatchBundleTimestamp_IsValidIso8601()
    {
        var manifest = new BuildManifest();
        manifest = manifest with
        {
            LastPatchBundleAt = DateTimeOffset.UtcNow.ToString("o"),
        };

        var parsed = DateTimeOffset.TryParse(manifest.LastPatchBundleAt, out var dto);
        Assert.True(parsed, $"Failed to parse '{manifest.LastPatchBundleAt}' as ISO 8601");
        Assert.Equal(TimeSpan.Zero, dto.Offset);
    }

    [Fact]
    public void Timestamp_RoundtripFormat_ContainsExpectedComponents()
    {
        var now = DateTimeOffset.UtcNow;
        var formatted = now.ToString("o");

        // ISO 8601 "o" format: yyyy-MM-ddTHH:mm:ss.fffffffK
        Assert.Contains("T", formatted);
        Assert.Contains("+00:00", formatted);
    }

    // ── 5. Null timestamps are omitted from JSON ──

    [Fact]
    public void NullLastFullBundleAt_OmittedFromJson()
    {
        var manifest = new BuildManifest { LastFullBundleAt = null };
        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.DoesNotContain("\"lastFullBundleAt\"", json);
    }

    [Fact]
    public void NullLastPatchBundleAt_OmittedFromJson()
    {
        var manifest = new BuildManifest { LastPatchBundleAt = null };
        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.DoesNotContain("\"lastPatchBundleAt\"", json);
    }

    [Fact]
    public void NonNullLastFullBundleAt_IncludedInJson()
    {
        var manifest = new BuildManifest
        {
            LastFullBundleAt = "2025-06-01T00:00:00Z",
        };
        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.Contains("\"lastFullBundleAt\"", json);
        Assert.Contains("2025-06-01T00:00:00Z", json);
    }

    [Fact]
    public void NonNullLastPatchBundleAt_IncludedInJson()
    {
        var manifest = new BuildManifest
        {
            LastPatchBundleAt = "2025-06-10T12:00:00Z",
        };
        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.Contains("\"lastPatchBundleAt\"", json);
        Assert.Contains("2025-06-10T12:00:00Z", json);
    }

    [Fact]
    public void BothTimestampsNull_BothOmittedFromJson()
    {
        var manifest = new BuildManifest();
        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.DoesNotContain("\"lastFullBundleAt\"", json);
        Assert.DoesNotContain("\"lastPatchBundleAt\"", json);
    }

    // ── Bonus: with-expression preserves Images dictionary reference ──

    [Fact]
    public void WithExpression_PreservesImagesDictionaryReference()
    {
        var manifest = new BuildManifest();
        manifest.Images["test.png"] = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "m",
            GeneratedAt = "t",
        };

        // Simulate the with-expression used in AnnotateCommand (line 502)
        var updated = manifest with
        {
            LastFullBundleAt = DateTimeOffset.UtcNow.ToString("o"),
        };

        // The Images dictionary should be the same reference (shallow copy)
        Assert.Same(manifest.Images, updated.Images);
        Assert.Single(updated.Images);
        Assert.Equal("abc", updated.Images["test.png"].ContentHash);
    }

    [Fact]
    public void WithExpression_MutationsToImages_VisibleInBothReferences()
    {
        var manifest = new BuildManifest();
        var updated = manifest with
        {
            LastFullBundleAt = DateTimeOffset.UtcNow.ToString("o"),
        };

        // Mutate Images via the original reference
        manifest.Images["new.png"] = new ImageManifestEntry
        {
            ContentHash = "xyz",
            Model = "m",
            GeneratedAt = "t",
        };

        // Visible through the updated reference too (shared dictionary)
        Assert.Single(updated.Images);
        Assert.Equal("xyz", updated.Images["new.png"].ContentHash);
    }
}
