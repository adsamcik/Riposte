using System.Text.Json;
using RiposteCli.Models;

namespace RiposteCli.Tests;

/// <summary>
/// Tests for model classes: SidecarMetadata defaults, CliVersion, LocalizedContent,
/// AnalysisResult, DeduplicationResult, HashEntry.
/// </summary>
public class ModelTests
{
    #region SidecarMetadata Defaults

    [Fact]
    public void SidecarMetadata_SchemaVersion_Is13()
    {
        var meta = new SidecarMetadata { Emojis = ["😂"] };
        Assert.Equal("1.3", meta.SchemaVersion);
    }

    [Fact]
    public void SidecarMetadata_CreatedAt_IsIso8601()
    {
        var meta = new SidecarMetadata { Emojis = ["😂"] };
        Assert.True(DateTimeOffset.TryParse(meta.CreatedAt, out _),
            $"CreatedAt '{meta.CreatedAt}' is not valid ISO 8601");
    }

    [Fact]
    public void SidecarMetadata_AppVersion_ContainsCli()
    {
        var meta = new SidecarMetadata { Emojis = ["😂"] };
        Assert.StartsWith("cli-", meta.AppVersion);
    }

    [Fact]
    public void SidecarMetadata_CliToolVersion_MatchesCliVersion()
    {
        var meta = new SidecarMetadata { Emojis = ["😂"] };
        Assert.Equal(CliVersion.Current, meta.CliToolVersion);
    }

    #endregion

    #region CliVersion

    [Fact]
    public void CliVersion_Current_IsNotEmpty()
    {
        Assert.NotEmpty(CliVersion.Current);
    }

    [Fact]
    public void CliVersion_Current_IsSemVer()
    {
        Assert.True(System.Version.TryParse(CliVersion.Current, out _),
            $"CliVersion '{CliVersion.Current}' is not valid semver");
    }

    #endregion

    #region SidecarMetadata Serialization

    [Fact]
    public void SidecarMetadata_Serialization_NullFieldsOmitted()
    {
        var meta = new SidecarMetadata { Emojis = ["😂"] };
        var json = JsonSerializer.Serialize(meta, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });

