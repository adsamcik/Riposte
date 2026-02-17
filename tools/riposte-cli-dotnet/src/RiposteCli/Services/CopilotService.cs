using System.Text.Json;
using System.Text.RegularExpressions;
using GitHub.Copilot.SDK;
using RiposteCli.Models;
using RiposteCli.RateLimiting;

namespace RiposteCli.Services;

/// <summary>
/// Copilot SDK integration for AI-powered meme analysis.
/// </summary>
public sealed class CopilotService
{
    /// <summary>
    /// Analyze a meme image using GitHub Copilot SDK.
    /// </summary>
    public static async Task<AnalysisResult> AnalyzeImageAsync(
        string imagePath,
        string model = "gpt-5-mini",
        bool verbose = false,
        IReadOnlyList<string>? languages = null,
        CopilotClient? client = null,
        RateLimiter? rateLimiter = null)
    {
        languages ??= ["en"];
        var systemPrompt = Prompts.GetSystemPrompt(languages.ToList());

        if (verbose)
        {
            Console.WriteLine($"  [DEBUG] Image: {Path.GetFileName(imagePath)}");
            Console.WriteLine($"  [DEBUG] Model: {model}");
            Console.WriteLine($"  [DEBUG] Languages: {string.Join(", ", languages)}");
        }

        if (rateLimiter is not null)
            await rateLimiter.WaitIfNeededAsync();

        var ownsClient = client is null;
        client ??= new CopilotClient(new CopilotClientOptions());

        try
        {
            if (ownsClient)
                await client.StartAsync();

            var session = await client.CreateSessionAsync(new SessionConfig
            {
                Model = model,
                SystemMessage = new SystemMessageConfig
                {
                    Mode = SystemMessageMode.Replace,
                    Content = systemPrompt,
                },
            });

            try
            {
                using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(120));
                var response = await session.SendAndWaitAsync(new MessageOptions
                {
                    Prompt = "Analyze this meme and provide the JSON metadata.",
                    Attachments =
                    [
                        new UserMessageDataAttachmentsItemFile
                        {
                            Path = Path.GetFullPath(imagePath),
                            DisplayName = Path.GetFileName(imagePath),
                        }
                    ],
                });

                if (response?.Data?.Content is not { Length: > 0 } resultContent)
                    throw new CopilotAnalysisException("No response from Copilot");

                if (verbose)
                    Console.WriteLine($"  [DEBUG] Response: {resultContent[..Math.Min(500, resultContent.Length)]}...");

                return ParseResponseContent(resultContent);
            }
            catch (OperationCanceledException)
            {
                throw new CopilotAnalysisException("Timeout waiting for Copilot response");
            }
            catch (CopilotAnalysisException)
            {
                throw;
            }
            catch (Exception ex)
            {
                ClassifyAndThrow(ex, rateLimiter);
                throw; // unreachable
            }
            finally
            {
                await session.DisposeAsync();
            }
        }
        catch (FileNotFoundException)
        {
            throw new CopilotNotAuthenticatedException(
                "GitHub Copilot CLI not found. Install it from https://github.com/github/copilot-cli");
        }
        finally
        {
            if (ownsClient)
                await client.DisposeAsync();
        }
    }

    /// <summary>
    /// Parse JSON from response, handling markdown code block wrapping.
    /// </summary>
    internal static AnalysisResult ParseResponseContent(string content)
    {
        content = content.Trim();

        if (content.StartsWith("```json"))
            content = content[7..];
        if (content.StartsWith("```"))
            content = content[3..];
        if (content.EndsWith("```"))
            content = content[..^3];
        content = content.Trim();

        AnalysisResult? result;
        try
        {
            result = JsonSerializer.Deserialize<AnalysisResult>(content);
        }
        catch (JsonException ex)
        {
            throw new CopilotAnalysisException(
                $"Failed to parse API response as JSON: {ex.Message}\nContent: {content[..Math.Min(200, content.Length)]}");
        }

        if (result is null || result.Emojis is not { Count: > 0 })
        {
            throw new CopilotAnalysisException(
                $"API response missing 'emojis' field: {content[..Math.Min(200, content.Length)]}");
        }

        return result;
    }

    private static void ClassifyAndThrow(Exception ex, RateLimiter? rateLimiter)
    {
        var message = ex.Message;
        var lower = message.ToLowerInvariant();

        // 429 rate limit
        if (lower.Contains("rate limit") || Regex.IsMatch(message, @"\b429\b"))
        {
            var waitTime = rateLimiter?.RecordFailure() ?? 5.0;
            throw new RateLimitException($"Rate limit exceeded. Retry after {waitTime:F1}s", waitTime);
        }

        // Server errors (500, 502, 503, 504)
        var serverMatch = Regex.Match(message, @"\b(500|502|503|504)\b");
        if (serverMatch.Success)
        {
            var statusCode = int.Parse(serverMatch.Groups[1].Value);
            var waitTime = rateLimiter?.RecordFailure(isServerError: true) ?? 5.0;
            throw new ServerErrorException($"Server error ({statusCode}). Retry after {waitTime:F1}s", statusCode);
        }

        if (lower.Contains("internal server") || lower.Contains("bad gateway") ||
            lower.Contains("gateway timeout") || lower.Contains("service unavailable"))
        {
            var waitTime = rateLimiter?.RecordFailure(isServerError: true) ?? 5.0;
            throw new ServerErrorException($"Server error. Retry after {waitTime:F1}s", 503);
        }

        throw new CopilotAnalysisException($"Copilot error: {message}");
    }
}

public class CopilotAnalysisException(string message) : Exception(message);

public class CopilotNotAuthenticatedException(string message) : CopilotAnalysisException(message);

public class RateLimitException(string message, double? retryAfter = null)
    : CopilotAnalysisException(message)
{
    public double? RetryAfter { get; } = retryAfter;
}

public class ServerErrorException(string message, int? statusCode = null)
    : CopilotAnalysisException(message)
{
    public int? StatusCode { get; } = statusCode;
}
