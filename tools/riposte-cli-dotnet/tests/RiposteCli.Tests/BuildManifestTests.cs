using RiposteCli.Models;
using System.Text.Json;

namespace RiposteCli.Tests;

public class BuildManifestTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = false,
    };

    // ── BuildManifest defaults ──────────────────────────────────────

    [Fact]
    public void BuildManifest_Default_ManifestVersion_Is_1_0()
    {
        var manifest = new BuildManifest();
        Assert.Equal("1.0", manifest.ManifestVersion);
    }

    [Fact]
    public void BuildManifest_Default_SchemaVersion_Is_1_4()
    {
        var manifest = new BuildManifest();
        Assert.Equal("1.4", manifest.SchemaVersion);
    }

    [Fact]
    public void BuildManifest_Default_Model_Is_Gpt5Mini()
    {
        var manifest = new BuildManifest();
        Assert.Equal("gpt-5-mini", manifest.Model);
    }

    [Fact]
    public void BuildManifest_Default_PromptHashes_Is_Empty()
    {
        var manifest = new BuildManifest();
        Assert.NotNull(manifest.PromptHashes);
        Assert.Empty(manifest.PromptHashes);
    }

    [Fact]
    public void BuildManifest_Default_Images_Is_Empty()
    {
        var manifest = new BuildManifest();
        Assert.NotNull(manifest.Images);
        Assert.Empty(manifest.Images);
    }

    [Fact]
    public void BuildManifest_Default_Optimization_Is_Null()
    {
        var manifest = new BuildManifest();
        Assert.Null(manifest.Optimization);
    }

    // ── BuildManifest JSON roundtrip ────────────────────────────────

    [Fact]
    public void BuildManifest_Json_Roundtrip_Preserves_All_Fields()
    {
        var original = new BuildManifest
        {
            ManifestVersion = "2.0",
            SchemaVersion = "1.5",
            Model = "gpt-6",
            PromptHashes = new Dictionary<string, string>
            {
                ["core"] = "abc123",
                ["search"] = "def456",
            },
            Optimization = new OptimizationConfig
            {
                ApiMaxDimension = 800,
                BundleMaxDimension = 600,
                Quality = 90,
                ApiFormat = "jpeg",
                BundleFormat = "png",
            },
            Images = new Dictionary<string, ImageManifestEntry>
            {
                ["test.png"] = new ImageManifestEntry
                {
                    ContentHash = "hash1",
                    Model = "gpt-5-mini",
                    GeneratedAt = "2025-01-01T00:00:00Z",
                    FieldHashes = new Dictionary<string, string> { ["core"] = "abc123" },
                    OptimizationFingerprint = "fp1",
                    HasApiOptimized = true,
                    HasBundleOptimized = true,
                },
            },
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<BuildManifest>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(original.ManifestVersion, deserialized.ManifestVersion);
        Assert.Equal(original.SchemaVersion, deserialized.SchemaVersion);
        Assert.Equal(original.Model, deserialized.Model);
        Assert.Equal(original.PromptHashes, deserialized.PromptHashes);
        Assert.NotNull(deserialized.Optimization);
        Assert.Equal(original.Optimization.ApiMaxDimension, deserialized.Optimization.ApiMaxDimension);
        Assert.Equal(original.Optimization.BundleFormat, deserialized.Optimization.BundleFormat);
        Assert.Single(deserialized.Images);
        Assert.True(deserialized.Images.ContainsKey("test.png"));
        Assert.Equal("hash1", deserialized.Images["test.png"].ContentHash);
    }

    // ── BuildManifest record with-expression ────────────────────────

    [Fact]
    public void BuildManifest_With_Expression_Creates_New_Instance()
    {
        var original = new BuildManifest { Model = "gpt-5-mini" };
        var modified = original with { Model = "gpt-6" };

        Assert.NotSame(original, modified);
        Assert.Equal("gpt-5-mini", original.Model);
        Assert.Equal("gpt-6", modified.Model);
        Assert.Equal(original.ManifestVersion, modified.ManifestVersion);
    }

    // ── BuildManifest dictionary mutability ──────────────────────────

    [Fact]
    public void BuildManifest_PromptHashes_Is_Mutable_After_Construction()
    {
        var manifest = new BuildManifest();

        manifest.PromptHashes["core"] = "hash1";
        manifest.PromptHashes["search"] = "hash2";

        Assert.Equal(2, manifest.PromptHashes.Count);
        Assert.Equal("hash1", manifest.PromptHashes["core"]);
    }

    [Fact]
    public void BuildManifest_Images_Is_Mutable_After_Construction()
    {
        var manifest = new BuildManifest();

        manifest.Images["meme.png"] = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "gpt-5-mini",
            GeneratedAt = "2025-01-01T00:00:00Z",
        };

        Assert.Single(manifest.Images);
        Assert.Equal("abc", manifest.Images["meme.png"].ContentHash);
    }

    // ── BuildManifest JSON property names ───────────────────────────

    [Fact]
    public void BuildManifest_Json_Uses_CamelCase_Property_Names()
    {
        var manifest = new BuildManifest
        {
            PromptHashes = new Dictionary<string, string> { ["core"] = "h1" },
        };

        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.Contains("\"manifestVersion\"", json);
        Assert.Contains("\"schemaVersion\"", json);
        Assert.Contains("\"model\"", json);
        Assert.Contains("\"promptHashes\"", json);
        Assert.Contains("\"images\"", json);
        Assert.DoesNotContain("\"ManifestVersion\"", json);
        Assert.DoesNotContain("\"SchemaVersion\"", json);
    }

    [Fact]
    public void BuildManifest_Json_Omits_Null_Optimization()
    {
        var manifest = new BuildManifest();

        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.DoesNotContain("\"optimization\"", json);
    }

    [Fact]
    public void BuildManifest_Json_Includes_Non_Null_Optimization()
    {
        var manifest = new BuildManifest { Optimization = new OptimizationConfig() };

        var json = JsonSerializer.Serialize(manifest, JsonOptions);

        Assert.Contains("\"optimization\"", json);
    }

    // ── OptimizationConfig defaults ─────────────────────────────────

    [Fact]
    public void OptimizationConfig_Default_ApiMaxDimension_Is_1200()
    {
        var config = new OptimizationConfig();
        Assert.Equal(1200, config.ApiMaxDimension);
    }

    [Fact]
    public void OptimizationConfig_Default_BundleMaxDimension_Is_1200()
    {
        var config = new OptimizationConfig();
        Assert.Equal(1200, config.BundleMaxDimension);
    }

    [Fact]
    public void OptimizationConfig_Default_Quality_Is_85()
    {
        var config = new OptimizationConfig();
        Assert.Equal(85, config.Quality);
    }

    [Fact]
    public void OptimizationConfig_Default_ApiFormat_Is_Original()
    {
        var config = new OptimizationConfig();
        Assert.Equal("original", config.ApiFormat);
    }

    [Fact]
    public void OptimizationConfig_Default_BundleFormat_Is_Webp()
    {
        var config = new OptimizationConfig();
        Assert.Equal("webp", config.BundleFormat);
    }

    // ── OptimizationConfig fingerprint ──────────────────────────────

    [Fact]
    public void OptimizationConfig_Fingerprint_Is_Deterministic()
    {
        var config = new OptimizationConfig();

        var fp1 = config.Fingerprint();
        var fp2 = config.Fingerprint();

        Assert.Equal(fp1, fp2);
    }

    [Fact]
    public void OptimizationConfig_Same_Values_Produce_Same_Fingerprint()
    {
        var config1 = new OptimizationConfig();
        var config2 = new OptimizationConfig();

        Assert.Equal(config1.Fingerprint(), config2.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_Changes_When_ApiMaxDimension_Changes()
    {
        var baseline = new OptimizationConfig();
        var changed = baseline with { ApiMaxDimension = 800 };

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_Changes_When_ApiFormat_Changes()
    {
        var baseline = new OptimizationConfig();
        var changed = baseline with { ApiFormat = "jpeg" };

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_Changes_When_BundleMaxDimension_Changes()
    {
        var baseline = new OptimizationConfig();
        var changed = baseline with { BundleMaxDimension = 600 };

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_Changes_When_BundleFormat_Changes()
    {
        var baseline = new OptimizationConfig();
        var changed = baseline with { BundleFormat = "png" };

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void OptimizationConfig_Fingerprint_Changes_When_Quality_Changes()
    {
        var baseline = new OptimizationConfig();
        var changed = baseline with { Quality = 50 };

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    // ── OptimizationConfig JSON roundtrip ───────────────────────────

    [Fact]
    public void OptimizationConfig_Json_Roundtrip_Preserves_All_Fields()
    {
        var original = new OptimizationConfig
        {
            ApiMaxDimension = 900,
            ApiFormat = "jpeg",
            BundleMaxDimension = 500,
            BundleFormat = "png",
            Quality = 70,
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<OptimizationConfig>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(original.ApiMaxDimension, deserialized.ApiMaxDimension);
        Assert.Equal(original.ApiFormat, deserialized.ApiFormat);
        Assert.Equal(original.BundleMaxDimension, deserialized.BundleMaxDimension);
        Assert.Equal(original.BundleFormat, deserialized.BundleFormat);
        Assert.Equal(original.Quality, deserialized.Quality);
    }

    // ── ImageManifestEntry defaults ─────────────────────────────────

    [Fact]
    public void ImageManifestEntry_Default_SchemaVersion_Is_1_4()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        Assert.Equal("1.4", entry.SchemaVersion);
    }

    [Fact]
    public void ImageManifestEntry_Default_FieldHashes_Is_Empty()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        Assert.NotNull(entry.FieldHashes);
        Assert.Empty(entry.FieldHashes);
    }

    [Fact]
    public void ImageManifestEntry_Default_HasApiOptimized_Is_False()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        Assert.False(entry.HasApiOptimized);
    }

    [Fact]
    public void ImageManifestEntry_Default_HasBundleOptimized_Is_False()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        Assert.False(entry.HasBundleOptimized);
    }

    [Fact]
    public void ImageManifestEntry_Default_OptimizationFingerprint_Is_Null()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        Assert.Null(entry.OptimizationFingerprint);
    }

    // ── ImageManifestEntry null fingerprint omitted from JSON ────────

    [Fact]
    public void ImageManifestEntry_Null_OptimizationFingerprint_Omitted_From_Json()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
        };

        var json = JsonSerializer.Serialize(entry, JsonOptions);

        Assert.DoesNotContain("\"optimizationFingerprint\"", json);
    }

    [Fact]
    public void ImageManifestEntry_Non_Null_OptimizationFingerprint_Included_In_Json()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h",
            Model = "m",
            GeneratedAt = "t",
            OptimizationFingerprint = "fp-abc",
        };

        var json = JsonSerializer.Serialize(entry, JsonOptions);

        Assert.Contains("\"optimizationFingerprint\"", json);
        Assert.Contains("fp-abc", json);
    }

    // ── ImageManifestEntry mutability ────────────────────────────────

    [Fact]
    public void ImageManifestEntry_All_Properties_Are_Mutable()
    {
        var entry = new ImageManifestEntry
        {
            ContentHash = "h1",
            Model = "m1",
            GeneratedAt = "t1",
        };

        entry.ContentHash = "h2";
        entry.SchemaVersion = "2.0";
        entry.Model = "m2";
        entry.GeneratedAt = "t2";
        entry.OptimizationFingerprint = "fp";
        entry.HasApiOptimized = true;
        entry.HasBundleOptimized = true;
        entry.FieldHashes["core"] = "x";

        Assert.Equal("h2", entry.ContentHash);
        Assert.Equal("2.0", entry.SchemaVersion);
        Assert.Equal("m2", entry.Model);
        Assert.Equal("t2", entry.GeneratedAt);
        Assert.Equal("fp", entry.OptimizationFingerprint);
        Assert.True(entry.HasApiOptimized);
        Assert.True(entry.HasBundleOptimized);
        Assert.Single(entry.FieldHashes);
    }

    // ── ImageManifestEntry JSON roundtrip ────────────────────────────

    [Fact]
    public void ImageManifestEntry_Json_Roundtrip_Preserves_All_Fields()
    {
        var original = new ImageManifestEntry
        {
            ContentHash = "abc123",
            SchemaVersion = "1.5",
            Model = "gpt-6",
            GeneratedAt = "2025-06-15T12:00:00Z",
            FieldHashes = new Dictionary<string, string>
            {
                ["core"] = "p1",
                ["search"] = "p2",
                ["localization:cs"] = "p3",
            },
            OptimizationFingerprint = "api:800:jpeg|bundle:600:webp|q:90",
            HasApiOptimized = true,
            HasBundleOptimized = true,
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<ImageManifestEntry>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(original.ContentHash, deserialized.ContentHash);
        Assert.Equal(original.SchemaVersion, deserialized.SchemaVersion);
        Assert.Equal(original.Model, deserialized.Model);
        Assert.Equal(original.GeneratedAt, deserialized.GeneratedAt);
        Assert.Equal(original.FieldHashes, deserialized.FieldHashes);
        Assert.Equal(original.OptimizationFingerprint, deserialized.OptimizationFingerprint);
        Assert.Equal(original.HasApiOptimized, deserialized.HasApiOptimized);
        Assert.Equal(original.HasBundleOptimized, deserialized.HasBundleOptimized);
    }

    [Fact]
    public void ImageManifestEntry_Json_Roundtrip_With_Null_OptimizationFingerprint()
    {
        var original = new ImageManifestEntry
        {
            ContentHash = "abc",
            Model = "m",
            GeneratedAt = "t",
            OptimizationFingerprint = null,
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<ImageManifestEntry>(json);

        Assert.NotNull(deserialized);
        Assert.Null(deserialized.OptimizationFingerprint);
    }
}
