"""GitHub Copilot SDK client for image analysis.

Uses the Copilot SDK (github-copilot-sdk) for AI-powered meme analysis.
Requires GitHub Copilot CLI installed and in PATH.
"""

import asyncio
import json
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from copilot import CopilotClient


class CopilotError(Exception):
    """Base exception for Copilot errors."""
    pass


class CopilotNotAuthenticatedError(CopilotError):
    """Raised when not authenticated or CLI not available."""
    pass


class RateLimitError(CopilotError):
    """Raised when API rate limit is exceeded."""
    
    def __init__(self, message: str, retry_after: float | None = None):
        super().__init__(message)
        self.retry_after = retry_after


class ServerError(CopilotError):
    """Raised for server errors (500, 502, 504) which may be transient."""
    
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


# System prompt for meme analysis (single language)
SYSTEM_PROMPT = """You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with the following fields:

1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

2. "title": A simple, descriptive title that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image. Examples: "Confused cat at computer", "Drake pointing meme", "Distracted boyfriend".

3. "description": A thorough description covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.

4. "tags": An array of 8-15 lowercase keywords covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

5. "searchPhrases": An array of 2-3 short natural language phrases someone might type when searching for this meme.

6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.

Respond ONLY with valid JSON, no markdown or explanation. Example:
{"emojis": ["😂", "🐱", "💻", "🤷"], "title": "Confused cat at computer", "description": "A cat sitting at a desk staring at a screen full of code with a bewildered expression. The text reads 'When the code works but you don't know why.' Captures the mass confusion and accidental success familiar to every programmer.", "tags": ["cat", "programming", "confused", "funny", "code", "developer", "it works", "bugs", "software", "humor", "relatable", "reaction"], "searchPhrases": ["that feeling when the code works", "confused programmer cat", "code works no idea why"], "basedOn": "Programmer humor"}"""


