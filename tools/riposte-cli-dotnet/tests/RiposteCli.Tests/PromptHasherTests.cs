namespace RiposteCli.Tests;

public class PromptHasherTests
{
    // ── Determinism ──────────────────────────────────────────────────

    [Fact]
    public void Hash_SameInput_ReturnsSameOutput()
    {
        var input = "hello world";
        Assert.Equal(PromptHasher.Hash(input), PromptHasher.Hash(input));
    }

    [Fact]
    public void ComputeAll_CalledTwice_ReturnsSameHashes()
    {
        var languages = new[] { "en" };
        var first = PromptHasher.ComputeAll(languages);
        var second = PromptHasher.ComputeAll(languages);

        foreach (var key in first.Keys)
        {
            Assert.Equal(first[key], second[key]);
        }
    }

    // ── Uniqueness ───────────────────────────────────────────────────

    [Fact]
    public void Hash_DifferentInputs_ReturnDifferentHashes()
    {
        Assert.NotEqual(PromptHasher.Hash("alpha"), PromptHasher.Hash("beta"));
    }

    [Fact]
    public void ComputeAll_BaseGroups_AllHaveDistinctValues()
    {
        var hashes = PromptHasher.ComputeAll(new[] { "en" });
        var values = new HashSet<string>(hashes.Values);

        Assert.Equal(hashes.Count, values.Count);
    }

    // ── Language sensitivity ─────────────────────────────────────────

    [Fact]
    public void ComputeAll_DifferentLanguage_ChangesCoreSensitiveGroups()
    {
        var en = PromptHasher.ComputeAll(new[] { "en" });
        var cs = PromptHasher.ComputeAll(new[] { "cs" });

        Assert.NotEqual(en[PromptHasher.GroupCore], cs[PromptHasher.GroupCore]);
        Assert.NotEqual(en[PromptHasher.GroupSearch], cs[PromptHasher.GroupSearch]);
        Assert.NotEqual(en[PromptHasher.GroupEmotions], cs[PromptHasher.GroupEmotions]);
    }

    [Fact]
    public void ComputeAll_DifferentLanguage_CulturalGroupUnchanged()
    {
        var en = PromptHasher.ComputeAll(new[] { "en" });
        var cs = PromptHasher.ComputeAll(new[] { "cs" });

        Assert.Equal(en[PromptHasher.GroupCultural], cs[PromptHasher.GroupCultural]);
    }

    // ── ComputeAll coverage ──────────────────────────────────────────

    [Fact]
    public void ComputeAll_WithSingleLanguage_ReturnsBaseGroupsOnly()
    {
        var hashes = PromptHasher.ComputeAll(new[] { "en" });

        Assert.Equal(PromptHasher.BaseGroups.Count, hashes.Count);
        foreach (var group in PromptHasher.BaseGroups)
        {
            Assert.True(hashes.ContainsKey(group), $"Missing key: {group}");
        }
    }

    [Fact]
    public void ComputeAll_WithMultipleLanguages_ReturnsBaseAndLocalizationKeys()
    {
        var languages = new[] { "en", "cs", "de" };
        var hashes = PromptHasher.ComputeAll(languages);

        var expectedCount = PromptHasher.BaseGroups.Count + 2; // cs + de
        Assert.Equal(expectedCount, hashes.Count);

        foreach (var group in PromptHasher.BaseGroups)
        {
            Assert.True(hashes.ContainsKey(group), $"Missing base key: {group}");
        }

        Assert.True(hashes.ContainsKey(PromptHasher.LocalizationGroup("cs")));
        Assert.True(hashes.ContainsKey(PromptHasher.LocalizationGroup("de")));
    }

    // ── Localization groups ──────────────────────────────────────────

    [Fact]
    public void LocalizationGroup_FormatsCorrectly()
    {
        Assert.Equal("localization:cs", PromptHasher.LocalizationGroup("cs"));
        Assert.Equal("localization:de", PromptHasher.LocalizationGroup("de"));
    }

    [Fact]
    public void ComputeAll_PrimaryLanguage_DoesNotGetLocalizationKey()
    {
        var hashes = PromptHasher.ComputeAll(new[] { "en", "cs" });

        Assert.False(hashes.ContainsKey(PromptHasher.LocalizationGroup("en")));
        Assert.True(hashes.ContainsKey(PromptHasher.LocalizationGroup("cs")));
    }

    [Fact]
    public void ComputeAll_DifferentSecondaryLanguages_ProduceDifferentLocalizationHashes()
    {
        var withCs = PromptHasher.ComputeAll(new[] { "en", "cs" });
        var withDe = PromptHasher.ComputeAll(new[] { "en", "de" });

        Assert.NotEqual(
            withCs[PromptHasher.LocalizationGroup("cs")],
            withDe[PromptHasher.LocalizationGroup("de")]);
    }

    // ── Hash format ──────────────────────────────────────────────────

    [Fact]
    public void Hash_ReturnsLowercaseHex64Chars()
    {
        var hash = PromptHasher.Hash("test input");

        Assert.Equal(64, hash.Length);
        Assert.Matches("^[0-9a-f]{64}$", hash);
    }

    [Fact]
    public void ComputeAll_AllValues_AreSha256Format()
    {
        var hashes = PromptHasher.ComputeAll(new[] { "en", "cs" });

        foreach (var (key, value) in hashes)
        {
            Assert.Equal(64, value.Length);
            Assert.Matches("^[0-9a-f]{64}$", value);
        }
    }

    // ── Empty / edge cases ───────────────────────────────────────────

    [Fact]
    public void ComputeAll_EmptyLanguageList_DefaultsToEnglish()
    {
        var empty = PromptHasher.ComputeAll(Array.Empty<string>());
        var en = PromptHasher.ComputeAll(new[] { "en" });

        Assert.Equal(PromptHasher.BaseGroups.Count, empty.Count);

        foreach (var group in PromptHasher.BaseGroups)
        {
            Assert.Equal(en[group], empty[group]);
        }
    }

    [Fact]
    public void Hash_EmptyString_ReturnsValidHash()
    {
        var hash = PromptHasher.Hash(string.Empty);

        Assert.Equal(64, hash.Length);
        Assert.Matches("^[0-9a-f]{64}$", hash);
    }

    [Fact]
    public void Hash_EmptyString_ReturnsSha256OfEmpty()
    {
        // Well-known SHA-256 of empty string
        var expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        Assert.Equal(expected, PromptHasher.Hash(string.Empty));
    }
}
