using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

public class SidecarMetadataTests
{
    [Fact]
    public void CreateMetadata_IncludesCliVersion()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂"]);

        Assert.Equal(CliVersion.Current, metadata.CliToolVersion);
        Assert.Equal($"cli-{CliVersion.Current}", metadata.AppVersion);
    }

    [Fact]
    public void CreateMetadata_RequiredFields()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂", "🔥"]);

        Assert.Equal("1.3", metadata.SchemaVersion);
        Assert.Equal(["😂", "🔥"], metadata.Emojis);
        Assert.NotNull(metadata.CreatedAt);
        Assert.NotNull(metadata.CliToolVersion);
        Assert.NotNull(metadata.AppVersion);
    }

    [Fact]
    public void CreateMetadata_OptionalFields()
    {
        var metadata = SidecarService.CreateMetadata(
            emojis: ["😂"],
            title: "Test Meme",
            description: "A test description",
            tags: ["funny"],
            searchPhrases: ["when code works"],
            primaryLanguage: "en",
            contentHash: "abc123",
            basedOn: "Drake Hotline Bling");

        Assert.Equal("Test Meme", metadata.Title);
        Assert.Equal("A test description", metadata.Description);
        Assert.Equal(["funny"], metadata.Tags);
        Assert.Equal(["when code works"], metadata.SearchPhrases);
        Assert.Equal("en", metadata.PrimaryLanguage);
        Assert.Equal("abc123", metadata.ContentHash);
        Assert.Equal("Drake Hotline Bling", metadata.BasedOn);
    }
}
