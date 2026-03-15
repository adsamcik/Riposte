using RiposteCli.Models;

namespace RiposteCli.Tests;

public class SidecarMergerTests
{
    // ─── Helpers ────────────────────────────────────────────────

    private static SidecarMetadata CreateExisting() => new()
    {
        SchemaVersion = "1.4",
        Emojis = ["😂", "🔥"],
        CreatedAt = "2024-01-01T00:00:00Z",
        Title = "Old Title",
        Description = "Old Description",
        Tags = ["old-tag"],
        SearchPhrases = ["old phrase"],
        PrimaryLanguage = "en",
        ContentHash = "abc123",
        BasedOn = "Original Source",
        Emotions = new EmotionMetadata
        {
            Primary = "humor",
            Sentiment = "positive",
            Intensity = "high",
            Secondary = ["joy"],
            MemeUsage = ["when something is funny"],
        },
        Localizations = new Dictionary<string, LocalizedContent>
        {
            ["cs"] = new()
            {
                Title = "Český titulek",
                Description = "Český popis",
                Tags = ["tag-cs"],
                SearchPhrases = ["hledej cs"],
            },
        },
    };

    private static AnalysisResult CreateCoreResult() => new()
    {
        Emojis = ["🎉", "✨", "💯"],
        Title = "New Title",
        Description = "New Description",
    };

    private static AnalysisResult CreateSearchResult() => new()
    {
        Tags = ["new-tag-1", "new-tag-2"],
        SearchPhrases = ["new search phrase"],
    };

    private static AnalysisResult CreateCulturalResult() => new()
    {
        BasedOn = "Star Wars",
    };

    private static AnalysisResult CreateEmotionsResult() => new()
    {
        Emotions = new EmotionMetadata
        {
            Primary = "sarcasm",
            Sentiment = "mixed",
            Intensity = "medium",
            Secondary = ["irony", "amusement"],
            MemeUsage = ["when you can't believe it"],
        },
    };

    // ─── Merge: Core group ──────────────────────────────────────

    [Fact]
    public void Merge_CoreGroup_UpdatesEmojisTitleDescription()
    {
        var existing = CreateExisting();
        var partial = CreateCoreResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal(partial.Emojis, result.Emojis);
        Assert.Equal("New Title", result.Title);
        Assert.Equal("New Description", result.Description);
    }

    [Fact]
    public void Merge_CoreGroup_PreservesNonCoreFields()
    {
        var existing = CreateExisting();
        var partial = CreateCoreResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal(existing.Tags, result.Tags);
        Assert.Equal(existing.SearchPhrases, result.SearchPhrases);
        Assert.Equal(existing.BasedOn, result.BasedOn);
        Assert.Equal(existing.Emotions!.Primary, result.Emotions!.Primary);
    }

    // ─── Merge: Search group ────────────────────────────────────

    [Fact]
    public void Merge_SearchGroup_UpdatesTagsAndSearchPhrases()
    {
        var existing = CreateExisting();
        var partial = CreateSearchResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        Assert.Equal(partial.Tags, result.Tags);
        Assert.Equal(partial.SearchPhrases, result.SearchPhrases);
    }

