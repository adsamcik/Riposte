using RiposteCli.Models;
using RiposteCli.Services;

namespace RiposteCli.Tests;

/// <summary>
/// Thread-safety and concurrency tests for BuildManifest and ManifestService.
/// </summary>
public sealed class ManifestConcurrencyTests : IDisposable
{
    private readonly string _tempDir;

    public ManifestConcurrencyTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, recursive: true);
    }

    // ── Test 1: Concurrent RecordImageBuild calls don't lose entries ──

    [Fact]
    public void ConcurrentRecordImageBuild_DoesNotLoseEntries()
    {
        var manifest = new BuildManifest();
        var manifestLock = new object();
        const int workerCount = 200;

        Parallel.For(0, workerCount, i =>
        {
            var hashes = new Dictionary<string, string>
            {
                ["core"] = $"hash-core-{i}",
                ["search"] = $"hash-search-{i}",
            };

            lock (manifestLock)
            {
                ManifestService.RecordImageBuild(
                    manifest, $"image_{i}.png", $"content-{i}", "gpt-5-mini", "1.4", hashes);
            }
        });

        Assert.Equal(workerCount, manifest.Images.Count);
        for (var i = 0; i < workerCount; i++)
        {
            Assert.True(manifest.Images.ContainsKey($"image_{i}.png"), $"Missing image_{i}.png");
            Assert.Equal($"content-{i}", manifest.Images[$"image_{i}.png"].ContentHash);
        }
    }

    [Fact]
    public async Task ConcurrentRecordImageBuild_WithTaskWhenAll_DoesNotLoseEntries()
    {
        var manifest = new BuildManifest();
        var manifestLock = new object();
        const int workerCount = 100;

        var tasks = Enumerable.Range(0, workerCount).Select(i => Task.Run(() =>
        {
            var hashes = new Dictionary<string, string> { ["core"] = $"c-{i}" };

            lock (manifestLock)
            {
                ManifestService.RecordImageBuild(
                    manifest, $"img_{i}.jpg", $"hash-{i}", "model", "1.4", hashes);
            }
        }));

        await Task.WhenAll(tasks);

        Assert.Equal(workerCount, manifest.Images.Count);
    }

    // ── Test 2: Concurrent RecordPartialBuild preserves unaffected hashes ──

    [Fact]
    public void ConcurrentRecordPartialBuild_PreservesUnaffectedHashes()
    {
        var manifest = new BuildManifest();
        var manifestLock = new object();
        const int imageCount = 50;

        // Seed all images with initial full build
        for (var i = 0; i < imageCount; i++)
        {
            var hashes = new Dictionary<string, string>
            {
                ["core"] = $"core-v1-{i}",
                ["search"] = $"search-v1-{i}",
                ["cultural"] = $"cultural-v1-{i}",
            };
            ManifestService.RecordImageBuild(
                manifest, $"img_{i}.png", $"hash-{i}", "model", "1.4", hashes);
        }

        // Concurrently update only "core" group for each image
        var updatedPromptHashes = new Dictionary<string, string>
        {
            ["core"] = "core-v2",
            ["search"] = "search-v1", // unchanged
            ["cultural"] = "cultural-v1", // unchanged
        };

        Parallel.For(0, imageCount, i =>
        {
            lock (manifestLock)
            {
                ManifestService.RecordPartialBuild(
                    manifest, $"img_{i}.png", $"hash-{i}", "model", "1.5",
                    affectedGroups: new[] { "core" },
                    currentPromptHashes: updatedPromptHashes);
            }
        });

        for (var i = 0; i < imageCount; i++)
        {
            var entry = manifest.Images[$"img_{i}.png"];
            // "core" was updated
            Assert.Equal("core-v2", entry.FieldHashes["core"]);
            // "search" and "cultural" preserved from original build
            Assert.Equal($"search-v1-{i}", entry.FieldHashes["search"]);
            Assert.Equal($"cultural-v1-{i}", entry.FieldHashes["cultural"]);
        }
    }

    // ── Test 3: Record `with` expression shares Images dict ──

    [Fact]
    public void WithExpression_SharesImagesDictByReference()
    {
        var original = new BuildManifest();
        ManifestService.RecordImageBuild(
            original, "test.png", "hash1", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });

        // Simulate what AnnotateCommand does after the work loop
        var updated = original with
        {
            Model = "new-model",
            SchemaVersion = "2.0",
            PromptHashes = new Dictionary<string, string> { ["core"] = "new-hash" },
        };

        // Images dict is the SAME reference — this is the shallow copy behavior
        Assert.True(ReferenceEquals(original.Images, updated.Images),
            "Record 'with' expression should share the same Images dictionary by reference");

        // Mutation through one reference is visible through the other
        ManifestService.RecordImageBuild(
            updated, "another.png", "hash2", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c2" });

        Assert.True(original.Images.ContainsKey("another.png"),
            "Mutation via the 'with'-copy should be visible through the original's Images reference");
        Assert.Equal(2, original.Images.Count);
    }

    [Fact]
    public void WithExpression_ReplacedPropertiesAreIndependent()
    {
        var original = new BuildManifest
        {
            PromptHashes = new Dictionary<string, string> { ["core"] = "v1" },
        };

        var updated = original with
        {
            PromptHashes = new Dictionary<string, string> { ["core"] = "v2" },
        };

        // PromptHashes was replaced, so they are independent
        Assert.False(ReferenceEquals(original.PromptHashes, updated.PromptHashes));
        Assert.Equal("v1", original.PromptHashes["core"]);
        Assert.Equal("v2", updated.PromptHashes["core"]);
    }

    // ── Test 4: Multiple manifest saves don't corrupt the file ──

    [Fact]
    public void ConcurrentSaves_DoNotCorruptFile()
    {
        // Rapid sequential saves (simulating the post-loop save → strip save → bundle save pattern)
        // Each save should produce a valid manifest
        const int saveCount = 50;

        for (var i = 0; i < saveCount; i++)
        {
            var manifest = new BuildManifest
            {
                Model = $"model-{i}",
                SchemaVersion = "1.4",
            };
            ManifestService.RecordImageBuild(
                manifest, $"img_{i}.png", $"hash-{i}", $"model-{i}", "1.4",
                new Dictionary<string, string> { ["core"] = $"c-{i}" });

            ManifestService.Save(_tempDir, manifest);
        }

        // Final file should be valid and represent last save
        var loaded = ManifestService.Load(_tempDir);
        Assert.Equal($"model-{saveCount - 1}", loaded.Model);
        Assert.Single(loaded.Images);
    }

    [Fact]
    public async Task ParallelSaves_WithLock_ProduceValidManifest()
    {
        var manifest = new BuildManifest();
        var saveLock = new object();
        const int workerCount = 30;

        // Pre-populate manifest with entries
        for (var i = 0; i < workerCount; i++)
        {
            ManifestService.RecordImageBuild(
                manifest, $"img_{i}.png", $"hash-{i}", "model", "1.4",
                new Dictionary<string, string> { ["core"] = $"c-{i}" });
        }

        // Save from multiple tasks (simulating what would happen if saves overlapped)
        var tasks = Enumerable.Range(0, 10).Select(_ => Task.Run(() =>
        {
            lock (saveLock)
            {
                ManifestService.Save(_tempDir, manifest);
            }
        }));

        await Task.WhenAll(tasks);

        // File must still be valid JSON that roundtrips
        var loaded = ManifestService.Load(_tempDir);
        Assert.Equal(workerCount, loaded.Images.Count);
    }

    [Fact]
    public void AtomicSave_DoesNotLeavePartialFile_OnSuccess()
    {
        var manifest = new BuildManifest
        {
            Model = "test-model",
            SchemaVersion = "1.4",
        };
        ManifestService.RecordImageBuild(
            manifest, "img.png", "hash", "model", "1.4",
            new Dictionary<string, string> { ["core"] = "c1" });

        ManifestService.Save(_tempDir, manifest);

        // No temp file should remain after successful save
        var tempPath = Path.Combine(_tempDir, BuildManifest.FileName + ".tmp");
        Assert.False(File.Exists(tempPath), "Temp file should not remain after successful save");

        // Actual file exists and is valid
        var loaded = ManifestService.Load(_tempDir);
        Assert.Equal("test-model", loaded.Model);
        Assert.Single(loaded.Images);
    }

    // ── Test 5: Mixed concurrent RecordImageBuild and RecordPartialBuild ──

    [Fact]
    public async Task MixedConcurrentBuildAndPartialBuild_DoesNotCorruptState()
    {
        var manifest = new BuildManifest();
        var manifestLock = new object();
        const int totalImages = 100;

        // Seed half the images
        for (var i = 0; i < totalImages / 2; i++)
        {
            ManifestService.RecordImageBuild(
                manifest, $"img_{i}.png", $"hash-{i}", "model-v1", "1.4",
                new Dictionary<string, string>
                {
                    ["core"] = $"core-v1-{i}",
                    ["search"] = $"search-v1-{i}",
                });
        }

        // Concurrently: full builds for new images + partial builds for existing ones
        var tasks = Enumerable.Range(0, totalImages).Select(i => Task.Run(() =>
        {
            if (i < totalImages / 2)
            {
                // Partial build for existing image
                lock (manifestLock)
                {
                    ManifestService.RecordPartialBuild(
                        manifest, $"img_{i}.png", $"hash-{i}", "model-v2", "1.5",
                        affectedGroups: new[] { "core" },
                        currentPromptHashes: new Dictionary<string, string>
                        {
                            ["core"] = $"core-v2-{i}",
                        });
                }
            }
            else
            {
                // Full build for new image
                lock (manifestLock)
                {
                    ManifestService.RecordImageBuild(
                        manifest, $"img_{i}.png", $"hash-{i}", "model-v2", "1.5",
                        new Dictionary<string, string>
                        {
                            ["core"] = $"core-v2-{i}",
                            ["search"] = $"search-v2-{i}",
                        });
                }
            }
        }));

        await Task.WhenAll(tasks);

        Assert.Equal(totalImages, manifest.Images.Count);

        // Verify partial builds preserved unaffected "search" hash
        for (var i = 0; i < totalImages / 2; i++)
        {
            var entry = manifest.Images[$"img_{i}.png"];
            Assert.Equal($"core-v2-{i}", entry.FieldHashes["core"]);
            Assert.Equal($"search-v1-{i}", entry.FieldHashes["search"]);
        }

        // Verify full builds have both hashes
        for (var i = totalImages / 2; i < totalImages; i++)
        {
            var entry = manifest.Images[$"img_{i}.png"];
            Assert.Equal($"core-v2-{i}", entry.FieldHashes["core"]);
            Assert.Equal($"search-v2-{i}", entry.FieldHashes["search"]);
        }
    }
}