def get_multilingual_system_prompt(languages: list[str]) -> str:
    """Generate a system prompt for multilingual meme analysis.
    
    Args:
        languages: List of BCP 47 language codes (e.g., ["en", "cs", "de"]).
        
    Returns:
        System prompt string for multilingual analysis.
    """
    primary_lang = languages[0] if languages else "en"
    additional_langs = languages[1:] if len(languages) > 1 else []
    
    # Language name mapping for clearer prompts
    language_names = {
        "en": "English",
        "cs": "Czech",
        "de": "German",
        "es": "Spanish",
        "fr": "French",
        "it": "Italian",
        "ja": "Japanese",
        "ko": "Korean",
        "pl": "Polish",
        "pt": "Portuguese",
        "ru": "Russian",
        "uk": "Ukrainian",
        "zh": "Chinese (Simplified)",
        "zh-TW": "Chinese (Traditional)",
    }
    
    primary_name = language_names.get(primary_lang, primary_lang)
    
    if not additional_langs:
        # Single language - use the standard prompt with language specification
        return f"""You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with the following fields.

IMPORTANT: All text fields (title, description, tags, searchPhrases) must be in {primary_name} ({primary_lang}).

Fields:
1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

2. "title": A simple, descriptive title in {primary_name} that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image.

3. "description": A thorough description in {primary_name} covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.

4. "tags": An array of 8-15 lowercase keywords/tags in {primary_name} covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

5. "searchPhrases": An array of 2-3 short natural language phrases in {primary_name} someone might type when searching for this meme.

6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.

Respond ONLY with valid JSON, no markdown or explanation. Example:
{{"emojis": ["😂", "🐱", "💻", "🤷"], "title": "Confused cat at computer", "description": "A cat sitting at a desk staring at a screen full of code with a bewildered expression.", "tags": ["cat", "programming", "confused", "funny", "code", "developer", "humor", "relatable", "reaction"], "searchPhrases": ["confused programmer cat", "code works no idea why"], "basedOn": "Programmer humor"}}"""
    
    # Multiple languages - include localizations
    additional_names = [language_names.get(lang, lang) for lang in additional_langs]
    additional_desc = ", ".join(f"{name} ({code})" for name, code in zip(additional_names, additional_langs))
    
    return f"""You are a meme analysis assistant. Analyze the provided meme image and return a JSON object with multilingual content.

PRIMARY LANGUAGE: {primary_name} ({primary_lang})
ADDITIONAL LANGUAGES: {additional_desc}

Fields:
1. "emojis": An array of 1-8 Unicode emoji characters that best represent the mood, emotion, or theme of the meme. Order from most significant/relevant to least significant. All emojis should be relevant.

2. "title": A simple, descriptive title in {primary_name} that plainly describes the meme content (max 50 characters). Don't try to be clever or catchy - just describe what's in the image.

3. "description": A thorough description in {primary_name} covering: what's literally in the image, the mood or emotion it conveys, and any themes or cultural references it relates to (e.g., programming, Witcher, Harry Potter, GTA, science, etc.). If there is text visible in the image, incorporate it naturally into your description.

4. "tags": An array of 8-15 lowercase keywords/tags in {primary_name} covering: subject matter, emotion/mood, synonyms, common slang, meme format name if recognizable, and related cultural references.

5. "searchPhrases": An array of 2-3 short natural language phrases in {primary_name} someone might type when searching for this meme.

6. "basedOn": If the image is based on a recognizable meme template, franchise, video game, movie, TV show, or other cultural reference, provide its name. Use the most commonly known name. If the source is not recognizable or the image is original content, omit this field or set to null.

7. "localizations": An object containing translations for each additional language. Each key is a language code, and each value is an object with "title", "description", "tags", and "searchPhrases" fields in that language.

Respond ONLY with valid JSON, no markdown or explanation. Example with Czech and German localizations:
{{"emojis": ["😂", "🐱", "💻", "🤷"], "title": "Confused cat at computer", "description": "A cat sitting at a desk staring at a screen full of code with a bewildered expression. The text reads 'When the code works but you don't know why.' Captures the confusion and accidental success familiar to every programmer.", "tags": ["cat", "programming", "confused", "funny", "code", "developer", "it works", "bugs", "software", "humor", "relatable", "reaction"], "searchPhrases": ["that feeling when the code works", "confused programmer cat", "code works no idea why"], "basedOn": "Programmer humor", "localizations":{{"cs": {{"title": "Zmatená kočka u počítače", "description": "Kočka sedí u stolu a zírá na obrazovku plnou kódu se zmateným výrazem. Text říká 'Když kód funguje, ale nevíš proč.' Zachycuje zmatek a náhodný úspěch známý každému programátorovi.", "tags": ["kočka", "programování", "zmatený", "vtipné", "kód", "vývojář", "funguje to", "chyby", "software", "humor", "relatable", "reakce"], "searchPhrases": ["když kód funguje", "zmatený programátor kočka", "kód funguje nevím proč"]}}, "de": {{"title": "Verwirrte Katze am Computer", "description": "Eine Katze sitzt am Schreibtisch und starrt verwirrt auf einen Bildschirm voller Code. Der Text lautet 'Wenn der Code funktioniert, aber du nicht weißt warum.' Fängt die Verwirrung und den zufälligen Erfolg ein, den jeder Programmierer kennt.", "tags": ["katze", "programmierung", "verwirrt", "lustig", "code", "entwickler", "es funktioniert", "bugs", "software", "humor", "nachvollziehbar", "reaktion"], "searchPhrases": ["wenn der Code funktioniert", "verwirrte Programmierer Katze", "Code funktioniert keine Ahnung warum"]}}}}}}"""