    [Fact]
    public void Merge_SearchGroup_PreservesCoreFields()
    {
        var existing = CreateExisting();
        var partial = CreateSearchResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupSearch]);

        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal(existing.Title, result.Title);
        Assert.Equal(existing.Description, result.Description);
    }

    // ─── Merge: Cultural group ──────────────────────────────────

    [Fact]
    public void Merge_CulturalGroup_UpdatesBasedOn()
    {
        var existing = CreateExisting();
        var partial = CreateCulturalResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCultural]);

        Assert.Equal("Star Wars", result.BasedOn);
    }

    [Fact]
    public void Merge_CulturalGroup_PreservesEverythingElse()
    {
        var existing = CreateExisting();
        var partial = CreateCulturalResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCultural]);

        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal(existing.Title, result.Title);
        Assert.Equal(existing.Tags, result.Tags);
        Assert.Equal(existing.Emotions!.Primary, result.Emotions!.Primary);
    }

    // ─── Merge: Emotions group ──────────────────────────────────

    [Fact]
    public void Merge_EmotionsGroup_UpdatesEmotionsObject()
    {
        var existing = CreateExisting();
        var partial = CreateEmotionsResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.Equal("sarcasm", result.Emotions!.Primary);
        Assert.Equal("mixed", result.Emotions.Sentiment);
        Assert.Equal("medium", result.Emotions.Intensity);
        Assert.Equal(["irony", "amusement"], result.Emotions.Secondary);
    }

    [Fact]
    public void Merge_EmotionsGroup_PreservesEverythingElse()
    {
        var existing = CreateExisting();
        var partial = CreateEmotionsResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupEmotions]);

        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal(existing.Title, result.Title);
        Assert.Equal(existing.Tags, result.Tags);
        Assert.Equal(existing.BasedOn, result.BasedOn);
    }

    // ─── Merge: Localization ────────────────────────────────────

    [Fact]
    public void Merge_Localization_AddsNewLanguage()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["de"] = new()
                {
                    Title = "Deutscher Titel",
                    Description = "Deutsche Beschreibung",
                },
            },
        };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("de")]);

        Assert.NotNull(result.Localizations);
        Assert.True(result.Localizations.ContainsKey("de"));
        Assert.Equal("Deutscher Titel", result.Localizations["de"].Title);
        // Original Czech localization preserved
        Assert.True(result.Localizations.ContainsKey("cs"));
        Assert.Equal("Český titulek", result.Localizations["cs"].Title);
    }

    [Fact]
    public void Merge_Localization_OverwritesExistingLanguage()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new()
                {
                    Title = "Nový český titulek",
                    Description = "Nový český popis",
                    Tags = ["nový-tag"],
                },
            },
        };

        var result = SidecarMerger.Merge(
            existing, partial, [PromptHasher.LocalizationGroup("cs")]);

        Assert.NotNull(result.Localizations);
        Assert.Equal("Nový český titulek", result.Localizations!["cs"].Title);
        Assert.Equal("Nový český popis", result.Localizations["cs"].Description);
    }

    // ─── Merge: Multiple groups ─────────────────────────────────

    [Fact]
    public void Merge_MultipleGroups_AllUpdatedSimultaneously()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Emojis = ["🚀"],
            Title = "Multi Title",
            Description = "Multi Desc",
            Tags = ["multi-tag"],
            SearchPhrases = ["multi search"],
            BasedOn = "The Matrix",
            Emotions = new EmotionMetadata
            {
                Primary = "awe",
                Sentiment = "positive",
            },
        };

        var groups = new[]
        {
            PromptHasher.GroupCore,
            PromptHasher.GroupSearch,
            PromptHasher.GroupCultural,
            PromptHasher.GroupEmotions,
        };

        var result = SidecarMerger.Merge(existing, partial, groups);

        Assert.Equal(["🚀"], result.Emojis);
        Assert.Equal("Multi Title", result.Title);
        Assert.Equal("Multi Desc", result.Description);
        Assert.Equal(["multi-tag"], result.Tags);
        Assert.Equal(["multi search"], result.SearchPhrases);
        Assert.Equal("The Matrix", result.BasedOn);
        Assert.Equal("awe", result.Emotions!.Primary);
    }

    // ─── Merge: Null partial fields preserve existing ───────────

    [Fact]
    public void Merge_PartialWithNullEmojis_PreservesExistingEmojis()
    {
        var existing = CreateExisting();
        var partial = new AnalysisResult
        {
            Emojis = null,
            Title = "Updated Title",
            Description = "Updated Desc",
        };

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal(existing.Emojis, result.Emojis);
        Assert.Equal("Updated Title", result.Title);
        Assert.Equal("Updated Desc", result.Description);
    }

    // ─── Merge: Preserves identity fields ───────────────────────

    [Fact]
    public void Merge_PreservesCreatedAtContentHashPrimaryLanguage()
    {
        var existing = CreateExisting();
        var partial = CreateCoreResult();

        var result = SidecarMerger.Merge(existing, partial, [PromptHasher.GroupCore]);

        Assert.Equal("2024-01-01T00:00:00Z", result.CreatedAt);
        Assert.Equal("abc123", result.ContentHash);
        Assert.Equal("en", result.PrimaryLanguage);
        Assert.Equal("1.4", result.SchemaVersion);
    }

    // ─── StripRemovedGroups: Localization removal ───────────────

    [Fact]
    public void Strip_RemovesLocalization_PreservesOthers()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "CZ" },
                ["de"] = new() { Title = "DE" },
            },
        };

        // Current config only has cs localization — de should be stripped
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
            [PromptHasher.LocalizationGroup("cs")] = "h5",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.NotNull(result.Localizations);
        Assert.Single(result.Localizations);
        Assert.True(result.Localizations.ContainsKey("cs"));
        Assert.False(result.Localizations.ContainsKey("de"));
    }

    [Fact]
    public void Strip_RemovesAllLocalizations_LocalizationsBecomeNull()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "CZ" },
            },
        };

        // No localization groups in current config
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Null(result.Localizations);
    }

    // ─── StripRemovedGroups: No changes returns same reference ──

    [Fact]
    public void Strip_NoChangesNeeded_ReturnsSameReference()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Title = "Title",
            Tags = ["tag"],
            BasedOn = "Source",
            Emotions = new EmotionMetadata { Primary = "humor", Sentiment = "positive" },
            Localizations = new Dictionary<string, LocalizedContent>
            {
                ["cs"] = new() { Title = "CZ" },
            },
        };

        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
            [PromptHasher.LocalizationGroup("cs")] = "h5",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Same(existing, result);
    }

    // ─── StripRemovedGroups: Base field groups ──────────────────

    [Fact]
    public void Strip_CulturalRemoved_BasedOnBecomesNull()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            BasedOn = "Star Wars",
        };

        // cultural group missing from hashes
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Null(result.BasedOn);
    }

    [Fact]
    public void Strip_EmotionsRemoved_EmotionsBecomesNull()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Emotions = new EmotionMetadata { Primary = "humor", Sentiment = "positive" },
        };

        // emotions group missing from hashes
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupSearch] = "h2",
            [PromptHasher.GroupCultural] = "h3",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Null(result.Emotions);
    }

    [Fact]
    public void Strip_SearchRemoved_TagsAndSearchPhrasesNull()
    {
        var existing = new SidecarMetadata
        {
            Emojis = ["😂"],
            Tags = ["tag1", "tag2"],
            SearchPhrases = ["search me"],
        };

        // search group missing from hashes
        var hashes = new Dictionary<string, string>
        {
            [PromptHasher.GroupCore] = "h1",
            [PromptHasher.GroupCultural] = "h3",
            [PromptHasher.GroupEmotions] = "h4",
        };

        var result = SidecarMerger.StripRemovedGroups(existing, hashes);

        Assert.Null(result.Tags);
        Assert.Null(result.SearchPhrases);
    }
}
