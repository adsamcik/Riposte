# GitHub Models API Documentation

> **Last Updated:** January 25, 2026
> 
> **⚠️ DEPRECATED:** The CLI now uses the **GitHub Copilot SDK** instead of direct API calls.
> See `tools/riposte-cli/README.md` for current usage.

## Overview

This document describes the direct GitHub Models API. The **riposte-cli** tool previously used this API but has been migrated to use the **GitHub Copilot SDK** (`github-copilot-sdk` package), which provides:

- Automatic authentication via Copilot CLI
- Built-in session management
- Streaming support
- Proper error handling

## Direct API (for reference)

## API Endpoint

```
POST https://models.github.ai/inference/chat/completions
```

## Authentication

Requires a GitHub Personal Access Token (PAT) with the `models` scope.

**Create a PAT:**
1. Go to https://github.com/settings/tokens
2. Generate new token (classic or fine-grained)
3. Select the `models` scope
4. Use the token in the `Authorization` header

**Note:** The `copilot` scope is NOT sufficient. You need the `models` scope specifically.

## Request Format

```json
{
  "model": "openai/gpt-4.1",
  "messages": [
    {
      "role": "system",
      "content": "System prompt here"
    },
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,{base64_encoded_image}"
          }
        },
        {
          "type": "text",
          "text": "User message here"
        }
      ]
    }
  ],
  "max_tokens": 500,
  "temperature": 0.7
}
```

## Headers

```
Authorization: Bearer {GITHUB_PAT}
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
Content-Type: application/json
```

## Model Naming

Models must include the provider prefix:
- ✅ `openai/gpt-4.1`
- ✅ `openai/gpt-4o`
- ✅ `openai/gpt-4o-mini`
- ❌ `gpt-4.1` (missing provider prefix)

## Response Format

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{ ... JSON response ... }"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1234,
    "completion_tokens": 100,
    "total_tokens": 1334
  }
}
```

## Error Codes

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 401 | Invalid or expired token |
| 403 | Token missing `models` scope |
| 429 | Rate limit exceeded |
| 500 | Server error |

## Rate Limiting

The API has rate limits based on your GitHub subscription tier. The CLI handles rate limiting with appropriate error messages.

## Image Support

Supported image formats:
- JPEG, PNG, WebP, GIF, BMP, TIFF
- HEIC/HEIF (if Pillow plugin installed)
- AVIF, JPEG XL (if Pillow plugins installed)

Images are base64-encoded and sent as data URLs in the request.

## Usage in riposte-cli

The CLI wraps this API in `src/riposte_cli/copilot.py`:

```python
from riposte_cli.copilot import analyze_image

result = analyze_image(
    image_path=Path("meme.jpg"),
    access_token="github_pat_...",
    model="openai/gpt-4.1",
)
# Returns: {"emojis": [...], "title": "...", ...}
```

## Comparison: GitHub Models API vs Copilot SDK

| Feature | GitHub Models API | Copilot SDK |
|---------|-------------------|-------------|
| Authentication | PAT with `models` scope | Copilot CLI login |
| Backend | Direct API call | Copilot CLI server |
| Dependencies | Just `httpx` | `github-copilot-sdk` + Copilot CLI |
| Image support | Via base64 in message | Via file attachments |
| Offline support | No | No |

**We chose GitHub Models API** because:
1. Simpler setup (no Copilot CLI required)
2. Direct control over requests
3. Works with just a PAT
4. Better for CI/CD environments

## Resources

- [GitHub Models Documentation](https://docs.github.com/en/github-models)
- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