@dataclass
class RateLimiter:
    """Adaptive rate limiter with exponential backoff for API errors.
    
    Based on GitHub Copilot SDK best practices (January 2026):
    - Respects Retry-After headers when available
    - Exponential backoff with jitter on repeated failures
    - Handles 429 (rate limit), 500, 502, 504 (server errors)
    - Enforces 1-second minimum between requests (per SDK guidance)
    - Adaptive throttling based on error rate
    """
    
    # Base delay settings - SDK recommends at least 1 second between requests
    min_delay: float = 1.0  # Minimum delay between requests (1 second per SDK docs)
    max_delay: float = 300.0  # Maximum delay (5 minutes)
    
    # Backoff settings
    base_backoff: float = 2.0  # Base for exponential backoff
    max_backoff_attempts: int = 8  # Max consecutive failures before giving up
    jitter_factor: float = 0.25  # Random jitter as fraction of delay (25%)
    
    # Adaptive throttling
    error_window: int = 10  # Number of recent requests to track
    error_threshold: float = 0.3  # Error rate threshold to slow down
    
    # State (using field with default_factory for mutable defaults)
    consecutive_failures: int = field(default=0, init=False)
    last_request_time: float = field(default=0.0, init=False)
    current_delay: float = field(default=1.0, init=False)  # Start at 1 second
    recent_results: list[bool] = field(default_factory=list, init=False)
    
    def record_success(self) -> None:
        """Record a successful request and reduce throttling."""
        self.consecutive_failures = 0
        # Gradually reduce delay but never below minimum
        self.current_delay = max(self.min_delay, self.current_delay * 0.9)
        self._add_result(True)
    
    def record_failure(
        self,
        retry_after: float | None = None,
        is_server_error: bool = False,
    ) -> float:
        """Record a failed request and calculate wait time.
        
        Args:
            retry_after: Server-specified retry delay (from Retry-After header).
            is_server_error: If True, this is a 500/502/504 error (use shorter backoff).
            
        Returns:
            Recommended wait time in seconds.
        """
        self.consecutive_failures += 1
        self._add_result(False)
        
        # If server specified retry delay, use it (with some buffer)
        if retry_after is not None:
            wait_time = retry_after * 1.1  # Add 10% buffer
            self.current_delay = max(self.current_delay, wait_time)
            return wait_time
        
        # Calculate exponential backoff with random jitter
        import random
        exponent = min(self.consecutive_failures, self.max_backoff_attempts)
        
        # Server errors typically resolve faster, use shorter backoff
        if is_server_error:
            base_wait = self.base_backoff ** (exponent * 0.5)  # Half exponent
        else:
            base_wait = self.base_backoff ** exponent
        
        # Add random jitter to prevent thundering herd
        jitter = base_wait * self.jitter_factor * random.uniform(-1, 1)
        wait_time = min(base_wait + jitter, self.max_delay)
        wait_time = max(wait_time, self.min_delay)  # Never below minimum
        
        self.current_delay = max(self.current_delay, wait_time)
        return wait_time
    
    def should_give_up(self) -> bool:
        """Check if we should stop retrying."""
        return self.consecutive_failures >= self.max_backoff_attempts
    
    async def wait_if_needed(self) -> None:
        """Wait before making the next request if needed.
        
        Enforces minimum 1-second delay between requests per SDK guidance.
        """
        now = time.monotonic()
        elapsed = now - self.last_request_time
        
        # Apply adaptive throttling based on recent error rate
        delay = self.current_delay
        error_rate = self._get_error_rate()
        if error_rate > self.error_threshold:
            delay = delay * (1 + error_rate)  # Slow down proportionally
        
        if elapsed < delay:
            await asyncio.sleep(delay - elapsed)
        
        self.last_request_time = time.monotonic()
    
    def _add_result(self, success: bool) -> None:
        """Add a result to the recent history."""
        self.recent_results.append(success)
        if len(self.recent_results) > self.error_window:
            self.recent_results.pop(0)
    
    def _get_error_rate(self) -> float:
        """Get the recent error rate."""
        if not self.recent_results:
            return 0.0
        failures = sum(1 for r in self.recent_results if not r)
        return failures / len(self.recent_results)


# Global rate limiter instance
_rate_limiter = RateLimiter()


def _parse_response_content(content: str) -> dict[str, Any]:
    """Parse JSON from response, handling markdown wrapping.
    
    Args:
        content: Raw response content from the model.
        
    Returns:
        Parsed JSON dictionary.
        
    Raises:
        CopilotError: If parsing fails or required fields are missing.
    """
    content = content.strip()
    
    # Handle markdown code block wrapping
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


