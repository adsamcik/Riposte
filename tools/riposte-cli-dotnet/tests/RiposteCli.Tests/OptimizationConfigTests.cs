using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

public class OptimizationConfigTests
{
    private static OptimizationConfig DefaultConfig() => new();

    private static OptimizationConfig CustomConfig(
        int apiMaxDim = 1200,
        string apiFormat = "original",
        int bundleMaxDim = 1200,
        string bundleFormat = "webp",
        int quality = 85) => new()
    {
        ApiMaxDimension = apiMaxDim,
        ApiFormat = apiFormat,
        BundleMaxDimension = bundleMaxDim,
        BundleFormat = bundleFormat,
        Quality = quality,
    };

    // --- Fingerprint determinism ---

    [Fact]
    public void Fingerprint_SameConfig_ReturnsSameValue()
    {
        var a = DefaultConfig();
        var b = DefaultConfig();

        Assert.Equal(a.Fingerprint(), b.Fingerprint());
    }

    // --- Single-field changes produce different fingerprints ---

    [Fact]
    public void Fingerprint_DifferentApiMaxDimension_ReturnsDifferentValue()
    {
        var baseline = DefaultConfig();
        var changed = CustomConfig(apiMaxDim: 800);

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void Fingerprint_DifferentBundleMaxDimension_ReturnsDifferentValue()
    {
        var baseline = DefaultConfig();
        var changed = CustomConfig(bundleMaxDim: 600);

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void Fingerprint_DifferentQuality_ReturnsDifferentValue()
    {
        var baseline = DefaultConfig();
        var changed = CustomConfig(quality: 50);

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void Fingerprint_DifferentApiFormat_ReturnsDifferentValue()
    {
        var baseline = DefaultConfig();
        var changed = CustomConfig(apiFormat: "webp");

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    [Fact]
    public void Fingerprint_DifferentBundleFormat_ReturnsDifferentValue()
    {
        var baseline = DefaultConfig();
        var changed = CustomConfig(bundleFormat: "jpeg");

        Assert.NotEqual(baseline.Fingerprint(), changed.Fingerprint());
    }

    // --- Fingerprint encodes all dimensions ---

    [Theory]
    [InlineData("1200")]       // ApiMaxDimension default
    [InlineData("original")]   // ApiFormat default
    [InlineData("webp")]       // BundleFormat default
    [InlineData("85")]         // Quality default
    public void Fingerprint_ContainsAllConfigValues(string expected)
    {
        var config = DefaultConfig();

        Assert.Contains(expected, config.Fingerprint());
    }

    [Fact]
    public void Fingerprint_ContainsBundleMaxDimension()
    {
        var config = CustomConfig(bundleMaxDim: 999);

        Assert.Contains("999", config.Fingerprint());
    }

    // --- Record equality ---

    [Fact]
    public void RecordEquality_SameValues_AreEqual()
    {
        var a = DefaultConfig();
        var b = DefaultConfig();

        Assert.Equal(a, b);
        Assert.True(a == b);
    }

    [Fact]
    public void RecordEquality_DifferentValues_AreNotEqual()
    {
        var a = DefaultConfig();
        var b = CustomConfig(quality: 50);

        Assert.NotEqual(a, b);
    }

    // --- Record with expression ---

    [Fact]
    public void WithExpression_ChangesOnlyTargetedField()
    {
        var original = DefaultConfig();
        var modified = original with { Quality = 50 };

        Assert.Equal(50, modified.Quality);
        Assert.Equal(original.ApiMaxDimension, modified.ApiMaxDimension);
        Assert.Equal(original.ApiFormat, modified.ApiFormat);
        Assert.Equal(original.BundleMaxDimension, modified.BundleMaxDimension);
        Assert.Equal(original.BundleFormat, modified.BundleFormat);
    }

    // --- Default config fingerprint stability ---

    [Fact]
    public void DefaultConfig_Fingerprint_IsStable()
    {
        var config = DefaultConfig();
        var expected = "api:1200:original|bundle:1200:webp|q:85";

        Assert.Equal(expected, config.Fingerprint());
    }

    // --- JSON roundtrip ---

    [Fact]
    public void JsonRoundtrip_PreservesAllFieldsAndFingerprint()
    {
        var original = CustomConfig(
            apiMaxDim: 800,
            apiFormat: "jpeg",
            bundleMaxDim: 600,
            bundleFormat: "png",
            quality: 70);

        var json = JsonSerializer.Serialize(original);
        var deserialized = JsonSerializer.Deserialize<OptimizationConfig>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(original.ApiMaxDimension, deserialized.ApiMaxDimension);
        Assert.Equal(original.ApiFormat, deserialized.ApiFormat);
        Assert.Equal(original.BundleMaxDimension, deserialized.BundleMaxDimension);
        Assert.Equal(original.BundleFormat, deserialized.BundleFormat);
        Assert.Equal(original.Quality, deserialized.Quality);
        Assert.Equal(original.Fingerprint(), deserialized.Fingerprint());
    }

    // --- Multiple single-field changes produce pairwise-unique fingerprints ---

    [Fact]
    public void MultipleChanges_ProducePairwiseUniqueFingerprints()
    {
        var configs = new[]
        {
            DefaultConfig(),
            CustomConfig(apiMaxDim: 800),
            CustomConfig(bundleMaxDim: 600),
            CustomConfig(quality: 50),
            CustomConfig(apiFormat: "webp"),
            CustomConfig(bundleFormat: "jpeg"),
        };

        var fingerprints = configs.Select(c => c.Fingerprint()).ToList();

        // Every fingerprint must be distinct from every other
        Assert.Equal(fingerprints.Count, fingerprints.Distinct().Count());
    }
}