        Assert.DoesNotContain("\"title\"", json);
        Assert.DoesNotContain("\"description\"", json);
        Assert.DoesNotContain("\"tags\"", json);
        Assert.DoesNotContain("\"searchPhrases\"", json);
        Assert.DoesNotContain("\"basedOn\"", json);
        Assert.DoesNotContain("\"contentHash\"", json);
        Assert.DoesNotContain("\"localizations\"", json);
        Assert.DoesNotContain("\"primaryLanguage\"", json);
    }

    [Fact]
    public void SidecarMetadata_Serialization_PresentFieldsIncluded()
    {
        var meta = new SidecarMetadata
        {
            Emojis = ["😂", "🔥"],
            Title = "Test",
            Description = "A test meme",
            Tags = ["test", "meme"],
        };

        var json = JsonSerializer.Serialize(meta);
        Assert.Contains("\"title\"", json);
        Assert.Contains("\"description\"", json);
        Assert.Contains("\"tags\"", json);
    }

    [Fact]
    public void SidecarMetadata_Deserialization_RoundTrip()
    {
        var original = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "Test",
            PrimaryLanguage = "en",
            ContentHash = "abc123",
            BasedOn = "Doge",
        };

        var json = JsonSerializer.Serialize(original);
        var deserialized = JsonSerializer.Deserialize<SidecarMetadata>(json);

        Assert.NotNull(deserialized);
        Assert.Equal(original.Emojis, deserialized.Emojis);
        Assert.Equal(original.Title, deserialized.Title);
        Assert.Equal(original.PrimaryLanguage, deserialized.PrimaryLanguage);
        Assert.Equal(original.ContentHash, deserialized.ContentHash);
        Assert.Equal(original.BasedOn, deserialized.BasedOn);
        Assert.Equal("1.3", deserialized.SchemaVersion);
    }

    #endregion

    #region LocalizedContent

    [Fact]
    public void LocalizedContent_AllNullFields_SerializesEmpty()
    {
        var lc = new LocalizedContent();
        var json = JsonSerializer.Serialize(lc, new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        });
        Assert.Equal("{}", json);
    }

    [Fact]
    public void LocalizedContent_WithAllFields_Serializes()
    {
        var lc = new LocalizedContent
        {
            Title = "Titre",
            Description = "Description en français",
            Tags = ["drôle", "meme"],
            SearchPhrases = ["meme drôle"],
        };

        var json = JsonSerializer.Serialize(lc);
        Assert.Contains("Titre", json);
        Assert.Contains("Description en fran", json);
    }

    [Fact]
    public void LocalizedContent_Deserialization_RoundTrip()
    {
        var original = new LocalizedContent
        {
            Title = "Titel",
            Description = "Beschreibung",
            Tags = ["lustig"],
        };

        var json = JsonSerializer.Serialize(original);
        var deserialized = JsonSerializer.Deserialize<LocalizedContent>(json);

        Assert.NotNull(deserialized);
        Assert.Equal("Titel", deserialized.Title);
        Assert.Equal("Beschreibung", deserialized.Description);
        Assert.Equal(["lustig"], deserialized.Tags);
        Assert.Null(deserialized.SearchPhrases);
    }

    #endregion

    #region AnalysisResult

    [Fact]
    public void AnalysisResult_Deserialization_AllFields()
    {
        var json = """
            {
                "emojis": ["😂", "🐱"],
                "title": "Cat Meme",
                "description": "A funny cat",
                "tags": ["cat", "funny"],
                "searchPhrases": ["funny cat meme"],
                "basedOn": "Grumpy Cat",
                "localizations": {
                    "cs": {
                        "title": "Kočičí Meme",
                        "description": "Vtipná kočka"
                    }
                }
            }
            """;

        var result = JsonSerializer.Deserialize<AnalysisResult>(json);
        Assert.NotNull(result);
        Assert.Equal(["😂", "🐱"], result.Emojis);
        Assert.Equal("Cat Meme", result.Title);
        Assert.Equal("A funny cat", result.Description);
        Assert.Equal(["cat", "funny"], result.Tags);
        Assert.Equal(["funny cat meme"], result.SearchPhrases);
        Assert.Equal("Grumpy Cat", result.BasedOn);
        Assert.NotNull(result.Localizations);
        Assert.Equal("Kočičí Meme", result.Localizations["cs"].Title);
    }

    [Fact]
    public void AnalysisResult_Deserialization_MinimalFields()
    {
        var json = """{"emojis": ["🎉"]}""";
        var result = JsonSerializer.Deserialize<AnalysisResult>(json);

        Assert.NotNull(result);
        Assert.Single(result.Emojis);
        Assert.Null(result.Title);
        Assert.Null(result.Description);
        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
        Assert.Null(result.BasedOn);
        Assert.Null(result.Localizations);
    }

    #endregion

    #region DeduplicationResult

    [Fact]
    public void DeduplicationResult_EmptyCollections()
    {
        var result = new DeduplicationResult
        {
            UniqueImages = [],
            ExactDuplicates = [],
            NearDuplicates = [],
        };

        Assert.Empty(result.UniqueImages);
        Assert.Empty(result.ExactDuplicates);
        Assert.Empty(result.NearDuplicates);
    }

    [Fact]
    public void DeduplicationResult_WithData()
    {
        var result = new DeduplicationResult
        {
            UniqueImages = ["a.jpg"],
            ExactDuplicates = [("b.jpg", "a.jpg")],
            NearDuplicates = [("c.jpg", "a.jpg", 3)],
        };

        Assert.Single(result.UniqueImages);
        Assert.Single(result.ExactDuplicates);
        Assert.Equal("a.jpg", result.ExactDuplicates[0].Original);
        Assert.Equal(3, result.NearDuplicates[0].Distance);
    }

    #endregion

    #region HashEntry Record

    [Fact]
    public void HashEntry_Equality()
    {
        var a = new HashEntry("hash1", "phash1");
        var b = new HashEntry("hash1", "phash1");
        Assert.Equal(a, b);
    }

    [Fact]
    public void HashEntry_Inequality()
    {
        var a = new HashEntry("hash1", "phash1");
        var b = new HashEntry("hash2", "phash1");
        Assert.NotEqual(a, b);
    }

    [Fact]
    public void HashEntry_NullPhash()
    {
        var entry = new HashEntry("hash1", null);
        Assert.Null(entry.PerceptualHash);
        Assert.Equal("hash1", entry.ContentHash);
    }

    [Fact]
    public void HashEntry_Deconstruction()
    {
        var entry = new HashEntry("hash1", "phash1");
        var (contentHash, phash) = entry;
        Assert.Equal("hash1", contentHash);
        Assert.Equal("phash1", phash);
    }

    #endregion
}
