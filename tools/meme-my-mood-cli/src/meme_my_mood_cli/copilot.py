"""GitHub Models API client for image analysis."""

import base64
import json
from pathlib import Path
from typing import Any

import httpx


class CopilotError(Exception):
    """Base exception for API errors."""
    pass


class CopilotNotAuthenticatedError(CopilotError):
    """Raised when not authenticated or token is invalid."""
    pass


class RateLimitError(CopilotError):
    """Raised when API rate limit is exceeded."""
    pass


# GitHub Models API endpoint
API_ENDPOINT = "https://models.github.ai/inference/chat/completions"

# MIME type mapping
MIME_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".gif": "image/gif",
    ".bmp": "image/bmp",
    ".tiff": "image/tiff",
    ".tif": "image/tiff",
    ".heic": "image/heic",
    ".heif": "image/heif",
    ".avif": "image/avif",
    ".jxl": "image/jxl",
}

# System prompt for meme analysis
SYSTEM_PROMPT = """You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with the following fields:

1. "emojis": An array of 1-5 Unicode emoji characters that best represent the mood, emotion, or theme of the meme.

2. "title": A simple, descriptive title that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image. Examples: "Confused cat at computer", "Drake pointing meme", "Distracted boyfriend".

3. "description": A brief description of what's happening in the meme (max 200 characters).

4. "textContent": Any text visible in the meme image, extracted verbatim. If no text, omit this field.

5. "tags": An array of 3-8 lowercase keywords/tags for searching.

Respond ONLY with valid JSON, no markdown or explanation. Example:
{"emojis": ["😂", "🐱"], "title": "Confused cat at computer", "description": "A cat staring at code with a confused expression", "textContent": "When the code works but you don't know why", "tags": ["cat", "programming", "confused", "funny"]}"""


def get_mime_type(path: Path) -> str:
    """Get the MIME type for an image file."""
    return MIME_TYPES.get(path.suffix.lower(), "image/jpeg")


def encode_image(image_path: Path) -> tuple[str, str]:
    """Encode an image as base64 with MIME type."""
    with open(image_path, "rb") as f:
        data = base64.b64encode(f.read()).decode("utf-8")
    mime_type = get_mime_type(image_path)
    return data, mime_type


def analyze_image(
    image_path: Path,
    access_token: str,
    model: str = "openai/gpt-4.1",
    verbose: bool = False,
) -> dict[str, Any]:
    """Analyze a meme image using GitHub Models API.
    
    Args:
        image_path: Path to the image file.
        access_token: GitHub PAT with 'models' scope.
        model: Model to use (default: openai/gpt-4o).
        verbose: Whether to print debug information.
        
    Returns:
        Dictionary with emojis, title, description, textContent, and tags.
        
    Raises:
        CopilotNotAuthenticatedError: If token is invalid.
        RateLimitError: If rate limit is exceeded.
        CopilotError: For other errors.
    """
    # Encode image
    image_data, mime_type = encode_image(image_path)
    
    if verbose:
        print(f"  [DEBUG] Image: {image_path.name} ({mime_type})")
        print(f"  [DEBUG] Model: {model}")
        print(f"  [DEBUG] Endpoint: {API_ENDPOINT}")
    
    # Build request payload
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": SYSTEM_PROMPT,
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{mime_type};base64,{image_data}",
                        },
                    },
                    {
                        "type": "text",
                        "text": "Analyze this meme and provide the JSON metadata.",
                    },
                ],
            },
        ],
        "max_tokens": 500,
        "temperature": 0.7,
    }
    
    # Make API request
    with httpx.Client(timeout=60.0) as client:
        response = client.post(
            API_ENDPOINT,
            json=payload,
            headers={
                "Authorization": f"Bearer {access_token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "Content-Type": "application/json",
            },
        )
    
    if verbose:
        print(f"  [DEBUG] Response status: {response.status_code}")
    
    # Handle errors
    if response.status_code == 401:
        raise CopilotNotAuthenticatedError(
            "Invalid or expired token. "
            "Create a PAT with 'models' scope at https://github.com/settings/tokens"
        )
    
    if response.status_code == 403:
        error_msg = response.text
        if "models" in error_msg.lower():
            raise CopilotNotAuthenticatedError(
                "Token missing 'models' scope. "
                "Create a PAT with 'models' scope at https://github.com/settings/tokens"
            )
        raise CopilotError(f"Access denied: {error_msg}")
    
    if response.status_code == 429:
        raise RateLimitError("Rate limit exceeded")
    
    if response.status_code != 200:
        raise CopilotError(
            f"API error {response.status_code}: {response.text}"
        )
    
    # Parse response
    result = response.json()
    
    if verbose:
        print(f"  [DEBUG] Response: {json.dumps(result, indent=2)[:500]}...")
    
    content = result["choices"][0]["message"]["content"]
    
    # Parse JSON from response (handle potential markdown wrapping)
    content = content.strip()
    if content.startswith("```json"):
        content = content[7:]
    if content.startswith("```"):
        content = content[3:]
    if content.endswith("```"):
        content = content[:-3]
    content = content.strip()
    
    try:
        parsed = json.loads(content)
        # Validate required fields
        if "emojis" not in parsed or not parsed["emojis"]:
            raise CopilotError(
                f"API response missing 'emojis' field: {content[:200]}"
            )
        return parsed
    except json.JSONDecodeError as e:
        raise CopilotError(
            f"Failed to parse API response as JSON: {e}\nContent: {content[:200]}"
        ) from e