async def analyze_image_async(
    image_path: Path,
    model: str = "gpt-5-mini",
    verbose: bool = False,
    languages: list[str] | None = None,
) -> dict[str, Any]:
    """Analyze a meme image using GitHub Copilot SDK.
    
    Args:
        image_path: Path to the image file.
        model: Model to use (default: gpt-5-mini).
        verbose: Whether to print debug information.
        languages: List of BCP 47 language codes for multilingual output.
            First language is the primary language.
            If None or empty, defaults to English only.
        
    Returns:
        Dictionary with emojis, title, description, tags, searchPhrases,
        and optionally localizations for additional languages.
        
    Raises:
        CopilotNotAuthenticatedError: If Copilot CLI not available.
        RateLimitError: If rate limit is exceeded.
        CopilotError: For other errors.
    """
    global _rate_limiter
    
    # Default to English if no languages specified
    if not languages:
        languages = ["en"]
    
    # Choose appropriate system prompt based on language count
    system_prompt = get_multilingual_system_prompt(languages)
    
    if verbose:
        print(f"  [DEBUG] Image: {image_path.name}")
        print(f"  [DEBUG] Model: {model}")
        print(f"  [DEBUG] Languages: {', '.join(languages)}")
        print(f"  [DEBUG] Using Copilot SDK")
    
    # Wait if we're being rate limited
    await _rate_limiter.wait_if_needed()
    
    client = CopilotClient()
    try:
        await client.start()
        
        # Create session with system message for meme analysis
        session_config = {
            "model": model,
            "streaming": False,
            "system_message": {
                "mode": "replace",
                "content": system_prompt,
            },
        }
        
        session = await client.create_session(session_config)
        try:
            done = asyncio.Event()
            result_content: str | None = None
            error_message: str | None = None
            
            def on_event(event):
                nonlocal result_content, error_message
                
                if verbose:
                    event_type = getattr(event.type, 'value', str(event.type))
                    print(f"  [DEBUG] Event: {event_type}")
                
                event_type = getattr(event.type, 'value', str(event.type))
                if event_type == "assistant.message":
                    result_content = event.data.content
                elif event_type == "session.idle":
                    done.set()
                elif event_type == "session.error":
                    error_message = getattr(event.data, "message", str(event.data))
                    done.set()
            
            session.on(on_event)
            
            # Send the image for analysis
            await session.send({
                "prompt": "Analyze this meme and provide the JSON metadata.",
                "attachments": [
                    {
                        "type": "file",
                        "path": str(image_path.resolve()),
                        "display_name": image_path.name,
                    }
                ],
            })
            
            # Wait for completion with timeout
            try:
                await asyncio.wait_for(done.wait(), timeout=120.0)
            except asyncio.TimeoutError:
                raise CopilotError("Timeout waiting for Copilot response")
            
            if error_message:
                error_lower = error_message.lower()
                
                # Check for rate limit errors (429)
                if "rate" in error_lower or "429" in error_message:
                    wait_time = _rate_limiter.record_failure()
                    raise RateLimitError(
                        f"Rate limit exceeded. Retry after {wait_time:.1f}s",
                        retry_after=wait_time,
                    )
                
                # Check for server errors (500, 502, 504) - transient, can retry
                if any(code in error_message for code in ["500", "502", "504"]):
                    wait_time = _rate_limiter.record_failure(is_server_error=True)
                    status = 500  # Default
                    for code in [500, 502, 504]:
                        if str(code) in error_message:
                            status = code
                            break
                    raise ServerError(
                        f"Server error ({status}). Retry after {wait_time:.1f}s",
                        status_code=status,
                    )
                
                # Check for server error keywords
                if any(kw in error_lower for kw in [
                    "internal server", "bad gateway", "gateway timeout",
                    "service unavailable", "503"
                ]):
                    wait_time = _rate_limiter.record_failure(is_server_error=True)
                    raise ServerError(
                        f"Server error. Retry after {wait_time:.1f}s",
                        status_code=503,
                    )
                raise CopilotError(f"Copilot error: {error_message}")
            
            if not result_content:
                raise CopilotError("No response from Copilot")
            
            if verbose:
                print(f"  [DEBUG] Response: {result_content[:500]}...")
            
            # Parse and validate response
            parsed = _parse_response_content(result_content)
            _rate_limiter.record_success()
            return parsed
        finally:
            await session.destroy()
    except FileNotFoundError:
        raise CopilotNotAuthenticatedError(
            "GitHub Copilot CLI not found. "
            "Install it from https://github.com/github/copilot-cli"
        )
    except ConnectionError as e:
        raise CopilotNotAuthenticatedError(
            f"Failed to connect to Copilot CLI: {e}"
        )
    finally:
        await client.stop()


def analyze_image(
    image_path: Path,
    access_token: str | None = None,  # Kept for API compatibility, not used with SDK
    model: str = "gpt-5-mini",
    verbose: bool = False,
    languages: list[str] | None = None,
) -> dict[str, Any]:
    """Analyze a meme image using GitHub Copilot SDK (sync wrapper).
    
    Args:
        image_path: Path to the image file.
        access_token: Ignored (kept for API compatibility).
        model: Model to use (default: gpt-5-mini).
        verbose: Whether to print debug information.
        languages: List of BCP 47 language codes for multilingual output.
        
    Returns:
        Dictionary with emojis, title, description, tags, searchPhrases,
        and optionally localizations for additional languages.
        
    Raises:
        CopilotNotAuthenticatedError: If Copilot CLI not available.
        RateLimitError: If rate limit is exceeded.
        CopilotError: For other errors.
    """
    return asyncio.run(
        analyze_image_async(image_path, model, verbose, languages)
    )


def get_rate_limiter() -> RateLimiter:
    """Get the global rate limiter instance for inspection/configuration."""
    return _rate_limiter


def reset_rate_limiter() -> None:
    """Reset the rate limiter state."""
    global _rate_limiter
    _rate_limiter = RateLimiter()
