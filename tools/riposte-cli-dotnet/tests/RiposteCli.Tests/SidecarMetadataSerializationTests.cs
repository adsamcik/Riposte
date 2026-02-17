using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

public class SidecarMetadataSerializationTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    #region Serialization

    [Fact]
    public void Serialize_MinimalMetadata_HasRequiredFields()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        Assert.Equal("1.3", root.GetProperty("schemaVersion").GetString());
        Assert.Single(root.GetProperty("emojis").EnumerateArray());
        Assert.True(root.TryGetProperty("createdAt", out _));
        Assert.True(root.TryGetProperty("appVersion", out _));
        Assert.True(root.TryGetProperty("cliVersion", out _));
    }

    [Fact]
    public void Serialize_NullOptionalFields_Omitted()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(metadata, JsonOptions);

        Assert.DoesNotContain("\"title\"", json);
        Assert.DoesNotContain("\"description\"", json);
        Assert.DoesNotContain("\"tags\"", json);
        Assert.DoesNotContain("\"searchPhrases\"", json);
        Assert.DoesNotContain("\"primaryLanguage\"", json);
        Assert.DoesNotContain("\"localizations\"", json);
        Assert.DoesNotContain("\"contentHash\"", json);
        Assert.DoesNotContain("\"basedOn\"", json);
    }

    [Fact]
    public void Serialize_AllFields_AllPresent()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂", "🔥"],
            Title = "Test",
            Description = "Test description",
            Tags = ["funny", "meme"],
            SearchPhrases = ["funny meme"],
            PrimaryLanguage = "en",
            ContentHash = "abc123",
            BasedOn = "Drake",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Český",
                    Description = "Český popis",
                    Tags = ["vtipné"],
                    SearchPhrases = ["vtipný meme"],
                },
            },
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);

        Assert.Contains("\"title\"", json);
        Assert.Contains("\"description\"", json);
        Assert.Contains("\"tags\"", json);
        Assert.Contains("\"searchPhrases\"", json);
        Assert.Contains("\"primaryLanguage\"", json);
        Assert.Contains("\"contentHash\"", json);
        Assert.Contains("\"basedOn\"", json);
        Assert.Contains("\"localizations\"", json);
    }

    [Fact]
    public void Serialize_UsesJsonPropertyNames_NotCSharpNames()
    {
        var metadata = new SidecarMetadata
        {
            Emojis = ["😂"],
            PrimaryLanguage = "en",
            ContentHash = "hash",
        };

        var json = JsonSerializer.Serialize(metadata, JsonOptions);

        Assert.Contains("\"schemaVersion\"", json);
        Assert.Contains("\"primaryLanguage\"", json);
        Assert.Contains("\"contentHash\"", json);
        Assert.Contains("\"cliVersion\"", json);
        Assert.Contains("\"appVersion\"", json);
        Assert.Contains("\"createdAt\"", json);

        // C# property names should NOT appear
        Assert.DoesNotContain("SchemaVersion", json);
        Assert.DoesNotContain("PrimaryLanguage", json);
        Assert.DoesNotContain("CliToolVersion", json);
    }

    [Fact]
    public void Serialize_CreatedAt_IsIso8601()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var doc = JsonDocument.Parse(json);

        var createdAt = doc.RootElement.GetProperty("createdAt").GetString()!;
        Assert.True(DateTimeOffset.TryParse(createdAt, out _),
            $"createdAt '{createdAt}' is not valid ISO 8601");
    }

    [Fact]
    public void Serialize_AppVersion_HasCliPrefix()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var doc = JsonDocument.Parse(json);

        var appVersion = doc.RootElement.GetProperty("appVersion").GetString()!;
        Assert.StartsWith("cli-", appVersion);
    }

    [Fact]
    public void Serialize_CliVersion_MatchesConstant()
    {
        var metadata = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(metadata, JsonOptions);
        var doc = JsonDocument.Parse(json);

        Assert.Equal(CliVersion.Current,
            doc.RootElement.GetProperty("cliVersion").GetString());
    }

    #endregion

    #region Deserialization Round-Trip

    [Fact]
    public void RoundTrip_MinimalMetadata()
    {
        var original = new SidecarMetadata { Emojis = ["😂", "🐱"] };
        var json = JsonSerializer.Serialize(original, JsonOptions);
        var restored = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(restored);
        Assert.Equal(original.Emojis, restored.Emojis);
        Assert.Equal(original.SchemaVersion, restored.SchemaVersion);
    }

    [Fact]
    public void RoundTrip_FullMetadata_WithLocalizations()
    {
        var original = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "Test",
            Description = "Test desc",
            Tags = ["tag1", "tag2"],
            SearchPhrases = ["search phrase"],
            PrimaryLanguage = "en",
            ContentHash = "hash123",
            BasedOn = "Template",
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Český",
                    Description = "Popis",
                    Tags = ["tag"],
                    SearchPhrases = ["fráze"],
                },
                ["de"] = new()
                {
                    Title = "Deutsch",
                },
            },
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var restored = JsonSerializer.Deserialize<SidecarMetadata>(json, JsonOptions);

        Assert.NotNull(restored);
        Assert.Equal("Test", restored.Title);
        Assert.Equal("hash123", restored.ContentHash);
        Assert.NotNull(restored.Localizations);
        Assert.Equal(2, restored.Localizations.Count);
        Assert.Equal("Český", restored.Localizations["cs"].Title);
        Assert.Equal("Deutsch", restored.Localizations["de"].Title);
        Assert.Null(restored.Localizations["de"].Description);
    }

    #endregion

    #region LocalizedContent Serialization

    [Fact]
    public void LocalizedContent_NullFieldsOmitted()
    {
        var lc = new LocalizedContent { Title = "Test" };
        var json = JsonSerializer.Serialize(lc, JsonOptions);

        Assert.Contains("\"title\"", json);
        Assert.DoesNotContain("\"description\"", json);
        Assert.DoesNotContain("\"tags\"", json);
        Assert.DoesNotContain("\"searchPhrases\"", json);
    }

    [Fact]
    public void LocalizedContent_AllFieldsPresent()
    {
        var lc = new LocalizedContent
        {
            Title = "Titre",
            Description = "Description en français",
            Tags = ["drôle"],
            SearchPhrases = ["meme drôle"],
        };
        var json = JsonSerializer.Serialize(lc, JsonOptions);

        Assert.Contains("\"title\"", json);
        Assert.Contains("\"description\"", json);
        Assert.Contains("\"tags\"", json);
        Assert.Contains("\"searchPhrases\"", json);
    }

    #endregion

    #region CliVersion

    [Fact]
    public void CliVersion_Current_IsNotEmpty()
    {
        Assert.False(string.IsNullOrWhiteSpace(CliVersion.Current));
    }

    [Fact]
    public void CliVersion_Current_FollowsSemver()
    {
        var parts = CliVersion.Current.Split('.');
        Assert.Equal(3, parts.Length);
        Assert.All(parts, p => Assert.True(int.TryParse(p, out _),
            $"'{p}' is not a valid integer"));
    }

    #endregion
}
